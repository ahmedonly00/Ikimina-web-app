package rw.ikimina.loans.internal;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.ikimina.audit.AuditEvent;
import rw.ikimina.audit.AuditService;
import rw.ikimina.groups.GroupBylaws;
import rw.ikimina.groups.GroupMembers;
import rw.ikimina.groups.GroupRole;
import rw.ikimina.ledger.AccountRef;
import rw.ikimina.ledger.AccountType;
import rw.ikimina.ledger.Ledger;
import rw.ikimina.loans.internal.LoanStateMachine.Action;
import rw.ikimina.loans.internal.LoanStateMachine.Status;
import rw.ikimina.savings.MemberSavings;
import rw.ikimina.shared.error.ApiException;
import rw.ikimina.shared.error.ErrorCode;
import rw.ikimina.shared.error.ReasonedApiException;
import rw.ikimina.shared.error.ReasonedApiException.Reason;
import rw.ikimina.shared.money.Money;
import rw.ikimina.shared.security.StepUp;
import rw.ikimina.shared.tenancy.TenantContext;
import rw.ikimina.shared.web.RequestContext;

/**
 * Requesting and deciding loans (spec 9.1-9.3). A member requests a loan for themselves; the
 * eligibility rules (9.2) are checked with every failing reason reported; officers then approve or
 * reject under {@link ApprovalPolicy}; the borrower may cancel before the money is handed over.
 */
@Service
class LoanService {

    /** Loans that block a new request unless the product allows concurrent loans (spec 9.2). */
    private static final Pattern IP_LITERAL = Pattern.compile("[0-9A-Fa-f:.]{2,45}");

    static final Set<Status> UNFINISHED = EnumSet.of(Status.SUBMITTED, Status.PARTIALLY_COUNTERSIGNED, Status.APPROVED,
            Status.DISBURSED, Status.OVERDUE);

    private final LoanRepository loans;
    private final LoanProductRepository products;
    private final LoanApprovalRepository approvals;
    private final LoanProductService productService;
    private final LoanQueries queries;
    private final GroupMembers members;
    private final GroupBylaws bylaws;
    private final MemberSavings savings;
    private final Ledger ledger;
    private final AuditService audit;
    private final StepUp stepUp;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    LoanService(LoanRepository loans, LoanProductRepository products, LoanApprovalRepository approvals,
                LoanProductService productService, LoanQueries queries, GroupMembers members, GroupBylaws bylaws,
                MemberSavings savings, Ledger ledger, AuditService audit, StepUp stepUp, JdbcTemplate jdbc, Clock clock) {
        this.loans = loans;
        this.products = products;
        this.approvals = approvals;
        this.productService = productService;
        this.queries = queries;
        this.members = members;
        this.bylaws = bylaws;
        this.savings = savings;
        this.ledger = ledger;
        this.audit = audit;
        this.stepUp = stepUp;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional
    LoanQueries.LoanView request(UUID productId, Money amount, int termMonths, String purpose) {
        TenantContext.GroupScope scope = TenantContext.requireGroup();
        LoanProduct product = products.findByGroupIdAndPublicId(scope.groupId(), productId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        if (!product.isActive()) {
            throw new ApiException(ErrorCode.LOAN_PRODUCT_NOT_ACTIVE);
        }
        // Interest counted as income when due needs an interest-receivable account the spec does not define yet.
        if (bylaws.current().interestRecognition() != GroupBylaws.InterestRecognition.WHEN_PAID) {
            throw new ApiException(ErrorCode.LOAN_TERMS_UNSUPPORTED);
        }
        GroupMembers.Member borrower = members.findById(scope.membershipId()).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        // One request at a time per member, so two simultaneous requests cannot both pass the open-loan rule.
        jdbc.queryForList("SELECT pg_advisory_xact_lock(?, ?)", 7_303, (int) borrower.membershipId());

        ProductTerms terms = productService.terms(product);
        int required = ApprovalPolicy.requiredApprovals(amount, terms.dualApprovalThreshold());
        List<Reason> reasons = eligibility(scope.groupId(), borrower, product, terms, amount, termMonths);
        // Spec 9.3: if the officers in post cannot give the approvals this loan needs, say so now rather than let it wait forever.
        List<GroupMembers.Member> active = members.active();
        List<GroupRole> officesHeld = active.stream()
                .filter(m -> m.membershipId() != borrower.membershipId() && ApprovalPolicy.OFFICES.contains(m.role()))
                .map(GroupMembers.Member::role).toList();
        if (!ApprovalPolicy.approvable(borrower.role(), required, ApprovalPolicy.separateDisburser(required, activeOfficers(active)),
                officesHeld)) {
            reasons.add(new Reason("APPROVERS_UNAVAILABLE"));
        }
        if (!reasons.isEmpty()) {
            throw new ReasonedApiException(ErrorCode.LOAN_NOT_ELIGIBLE, reasons);
        }
        Loan loan = loans.saveAndFlush(new Loan(scope.groupId(), product.getId(), borrower.membershipId(), borrower.role(),
                blankToNull(purpose), amount, termMonths, terms.loanTerms(), product.getAllocationOrder(), required, clock.instant()));
        audit.record(AuditEvent.of("LOAN_REQUESTED").entity("loan", loan.getPublicId())
                .after(Map.of("product", product.getPublicId(), "amount", amount, "termMonths", termMonths,
                        "requiredApprovals", required)));
        return queries.view(loan);
    }

    /** Spec 9.2: every rule the request breaks, so the member sees all of them at once. */
    private List<Reason> eligibility(long groupId, GroupMembers.Member borrower, LoanProduct product, ProductTerms terms,
                                     Money amount, int termMonths) {
        List<Reason> reasons = new ArrayList<>();
        if (!borrower.active()) {
            reasons.add(new Reason("MEMBER_NOT_ACTIVE"));
        }
        if (!terms.allowConcurrentLoans()
                && loans.existsByGroupIdAndBorrowerMembershipIdAndStatusIn(groupId, borrower.membershipId(), UNFINISHED)) {
            reasons.add(new Reason("OPEN_LOAN_EXISTS"));
        }
        if (!amount.isPositive() || !amount.isWholeRwf()) {
            reasons.add(new Reason("AMOUNT_NOT_WHOLE"));
        }
        if (terms.minAmount() != null && amount.compareTo(terms.minAmount()) < 0) {
            reasons.add(new Reason("BELOW_MINIMUM_AMOUNT", terms.minAmount().toWireString()));
        }
        if (terms.maxAmount() != null && amount.compareTo(terms.maxAmount()) > 0) {
            reasons.add(new Reason("ABOVE_MAXIMUM_AMOUNT", terms.maxAmount().toWireString()));
        }
        if (terms.maxMultipleOfSavings() != null) {
            Money limit = savings.loanBasis(borrower.membershipId()).times(terms.maxMultipleOfSavings());
            if (amount.compareTo(limit) > 0) {
                reasons.add(new Reason("ABOVE_SAVINGS_LIMIT", limit.toWireString()));
            }
        }
        if (termMonths < terms.minTermMonths()) {
            reasons.add(new Reason("TERM_TOO_SHORT", terms.minTermMonths()));
        }
        if (termMonths > terms.maxTermMonths()) {
            reasons.add(new Reason("TERM_TOO_LONG", terms.maxTermMonths()));
        }
        Money available = availableFunds(groupId, null);
        if (amount.compareTo(available) > 0) {
            reasons.add(new Reason("INSUFFICIENT_GROUP_FUNDS", available.isNegative() ? "0.00" : available.toWireString()));
        }
        return reasons;
    }

    /** Group cash less loans approved but not yet handed over (spec 9.2 "available funds"), leaving out {@code except}. */
    Money availableFunds(long groupId, Long except) {
        Money reserved = loans.findByGroupIdAndStatusIn(groupId, EnumSet.of(Status.APPROVED)).stream()
                .filter(l -> !l.getId().equals(except))
                .map(Loan::getPrincipal)
                .reduce(Money.ZERO, Money::plus);
        return ledger.balance(AccountRef.of(AccountType.GROUP_CASH)).minus(reserved);
    }

    /**
     * What an approved loan may draw at payout: cash less the loans approved before it (first approved,
     * first paid). Loans approved later wait their turn instead of blocking this one.
     */
    Money availableFundsFor(Loan loan) {
        Money reservedEarlier = loans.findByGroupIdAndStatusIn(loan.getGroupId(), EnumSet.of(Status.APPROVED)).stream()
                .filter(l -> !l.getId().equals(loan.getId()) && approvedBefore(l, loan))
                .map(Loan::getPrincipal)
                .reduce(Money.ZERO, Money::plus);
        return ledger.balance(AccountRef.of(AccountType.GROUP_CASH)).minus(reservedEarlier);
    }

    private static boolean approvedBefore(Loan a, Loan b) {
        int byTime = a.getApprovedAt().compareTo(b.getApprovedAt());
        return byTime < 0 || byTime == 0 && a.getId() < b.getId();
    }

    private static long activeOfficers(List<GroupMembers.Member> active) {
        return active.stream().filter(m -> ApprovalPolicy.OFFICES.contains(m.role())).count();
    }

    @Transactional
    LoanQueries.LoanView approve(UUID loanId, String comment) {
        return decide(loanId, LoanApproval.Decision.APPROVE, blankToNull(comment));
    }

    @Transactional
    LoanQueries.LoanView reject(UUID loanId, String reason) {
        return decide(loanId, LoanApproval.Decision.REJECT, reason.trim());
    }

    private LoanQueries.LoanView decide(UUID loanId, LoanApproval.Decision decision, String text) {
        TenantContext.GroupScope scope = TenantContext.requireGroup();
        Loan loan = visibleForUpdate(scope, loanId);
        if (loan.getStatus() != Status.SUBMITTED && loan.getStatus() != Status.PARTIALLY_COUNTERSIGNED) {
            throw new ApiException(ErrorCode.LOAN_INVALID_TRANSITION);
        }
        GroupRole approverRole = GroupRole.valueOf(scope.role());
        if (loan.getBorrowerMembershipId().equals(scope.membershipId())) {
            throw new ApiException(ErrorCode.SELF_APPROVAL_FORBIDDEN);
        }
        if (approvals.existsByGroupIdAndLoanIdAndApproverMembershipId(scope.groupId(), loan.getId(), scope.membershipId())) {
            throw new ApiException(ErrorCode.DUPLICATE_APPROVAL);
        }
        List<GroupRole> approvedAs = approvals.findByGroupIdAndLoanIdOrderByDecidedAtAscIdAsc(scope.groupId(), loan.getId()).stream()
                .filter(a -> a.getDecision() == LoanApproval.Decision.APPROVE)
                .map(LoanApproval::getApproverRole)
                .toList();
        // The borrower's role as it was when they asked: a later office change does not move the approval slots.
        boolean separateDisburser = ApprovalPolicy.separateDisburser(loan.getRequiredApprovals(), activeOfficers(members.active()));
        if (ApprovalPolicy.slotFor(loan.getBorrowerRole(), loan.getRequiredApprovals(), separateDisburser, approvedAs, approverRole, false)
                .isEmpty()) {
            throw new ApiException(ErrorCode.LOAN_APPROVER_NOT_ALLOWED);
        }
        List<GroupRole> withThis = new ArrayList<>(approvedAs);
        withThis.add(approverRole);
        boolean complete = ApprovalPolicy.openSlots(loan.getBorrowerRole(), loan.getRequiredApprovals(), separateDisburser, withThis)
                .isEmpty();
        // The last approval commits the group's cash: refuse it if loans approved earlier already need that money.
        if (decision == LoanApproval.Decision.APPROVE && complete
                && availableFunds(scope.groupId(), loan.getId()).compareTo(loan.getPrincipal()) < 0) {
            throw new ApiException(ErrorCode.INSUFFICIENT_GROUP_FUNDS);
        }
        // Spec 9.3: approving at or above the dual-approval threshold needs a fresh password.
        if (decision == LoanApproval.Decision.APPROVE && loan.getRequiredApprovals() == 2) {
            stepUp.require();
        }

        RequestContext request = RequestContext.current().orElse(null);
        try {
            approvals.saveAndFlush(new LoanApproval(scope.groupId(), loan.getId(), scope.membershipId(), approverRole, decision, text,
                    request == null ? null : ipLiteral(request.clientIp()), request == null ? null : request.userAgent(), clock.instant()));
        } catch (DataIntegrityViolationException twice) {
            throw new ApiException(ErrorCode.DUPLICATE_APPROVAL);
        }
        Status before = loan.getStatus();
        if (decision == LoanApproval.Decision.REJECT) {
            loan.apply(Action.REJECT, clock.instant());
            loan.recordRejectionReason(text);
        } else {
            loan.apply(complete ? Action.APPROVE : Action.COUNTERSIGN, clock.instant());
        }
        loans.flush();
        audit.record(AuditEvent.of(decision == LoanApproval.Decision.REJECT ? "LOAN_REJECTED" : "LOAN_APPROVED")
                .entity("loan", loan.getPublicId())
                .before(Map.of("status", before))
                .after(Map.of("status", loan.getStatus(), "approverRole", approverRole))
                .reason(text));
        return queries.view(loan);
    }

    /** The borrower withdraws their request, at any point before the money is handed over (spec 9.1). */
    @Transactional
    LoanQueries.LoanView cancel(UUID loanId) {
        TenantContext.GroupScope scope = TenantContext.requireGroup();
        Loan loan = visibleForUpdate(scope, loanId);
        if (!loan.getBorrowerMembershipId().equals(scope.membershipId())) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }
        Status before = loan.getStatus();
        loan.apply(Action.CANCEL, clock.instant());
        loans.flush();
        audit.record(AuditEvent.of("LOAN_CANCELLED").entity("loan", loan.getPublicId())
                .before(Map.of("status", before)).after(Map.of("status", loan.getStatus())));
        return queries.view(loan);
    }

    /** Locks the loan; someone else's loan that the caller may not see answers 404, as if it did not exist (spec 5.4). */
    private Loan visibleForUpdate(TenantContext.GroupScope scope, UUID loanId) {
        Loan loan = loans.findByGroupIdAndPublicIdForUpdate(scope.groupId(), loanId).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        if (!loan.getBorrowerMembershipId().equals(scope.membershipId()) && !LoanQueries.seesEveryLoan(scope)) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }
        return loan;
    }

    /** The client address for the approval record, or null if it is not an IP literal (never a DNS lookup). */
    private static InetAddress ipLiteral(String text) {
        if (text == null || !IP_LITERAL.matcher(text).matches()) {
            return null;
        }
        try {
            return InetAddress.getByName(text);
        } catch (UnknownHostException e) {
            return null;
        }
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }
}
