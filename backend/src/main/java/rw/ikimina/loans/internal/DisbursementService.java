package rw.ikimina.loans.internal;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
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
import rw.ikimina.shared.locks.AdvisoryLocks;
import rw.ikimina.shared.error.ErrorCode;
import rw.ikimina.shared.security.CurrentUser;
import rw.ikimina.shared.tenancy.TenantContext;
import rw.ikimina.shared.time.BusinessTime;

/**
 * Recording that a loan's money was handed over (spec 9.4): the disbursement record, the ledger
 * journal (debit the loan receivable, credit group cash - spec 7.2), the repayment schedule and the
 * move to DISBURSED, in one transaction.
 *
 * <p>A loan is disbursed once, even under concurrent requests: the loan row is locked, the state
 * machine allows DISBURSE only from APPROVED, and {@code loan_disbursements.loan_id} is unique.
 * A retry with the same Idempotency-Key returns the original disbursement (H7).
 */
@Service
class DisbursementService {

    private final LoanRepository loans;
    private final LoanDisbursementRepository disbursements;
    private final LoanInstallmentRepository installments;
    private final LoanApprovalRepository approvals;
    private final LoanService loanService;
    private final LoanProductService productService;
    private final LoanQueries queries;
    private final GroupMembers members;
    private final Ledger ledger;
    private final AuditService audit;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    DisbursementService(LoanRepository loans, LoanDisbursementRepository disbursements, LoanInstallmentRepository installments,
                        LoanApprovalRepository approvals, LoanService loanService, LoanProductService productService,
                        LoanQueries queries, GroupMembers members, Ledger ledger, AuditService audit, JdbcTemplate jdbc,
                        Clock clock) {
        this.loans = loans;
        this.disbursements = disbursements;
        this.installments = installments;
        this.approvals = approvals;
        this.loanService = loanService;
        this.productService = productService;
        this.queries = queries;
        this.members = members;
        this.ledger = ledger;
        this.audit = audit;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional
    LoanQueries.LoanView disburse(UUID loanId, String idempotencyKey, LoanDisbursement.Method method, String externalRef,
                                  LocalDate businessDate) {
        TenantContext.GroupScope scope = TenantContext.requireGroup();
        // Payouts in one group run one at a time: two payouts of different loans must not both spend the same cash.
        // Taken before any row lock, and by no other path, so it cannot form a lock cycle.
        AdvisoryLocks.lock(jdbc, AdvisoryLocks.GROUP_PAYOUT, scope.groupId());
        Loan loan = loans.findByGroupIdAndPublicIdForUpdate(scope.groupId(), loanId).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        String key = RequestKeys.require(idempotencyKey);
        LocalDate today = BusinessTime.today(clock);
        LocalDate date = businessDate == null ? today : businessDate;
        String ref = externalRef == null || externalRef.isBlank() ? null : externalRef.trim();
        // The date as the client sent it (null = today), so a retry after midnight is still the same request.
        String requestHash = RequestKeys.hash(loan.getPublicId() + "|" + method + "|" + ref + "|" + businessDate);

        Optional<LoanDisbursement> earlier = disbursements.findByGroupIdAndIdempotencyKey(scope.groupId(), key);
        if (earlier.isPresent()) {
            if (!earlier.get().getRequestHash().equals(requestHash)) {
                throw new ApiException(ErrorCode.IDEMPOTENCY_CONFLICT);
            }
            return queries.view(loan);
        }

        LoanStateMachine.next(loan.getStatus(), Action.DISBURSE);
        if (method == LoanDisbursement.Method.MOMO_MANUAL && ref == null
                || date.isAfter(today) || loan.getApprovedAt() != null && date.isBefore(BusinessTime.dateOf(loan.getApprovedAt()))) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED);
        }
        requireDifferentOfficer(scope, loan);
        if (loanService.availableFundsFor(loan).compareTo(loan.getPrincipal()) < 0) {
            throw new ApiException(ErrorCode.INSUFFICIENT_GROUP_FUNDS);
        }

        PostedJournal journal = ledger.post(new JournalRequest(JournalType.LOAN_DISBURSEMENT, "loan-disbursement:" + key, requestHash,
                date, "Loan disbursement", JournalSource.MANUAL, ref, loan.getBorrowerMembershipId(), List.of(
                JournalRequest.Line.debit(AccountRef.loanReceivable(loan.getBorrowerMembershipId(), loan.getId()), loan.getPrincipal()),
                JournalRequest.Line.credit(AccountRef.of(AccountType.GROUP_CASH), loan.getPrincipal()))));
        disbursements.saveAndFlush(new LoanDisbursement(scope.groupId(), loan.getId(), loan.getPrincipal(), method, ref, journal.id(),
                date, CurrentUser.require().id(), key, requestHash, clock.instant()));

        List<LoanScheduleCalculator.Installment> schedule = LoanScheduleCalculator.schedule(loan.getPrincipal(), loan.getTermMonths(),
                loan.terms(productService.allocation(loan.getAllocationOrder())), date);
        installments.saveAll(schedule.stream().map(row -> new LoanInstallment(scope.groupId(), loan.getId(), row)).toList());
        loan.apply(Action.DISBURSE, clock.instant());
        loan.matureOn(schedule.getLast().dueDate());
        loans.flush();
        installments.flush();

        boolean ownLoan = OwnLoan.recordedByBorrower(members, loan);
        audit.record(AuditEvent.of("LOAN_DISBURSED").entity("loan", loan.getPublicId())
                .after(Map.of("amount", loan.getPrincipal(), "method", method, "journal", journal.publicId(),
                        "installments", schedule.size(), "maturesOn", loan.getMaturesOn(), "externalRef", ref == null ? "" : ref,
                        "recordedByBorrower", ownLoan))
                .reason(ownLoan ? OwnLoan.REASON : null));
        return queries.view(loan);
    }

    /**
     * Spec 9.3, as the owner decided for Phase 3: when the group has three or more active officers,
     * whoever gave a single-approval loan its approval cannot also record its payout. {@link ApprovalPolicy}
     * keeps the Treasurer - who alone records payouts - from giving that approval, so this is a safety net,
     * e.g. for a loan approved before an officer joined. (A dual-approved loan already has an independent
     * second signer.)
     */
    private void requireDifferentOfficer(TenantContext.GroupScope scope, Loan loan) {
        long officers = members.active().stream().filter(m -> ApprovalPolicy.OFFICES.contains(m.role())).count();
        if (!ApprovalPolicy.separateDisburser(loan.getRequiredApprovals(), officers)) {
            return;
        }
        boolean approvedIt = approvals.findByGroupIdAndLoanIdOrderByDecidedAtAscIdAsc(scope.groupId(), loan.getId()).stream()
                .anyMatch(a -> a.getDecision() == LoanApproval.Decision.APPROVE && a.getApproverMembershipId().equals(scope.membershipId()));
        if (approvedIt) {
            throw new ApiException(ErrorCode.LOAN_DISBURSER_MUST_DIFFER);
        }
    }
}
