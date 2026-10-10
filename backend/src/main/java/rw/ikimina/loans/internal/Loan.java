package rw.ikimina.loans.internal;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import rw.ikimina.loans.internal.LoanStateMachine.Action;
import rw.ikimina.loans.internal.LoanStateMachine.Status;
import rw.ikimina.shared.money.Money;

/**
 * A loan (spec 6.4). Its status changes only through {@link #apply}, which asks the
 * {@link LoanStateMachine}; it keeps a copy of the product's money terms from when it was requested.
 */
@Entity
@Table(name = "loans")
public class Loan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, updatable = false)
    private UUID publicId;

    @Column(name = "group_id", nullable = false, updatable = false)
    private Long groupId;

    @Column(name = "product_id", nullable = false, updatable = false)
    private Long productId;

    @Column(name = "borrower_membership_id", nullable = false, updatable = false)
    private Long borrowerMembershipId;

    @Column(updatable = false)
    private String purpose;

    @Column(name = "principal_amount", nullable = false, updatable = false)
    private BigDecimal principalAmount;

    @Column(name = "term_months", nullable = false, updatable = false)
    private int termMonths;

    @Enumerated(EnumType.STRING)
    @Column(name = "interest_method", nullable = false, updatable = false)
    private LoanTerms.InterestMethod interestMethod;

    @Column(name = "interest_rate_percent", nullable = false, updatable = false)
    private BigDecimal interestRatePercent;

    @Enumerated(EnumType.STRING)
    @Column(name = "interest_period", nullable = false, updatable = false)
    private LoanTerms.InterestPeriod interestPeriod;

    @Enumerated(EnumType.STRING)
    @Column(name = "repayment_frequency", nullable = false, updatable = false)
    private LoanTerms.RepaymentFrequency repaymentFrequency;

    @Column(name = "grace_days", nullable = false, updatable = false)
    private int graceDays;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "allocation_order", nullable = false, updatable = false)
    private String allocationOrder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "disbursed_at")
    private Instant disbursedAt;

    @Column(name = "matures_on")
    private LocalDate maturesOn;

    @Column(name = "settled_at")
    private Instant settledAt;

    @Column(name = "rejected_at")
    private Instant rejectedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "required_approvals", nullable = false, updatable = false)
    private int requiredApprovals;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @Version
    private long version;

    protected Loan() {
    }

    Loan(long groupId, long productId, long borrowerMembershipId, String purpose, Money principal, int termMonths,
         LoanTerms terms, String allocationJson, int requiredApprovals, Instant now) {
        this.publicId = UUID.randomUUID();
        this.groupId = groupId;
        this.productId = productId;
        this.borrowerMembershipId = borrowerMembershipId;
        this.purpose = purpose;
        this.principalAmount = principal.toStorageAmount();
        this.termMonths = termMonths;
        this.interestMethod = terms.interestMethod();
        this.interestRatePercent = terms.interestRatePercent();
        this.interestPeriod = terms.interestPeriod();
        this.repaymentFrequency = terms.repaymentFrequency();
        this.graceDays = terms.graceDays();
        this.allocationOrder = allocationJson;
        this.requiredApprovals = requiredApprovals;
        this.status = Status.SUBMITTED;
        this.requestedAt = now;
    }

    /** Moves the loan on, or refuses with LOAN_INVALID_TRANSITION. */
    Status apply(Action action, Instant now) {
        status = LoanStateMachine.next(status, action);
        switch (action) {
            case APPROVE -> approvedAt = now;
            case REJECT -> rejectedAt = now;
            case CANCEL -> cancelledAt = now;
            case DISBURSE -> disbursedAt = now;
            case SETTLE -> settledAt = now;
            case REOPEN -> settledAt = null;
            default -> {
                // no timestamp to keep
            }
        }
        return status;
    }

    void recordRejectionReason(String reason) {
        rejectionReason = reason;
    }

    void matureOn(LocalDate date) {
        maturesOn = date;
    }

    LoanTerms terms(List<LoanTerms.Component> allocation) {
        return new LoanTerms(interestMethod, interestRatePercent, interestPeriod, repaymentFrequency, graceDays, allocation);
    }

    Long getId() {
        return id;
    }

    UUID getPublicId() {
        return publicId;
    }

    Long getGroupId() {
        return groupId;
    }

    Long getProductId() {
        return productId;
    }

    Long getBorrowerMembershipId() {
        return borrowerMembershipId;
    }

    String getPurpose() {
        return purpose;
    }

    Money getPrincipal() {
        return Money.of(principalAmount);
    }

    int getTermMonths() {
        return termMonths;
    }

    LoanTerms.InterestMethod getInterestMethod() {
        return interestMethod;
    }

    BigDecimal getInterestRatePercent() {
        return interestRatePercent;
    }

    LoanTerms.InterestPeriod getInterestPeriod() {
        return interestPeriod;
    }

    LoanTerms.RepaymentFrequency getRepaymentFrequency() {
        return repaymentFrequency;
    }

    int getGraceDays() {
        return graceDays;
    }

    String getAllocationOrder() {
        return allocationOrder;
    }

    Status getStatus() {
        return status;
    }

    Instant getRequestedAt() {
        return requestedAt;
    }

    Instant getApprovedAt() {
        return approvedAt;
    }

    Instant getDisbursedAt() {
        return disbursedAt;
    }

    LocalDate getMaturesOn() {
        return maturesOn;
    }

    Instant getSettledAt() {
        return settledAt;
    }

    int getRequiredApprovals() {
        return requiredApprovals;
    }

    String getRejectionReason() {
        return rejectionReason;
    }

    long getVersion() {
        return version;
    }
}
