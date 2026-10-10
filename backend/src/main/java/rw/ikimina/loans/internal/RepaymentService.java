package rw.ikimina.loans.internal;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.ikimina.audit.AuditEvent;
import rw.ikimina.audit.AuditService;
import rw.ikimina.groups.GroupMembers;
import rw.ikimina.ledger.AccountRef;
import rw.ikimina.ledger.AccountType;
import rw.ikimina.ledger.JournalRequest;
import rw.ikimina.ledger.JournalSource;
import rw.ikimina.ledger.JournalType;
import rw.ikimina.ledger.Ledger;
import rw.ikimina.ledger.PostedJournal;
import rw.ikimina.loans.internal.LoanStateMachine.Action;
import rw.ikimina.shared.error.ApiException;
import rw.ikimina.shared.error.ErrorCode;
import rw.ikimina.shared.money.Money;
import rw.ikimina.shared.security.CurrentUser;
import rw.ikimina.shared.tenancy.TenantContext;
import rw.ikimina.shared.time.BusinessTime;

/**
 * Recording a loan repayment (spec 9.6): split oldest installment first, in the loan's allocation
 * order (fines, interest, principal by default - fines are always 0 until Phase 4), posted as one
 * journal (debit cash; credit the loan receivable for principal and interest income for interest -
 * spec 7.2, interest recognised when paid). Paying more than is still owed is refused (owner
 * decision, Phase 3). Idempotent on the client's Idempotency-Key (H7).
 */
@Service
class RepaymentService {

    record Split(LoanInstallment installment, Money interest, Money principal) {
    }

    private final LoanRepository loans;
    private final LoanInstallmentRepository installments;
    private final LoanRepaymentRepository repayments;
    private final RepaymentAllocationRepository allocations;
    private final LoanDisbursementRepository disbursements;
    private final LoanProductService productService;
    private final LoanQueries queries;
    private final Ledger ledger;
    private final LoanStatusUpdater statusUpdater;
    private final GroupMembers members;
    private final AuditService audit;
    private final Clock clock;

    RepaymentService(LoanRepository loans, LoanInstallmentRepository installments, LoanRepaymentRepository repayments,
                     RepaymentAllocationRepository allocations, LoanDisbursementRepository disbursements,
                     LoanProductService productService, LoanQueries queries, Ledger ledger, LoanStatusUpdater statusUpdater,
                     GroupMembers members, AuditService audit, Clock clock) {
        this.loans = loans;
        this.installments = installments;
        this.repayments = repayments;
        this.allocations = allocations;
        this.disbursements = disbursements;
        this.productService = productService;
        this.queries = queries;
        this.ledger = ledger;
        this.statusUpdater = statusUpdater;
        this.members = members;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    LoanQueries.LoanView record(UUID loanId, String idempotencyKey, Money amount, LoanRepayment.Method method, String externalRef,
                                LocalDate businessDate) {
        TenantContext.GroupScope scope = TenantContext.requireGroup();
        Loan loan = loans.findByGroupIdAndPublicIdForUpdate(scope.groupId(), loanId).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        String key = RequestKeys.require(idempotencyKey);
        LocalDate today = BusinessTime.today(clock);
        LocalDate date = businessDate == null ? today : businessDate;
        String ref = externalRef == null || externalRef.isBlank() ? null : externalRef.trim();
        // The date as the client sent it (null = today), so a retry after midnight is still the same request.
        String requestHash = RequestKeys.hash(loan.getPublicId() + "|" + amount.toWireString() + "|" + method + "|" + ref + "|"
                + businessDate);

        Optional<LoanRepayment> earlier = repayments.findByGroupIdAndIdempotencyKey(scope.groupId(), key);
        if (earlier.isPresent()) {
            if (!earlier.get().getRequestHash().equals(requestHash)) {
                throw new ApiException(ErrorCode.IDEMPOTENCY_CONFLICT);
            }
            return queries.view(loan);
        }

        LoanStateMachine.next(loan.getStatus(), Action.RECORD_REPAYMENT);
        LocalDate disbursedOn = disbursements.findByGroupIdAndLoanId(scope.groupId(), loan.getId())
                .map(LoanDisbursement::getBusinessDate).orElseThrow(() -> new IllegalStateException("disbursed loan without disbursement"));
        if (!amount.isPositive() || !amount.isWholeRwf() || method == LoanRepayment.Method.MOMO_API
                || method == LoanRepayment.Method.MOMO_MANUAL && ref == null
                || date.isAfter(today) || date.isBefore(disbursedOn)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED);
        }
        if (ref != null && repayments.existsByGroupIdAndExternalRefAndReversedAtIsNull(scope.groupId(), ref)) {
            throw new ApiException(ErrorCode.DUPLICATE_EXTERNAL_REF);
        }

        List<LoanInstallment> schedule = installments.findByLoanForUpdate(scope.groupId(), loan.getId());
        Money owed = schedule.stream().map(LoanInstallment::outstanding).reduce(Money.ZERO, Money::plus);
        if (amount.compareTo(owed) > 0) {
            throw new ApiException(ErrorCode.LOAN_OVERPAYMENT, owed.toWireString());
        }
        List<Split> splits = split(schedule, productService.allocation(loan.getAllocationOrder()), amount);
        Money interest = splits.stream().map(Split::interest).reduce(Money.ZERO, Money::plus);
        Money principal = splits.stream().map(Split::principal).reduce(Money.ZERO, Money::plus);

        List<JournalRequest.Line> lines = new ArrayList<>();
        lines.add(JournalRequest.Line.debit(AccountRef.of(AccountType.GROUP_CASH), amount));
        if (principal.isPositive()) {
            lines.add(JournalRequest.Line.credit(AccountRef.loanReceivable(loan.getBorrowerMembershipId(), loan.getId()), principal));
        }
        if (interest.isPositive()) {
            lines.add(JournalRequest.Line.credit(AccountRef.of(AccountType.INTEREST_INCOME), interest));
        }
        PostedJournal journal = ledger.post(new JournalRequest(JournalType.LOAN_REPAYMENT, "loan-repayment:" + key, requestHash, date,
                "Loan repayment", JournalSource.MANUAL, ref, loan.getBorrowerMembershipId(), lines));

        LoanRepayment repayment = repayments.saveAndFlush(new LoanRepayment(scope.groupId(), loan.getId(), interest, principal,
                journal.id(), method, ref, date, CurrentUser.require().id(), key, requestHash, clock.instant()));
        for (Split split : splits) {
            allocations.save(new RepaymentAllocation(scope.groupId(), repayment.getId(), split.installment().getId(), split.interest(),
                    split.principal()));
            split.installment().applyPayment(split.interest(), split.principal(), today, loan.getGraceDays());
        }
        allocations.flush();
        installments.flush();

        boolean ownLoan = OwnLoan.recordedByBorrower(members, loan);
        audit.record(AuditEvent.of("LOAN_REPAYMENT_RECORDED").entity("loan_repayment", repayment.getPublicId())
                .after(Map.of("loan", loan.getPublicId(), "amount", amount, "interest", interest, "principal", principal,
                        "method", method, "journal", journal.publicId(), "externalRef", ref == null ? "" : ref,
                        "recordedByBorrower", ownLoan))
                .reason(ownLoan ? OwnLoan.REASON : null));
        statusUpdater.refresh(loan, schedule);
        loans.flush();
        return queries.view(loan);
    }

    /** Oldest installment first; within each, the components in {@code order}. Fines are 0 until Phase 4. */
    static List<Split> split(List<LoanInstallment> schedule, List<LoanTerms.Component> order, Money amount) {
        List<Split> splits = new ArrayList<>();
        Money left = amount;
        for (LoanInstallment installment : schedule) {
            if (!left.isPositive()) {
                break;
            }
            Money interest = Money.ZERO;
            Money principal = Money.ZERO;
            for (LoanTerms.Component component : order) {
                if (component == LoanTerms.Component.INTEREST) {
                    interest = min(left, installment.interestOutstanding());
                    left = left.minus(interest);
                } else if (component == LoanTerms.Component.PRINCIPAL) {
                    principal = min(left, installment.principalOutstanding());
                    left = left.minus(principal);
                }
            }
            if (interest.isPositive() || principal.isPositive()) {
                splits.add(new Split(installment, interest, principal));
            }
        }
        return splits;
    }

    private static Money min(Money a, Money b) {
        return a.compareTo(b) <= 0 ? a : b;
    }
}
