package rw.ikimina.savings.internal;

import java.math.BigDecimal;
import java.time.LocalDate;
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
import rw.ikimina.shared.money.Money;

/** What one member owes one bucket for one period (spec 8.1). */
@Entity
@Table(name = "contribution_obligations")
public class Obligation {

    enum Status { OPEN, PARTIAL, PAID, OVERDUE, WAIVED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, updatable = false)
    private UUID publicId;

    @Column(name = "group_id", nullable = false, updatable = false)
    private Long groupId;

    @Column(name = "bucket_id", nullable = false, updatable = false)
    private Long bucketId;

    @Column(name = "membership_id", nullable = false, updatable = false)
    private Long membershipId;

    @Column(name = "period_start", nullable = false, updatable = false)
    private LocalDate periodStart;

    @Column(name = "due_date", nullable = false, updatable = false)
    private LocalDate dueDate;

    @Column(name = "amount_due", nullable = false, updatable = false)
    private BigDecimal amountDue;

    @Column(name = "amount_paid", nullable = false)
    private BigDecimal amountPaid;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Version
    private long version;

    protected Obligation() {
    }

    Money outstanding() {
        return Money.of(amountDue).minus(Money.of(amountPaid));
    }

    /** Applies a payment (positive) or undoes one (negative), then recomputes the status as of {@code today}. */
    void applyPayment(Money amount, LocalDate today) {
        amountPaid = Money.of(amountPaid).plus(amount).toStorageAmount();
        refreshStatus(today);
    }

    void refreshStatus(LocalDate today) {
        if (status == Status.WAIVED) {
            return;
        }
        if (amountPaid.compareTo(amountDue) >= 0) {
            status = Status.PAID;
        } else if (dueDate.isBefore(today)) {
            status = Status.OVERDUE;
        } else {
            status = amountPaid.signum() > 0 ? Status.PARTIAL : Status.OPEN;
        }
    }

    Long getId() {
        return id;
    }

    UUID getPublicId() {
        return publicId;
    }

    Long getBucketId() {
        return bucketId;
    }

    Long getMembershipId() {
        return membershipId;
    }

    LocalDate getPeriodStart() {
        return periodStart;
    }

    LocalDate getDueDate() {
        return dueDate;
    }

    Money getAmountDue() {
        return Money.of(amountDue);
    }

    Money getAmountPaid() {
        return Money.of(amountPaid);
    }

    Status getStatus() {
        return status;
    }
}
