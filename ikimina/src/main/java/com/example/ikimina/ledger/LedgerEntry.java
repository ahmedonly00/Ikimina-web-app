package com.example.ikimina.ledger;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One immutable money movement.
 *
 * The ledger is append-only: rows are never updated or deleted. A mistake is
 * corrected by appending a reversing entry that points at the original via
 * {@link #reversalOf}, so the history of what the group believed, and when,
 * stays intact. That is what makes the books auditable.
 *
 * Balances are derived by summing entries rather than stored, so a balance can
 * always be explained by the rows that produced it.
 */
@Entity
@Table(
    name = "ledger_entries",
    uniqueConstraints = {
        // Retrying a request with the same key must not create a second entry.
        @UniqueConstraint(name = "uk_ledger_idempotency_key", columnNames = "idempotency_key")
    },
    indexes = {
        @Index(name = "ix_ledger_group_date", columnList = "group_id, occurred_on"),
        @Index(name = "ix_ledger_member_date", columnList = "member_id, occurred_on"),
        @Index(name = "ix_ledger_source", columnList = "source_type, source_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 40)
    private LedgerEntryType entryType;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false, length = 10)
    private LedgerDirection direction;

    /** Always non-negative; {@link #direction} carries the sign. */
    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    /** Null for entries that belong to the fund rather than a member. */
    @Column(name = "member_id")
    private Long memberId;

    /** Business date of the movement, which may precede when it was recorded. */
    @Column(name = "occurred_on", nullable = false)
    private LocalDate occurredOn;

    @CreationTimestamp
    @Column(name = "recorded_at", nullable = false, updatable = false)
    private LocalDateTime recordedAt;

    /**
     * Caller-supplied key that makes recording idempotent. A retry after a
     * timeout on a poor connection reuses the key and returns the existing
     * entry instead of double-counting a contribution.
     */
    @Column(name = "idempotency_key", nullable = false, length = 120, updatable = false)
    private String idempotencyKey;

    /** Originating aggregate, e.g. "SAVINGS" / "FINE" / "PAYOUT". */
    @Column(name = "source_type", length = 40)
    private String sourceType;

    @Column(name = "source_id")
    private Long sourceId;

    /** Set only on a correcting entry, naming the entry it cancels. */
    @Column(name = "reversal_of")
    private Long reversalOf;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "description", length = 500)
    private String description;

    /** Signed contribution of this entry to the fund balance. */
    public BigDecimal signedAmount() {
        return direction == LedgerDirection.CREDIT ? amount : amount.negate();
    }
}
