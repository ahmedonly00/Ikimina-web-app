package rw.ikimina.loans.internal;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import rw.ikimina.shared.money.Money;

/** How much of one repayment went to one installment, so a reversal undoes exactly that. */
@Entity
@Table(name = "loan_repayment_allocations")
public class RepaymentAllocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_id", nullable = false, updatable = false)
    private Long groupId;

    @Column(name = "repayment_id", nullable = false, updatable = false)
    private Long repaymentId;

    @Column(name = "installment_id", nullable = false, updatable = false)
    private Long installmentId;

    @Column(name = "interest_amount", nullable = false, updatable = false)
    private BigDecimal interestAmount;

    @Column(name = "principal_amount", nullable = false, updatable = false)
    private BigDecimal principalAmount;

    @Column(name = "reversed_at")
    private Instant reversedAt;

    protected RepaymentAllocation() {
    }

    RepaymentAllocation(long groupId, long repaymentId, long installmentId, Money interest, Money principal) {
        this.groupId = groupId;
        this.repaymentId = repaymentId;
        this.installmentId = installmentId;
        this.interestAmount = interest.toStorageAmount();
        this.principalAmount = principal.toStorageAmount();
    }

    void markReversed(Instant now) {
        reversedAt = now;
    }

    Long getInstallmentId() {
        return installmentId;
    }

    Money getInterest() {
        return Money.of(interestAmount);
    }

    Money getPrincipal() {
        return Money.of(principalAmount);
    }
}
