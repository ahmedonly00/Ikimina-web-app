package rw.ikimina.loans.internal;

import java.math.BigDecimal;
import java.time.Instant;
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
import rw.ikimina.shared.money.Money;

/** A group's loan product (spec 6.4): the terms its loans are offered on. */
@Entity
@Table(name = "loan_products")
public class LoanProduct {

    enum Status { ACTIVE, CLOSED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, updatable = false)
    private UUID publicId;

    @Column(name = "group_id", nullable = false, updatable = false)
    private Long groupId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "interest_method", nullable = false)
    private LoanTerms.InterestMethod interestMethod;

    @Column(name = "interest_rate_percent", nullable = false)
    private BigDecimal interestRatePercent;

    @Enumerated(EnumType.STRING)
    @Column(name = "interest_period", nullable = false)
    private LoanTerms.InterestPeriod interestPeriod;

    @Column(name = "min_amount")
    private BigDecimal minAmount;

    @Column(name = "max_amount")
    private BigDecimal maxAmount;

    @Column(name = "max_multiple_of_savings")
    private BigDecimal maxMultipleOfSavings;

    @Column(name = "min_term_months", nullable = false)
    private int minTermMonths;

    @Column(name = "max_term_months", nullable = false)
    private int maxTermMonths;

    @Enumerated(EnumType.STRING)
    @Column(name = "repayment_frequency", nullable = false)
    private LoanTerms.RepaymentFrequency repaymentFrequency;

    @Column(name = "grace_days", nullable = false)
    private int graceDays;

    @Column(name = "dual_approval_threshold")
    private BigDecimal dualApprovalThreshold;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "allocation_order", nullable = false)
    private String allocationOrder;

    @Column(name = "allow_concurrent_loans", nullable = false)
    private boolean allowConcurrentLoans;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Version
    private long version;

    protected LoanProduct() {
    }

    LoanProduct(long groupId, String name, ProductTerms terms, String allocationJson, Instant now) {
        this.publicId = UUID.randomUUID();
        this.groupId = groupId;
        this.name = name;
        this.status = Status.ACTIVE;
        this.createdAt = now;
        applyTerms(terms, allocationJson);
    }

    void applyTerms(ProductTerms terms, String allocationJson) {
        interestMethod = terms.interestMethod();
        interestRatePercent = terms.interestRatePercent();
        interestPeriod = terms.interestPeriod();
        minAmount = storage(terms.minAmount());
        maxAmount = storage(terms.maxAmount());
        maxMultipleOfSavings = terms.maxMultipleOfSavings();
        minTermMonths = terms.minTermMonths();
        maxTermMonths = terms.maxTermMonths();
        repaymentFrequency = terms.repaymentFrequency();
        graceDays = terms.graceDays();
        dualApprovalThreshold = storage(terms.dualApprovalThreshold());
        allocationOrder = allocationJson;
        allowConcurrentLoans = terms.allowConcurrentLoans();
    }

    void rename(String newName) {
        if (newName != null) {
            name = newName;
        }
    }

    void changeStatus(Status newStatus) {
        status = newStatus;
    }

    boolean isActive() {
        return status == Status.ACTIVE;
    }

    private static BigDecimal storage(Money amount) {
        return amount == null ? null : amount.toStorageAmount();
    }

    private static Money money(BigDecimal amount) {
        return amount == null ? null : Money.of(amount);
    }

    Long getId() {
        return id;
    }

    UUID getPublicId() {
        return publicId;
    }

    String getName() {
        return name;
    }

    Status getStatus() {
        return status;
    }

    long getVersion() {
        return version;
    }

    String getAllocationOrder() {
        return allocationOrder;
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

    Money getMinAmount() {
        return money(minAmount);
    }

    Money getMaxAmount() {
        return money(maxAmount);
    }

    BigDecimal getMaxMultipleOfSavings() {
        return maxMultipleOfSavings;
    }

    int getMinTermMonths() {
        return minTermMonths;
    }

    int getMaxTermMonths() {
        return maxTermMonths;
    }

    LoanTerms.RepaymentFrequency getRepaymentFrequency() {
        return repaymentFrequency;
    }

    int getGraceDays() {
        return graceDays;
    }

    Money getDualApprovalThreshold() {
        return money(dualApprovalThreshold);
    }

    boolean isAllowConcurrentLoans() {
        return allowConcurrentLoans;
    }
}
