package rw.ikimina.loans.internal;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import com.fasterxml.jackson.annotation.JsonFormat;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.ikimina.groups.GroupMembers;
import rw.ikimina.groups.GroupRole;
import rw.ikimina.groups.Permission;
import rw.ikimina.groups.PermissionMatrix;
import rw.ikimina.ledger.Ledger;
import rw.ikimina.loans.internal.LoanStateMachine.Status;
import rw.ikimina.shared.error.ApiException;
import rw.ikimina.shared.error.ErrorCode;
import rw.ikimina.shared.money.Money;
import rw.ikimina.shared.tenancy.TenantContext;

/**
 * What members and officers read about loans (spec 17.4). Object rule (spec 5.4 #4): a member sees
 * their own loans; officers who decide, hand over or collect loans, and anyone with group reports,
 * see every loan. Someone else's loan answers 404, as if it did not exist.
 */
@Service
@Transactional(readOnly = true)
class LoanQueries {

    record Person(UUID memberId, String memberNumber, String fullName) {
    }

    record ApprovalView(UUID memberId, GroupRole role, LoanApproval.Decision decision, String comment, Instant decidedAt) {
    }

    /** @param recordedByBorrower the borrower recorded it on their own loan - allowed, but flagged (owner decision) */
    record RepaymentView(UUID repaymentId, UUID journalId, Money amount, Money interest, Money principal,
                         LoanRepayment.Method method, String externalRef, LocalDate businessDate, boolean reversed,
                         boolean recordedByBorrower) {
    }

    record Outstanding(Money principal, Money interest, Money total) {
    }

    record LoanView(UUID loanId, UUID productId, String productName, Person borrower, String purpose, Money principal,
                    int termMonths, LoanTerms.InterestMethod interestMethod,
                    @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal interestRatePercent,
                    LoanTerms.InterestPeriod interestPeriod, LoanTerms.RepaymentFrequency repaymentFrequency, int graceDays,
                    Status status, int requiredApprovals, List<ApprovalView> approvals, Set<GroupRole> waitingFor,
                    Instant requestedAt, Instant approvedAt, Instant disbursedAt, LocalDate maturesOn, Instant settledAt,
                    String rejectionReason, Outstanding outstanding, List<RepaymentView> repayments, boolean disbursedByBorrower,
                    long version) {
    }

    record InstallmentView(int number, LocalDate dueDate, Money principalDue, Money interestDue, Money principalPaid,
                           Money interestPaid, LoanInstallment.Status status) {
    }

    record ScheduleView(UUID loanId, List<InstallmentView> installments, Outstanding outstanding) {
    }

    record PageView<T>(List<T> items, int page, int size, long totalItems, int totalPages) {
    }

    private final LoanRepository loans;
    private final LoanProductRepository products;
    private final LoanApprovalRepository approvals;
    private final LoanInstallmentRepository installments;
    private final LoanRepaymentRepository repayments;
    private final LoanDisbursementRepository disbursements;
    private final GroupMembers members;
    private final Ledger ledger;

    LoanQueries(LoanRepository loans, LoanProductRepository products, LoanApprovalRepository approvals,
                LoanInstallmentRepository installments, LoanRepaymentRepository repayments, LoanDisbursementRepository disbursements,
                GroupMembers members, Ledger ledger) {
        this.loans = loans;
        this.products = products;
        this.approvals = approvals;
        this.installments = installments;
        this.repayments = repayments;
        this.disbursements = disbursements;
        this.members = members;
        this.ledger = ledger;
    }

    LoanView get(UUID loanId) {
        return view(visible(loanId));
    }

    ScheduleView schedule(UUID loanId) {
        Loan loan = visible(loanId);
        List<LoanInstallment> rows = installments.findByGroupIdAndLoanIdOrderByInstallmentNo(loan.getGroupId(), loan.getId());
        return new ScheduleView(loan.getPublicId(), rows.stream().map(i -> new InstallmentView(i.getInstallmentNo(), i.getDueDate(),
                i.getPrincipalDue(), i.getInterestDue(), i.getPrincipalPaid(), i.getInterestPaid(), i.getStatus())).toList(),
                outstanding(rows));
    }

    /** Officers see every loan (optionally one member's); anyone else sees only their own, whatever they ask for. */
    PageView<LoanView> list(UUID memberId, Status status, int page, int size) {
        TenantContext.GroupScope scope = TenantContext.requireGroup();
        Long borrower;
        if (seesEveryLoan(scope)) {
            borrower = memberId == null ? null
                    : members.find(memberId).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND)).membershipId();
        } else {
            borrower = scope.membershipId();
        }
        Page<Loan> found = loans.search(scope.groupId(), borrower, status,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "requestedAt", "id")));
        return new PageView<>(found.getContent().stream().map(this::view).toList(), found.getNumber(), found.getSize(),
                found.getTotalElements(), found.getTotalPages());
    }

    LoanView view(Loan loan) {
        long groupId = loan.getGroupId();
        LoanProduct product = products.findByGroupIdAndId(groupId, loan.getProductId()).orElseThrow();
        List<LoanApproval> decisions = approvals.findByGroupIdAndLoanIdOrderByDecidedAtAscIdAsc(groupId, loan.getId());
        Map<Long, GroupMembers.Member> people = members.findByIds(Stream.concat(Stream.of(loan.getBorrowerMembershipId()),
                decisions.stream().map(LoanApproval::getApproverMembershipId)).toList());
        GroupMembers.Member borrower = people.get(loan.getBorrowerMembershipId());
        List<GroupRole> approvedAs = decisions.stream().filter(a -> a.getDecision() == LoanApproval.Decision.APPROVE)
                .map(LoanApproval::getApproverRole).toList();
        Set<GroupRole> waitingFor = Set.of();
        if (loan.getStatus() == Status.SUBMITTED || loan.getStatus() == Status.PARTIALLY_COUNTERSIGNED) {
            long officers = members.active().stream().filter(m -> ApprovalPolicy.OFFICES.contains(m.role())).count();
            waitingFor = ApprovalPolicy.waitingFor(loan.getBorrowerRole(), loan.getRequiredApprovals(),
                    ApprovalPolicy.separateDisburser(loan.getRequiredApprovals(), officers), approvedAs);
        }
        Long borrowerUser = borrower == null ? null : borrower.userId();
        boolean disbursedByBorrower = borrowerUser != null && disbursements.findByGroupIdAndLoanId(groupId, loan.getId())
                .map(d -> d.getDisbursedBy().equals(borrowerUser)).orElse(false);
        List<LoanRepayment> paid = repayments.findByGroupIdAndLoanIdOrderByBusinessDateAscIdAsc(groupId, loan.getId());
        Map<Long, UUID> journals = ledger.publicIds(paid.stream().map(LoanRepayment::getJournalId).toList());
        List<LoanInstallment> schedule = installments.findByGroupIdAndLoanIdOrderByInstallmentNo(groupId, loan.getId());
        return new LoanView(loan.getPublicId(), product.getPublicId(), product.getName(), person(borrower), loan.getPurpose(),
                loan.getPrincipal(), loan.getTermMonths(), loan.getInterestMethod(), loan.getInterestRatePercent(),
                loan.getInterestPeriod(), loan.getRepaymentFrequency(), loan.getGraceDays(), loan.getStatus(), loan.getRequiredApprovals(),
                decisions.stream().map(a -> new ApprovalView(memberId(people.get(a.getApproverMembershipId())), a.getApproverRole(),
                        a.getDecision(), a.getComment(), a.getDecidedAt())).toList(),
                waitingFor, loan.getRequestedAt(), loan.getApprovedAt(), loan.getDisbursedAt(), loan.getMaturesOn(), loan.getSettledAt(),
                loan.getRejectionReason(), outstanding(schedule),
                paid.stream().map(r -> new RepaymentView(r.getPublicId(), journals.get(r.getJournalId()), r.getAmount(), r.getInterestPart(),
                        r.getPrincipalPart(), r.getPaymentMethod(), r.getExternalRef(), r.getBusinessDate(), r.isReversed(),
                        r.getRecordedBy().equals(borrowerUser))).toList(),
                disbursedByBorrower, loan.getVersion());
    }

    private Loan visible(UUID loanId) {
        TenantContext.GroupScope scope = TenantContext.requireGroup();
        Loan loan = loans.findByGroupIdAndPublicId(scope.groupId(), loanId).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        if (!loan.getBorrowerMembershipId().equals(scope.membershipId()) && !seesEveryLoan(scope)) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }
        return loan;
    }

    static boolean seesEveryLoan(TenantContext.GroupScope scope) {
        GroupRole role = GroupRole.valueOf(scope.role());
        return PermissionMatrix.allows(role, Permission.REPORT_VIEW_GROUP) || PermissionMatrix.allows(role, Permission.LOAN_APPROVE)
                || PermissionMatrix.allows(role, Permission.LOAN_DISBURSE) || PermissionMatrix.allows(role, Permission.REPAYMENT_RECORD);
    }

    private static Outstanding outstanding(List<LoanInstallment> schedule) {
        Money principal = schedule.stream().map(LoanInstallment::principalOutstanding).reduce(Money.ZERO, Money::plus);
        Money interest = schedule.stream().map(LoanInstallment::interestOutstanding).reduce(Money.ZERO, Money::plus);
        return new Outstanding(principal, interest, principal.plus(interest));
    }

    private static Person person(GroupMembers.Member member) {
        return member == null ? null : new Person(member.memberId(), member.memberNumber(), member.fullName());
    }

    private static UUID memberId(GroupMembers.Member member) {
        return member == null ? null : member.memberId();
    }
}
