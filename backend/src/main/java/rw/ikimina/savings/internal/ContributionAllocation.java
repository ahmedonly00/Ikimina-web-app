package rw.ikimina.savings.internal;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import rw.ikimina.shared.money.Money;

/** How much of a contribution paid how much of an obligation; marked reversed, never deleted. */
@Entity
@Table(name = "contribution_allocations")
public class ContributionAllocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_id", nullable = false, updatable = false)
    private Long groupId;

    @Column(name = "transaction_id", nullable = false, updatable = false)
    private Long transactionId;

    @Column(name = "obligation_id", nullable = false, updatable = false)
    private Long obligationId;

    @Column(nullable = false, updatable = false)
    private BigDecimal amount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "reversed_at")
    private Instant reversedAt;

    protected ContributionAllocation() {
    }

    ContributionAllocation(long groupId, long transactionId, long obligationId, Money amount, Instant now) {
        this.groupId = groupId;
        this.transactionId = transactionId;
        this.obligationId = obligationId;
        this.amount = amount.toStorageAmount();
        this.createdAt = now;
    }

    void markReversed(Instant now) {
        reversedAt = now;
    }

    Long getTransactionId() {
        return transactionId;
    }

    Long getObligationId() {
        return obligationId;
    }

    Money getAmount() {
        return Money.of(amount);
    }
}
