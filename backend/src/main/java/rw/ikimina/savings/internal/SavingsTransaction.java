package rw.ikimina.savings.internal;

import java.math.BigDecimal;
import java.time.Instant;
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
import rw.ikimina.shared.money.Money;

/** The business record of a contribution, tied one-to-one to its ledger journal (spec 6.3). */
@Entity
@Table(name = "savings_transactions")
public class SavingsTransaction {

    enum Type { CONTRIBUTION, WITHDRAWAL, SHARE_OUT }

    enum Method { CASH, MOMO_MANUAL, MOMO_API, BANK }

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

    @Enumerated(EnumType.STRING)
    @Column(name = "txn_type", nullable = false, updatable = false)
    private Type txnType;

    @Column(nullable = false, updatable = false)
    private BigDecimal amount;

    @Column(name = "journal_id", nullable = false, updatable = false)
    private Long journalId;

    @Column(name = "obligation_id", updatable = false)
    private Long obligationId;

    @Column(name = "meeting_id", updatable = false)
    private Long meetingId;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, updatable = false)
    private Method paymentMethod;

    @Column(name = "external_ref", updatable = false)
    private String externalRef;

    @Column(name = "business_date", nullable = false, updatable = false)
    private LocalDate businessDate;

    @Column(name = "recorded_by", nullable = false, updatable = false)
    private Long recordedBy;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt;

    @Column(name = "reversed_at")
    private Instant reversedAt;

    protected SavingsTransaction() {
    }

    static SavingsTransaction contribution(long groupId, long bucketId, long membershipId, Money amount, long journalId,
                                           Long obligationId, Method method, String externalRef, LocalDate businessDate,
                                           long recordedBy, Instant now) {
        SavingsTransaction txn = new SavingsTransaction();
        txn.publicId = UUID.randomUUID();
        txn.groupId = groupId;
        txn.bucketId = bucketId;
        txn.membershipId = membershipId;
        txn.txnType = Type.CONTRIBUTION;
        txn.amount = amount.toStorageAmount();
        txn.journalId = journalId;
        txn.obligationId = obligationId;
        txn.paymentMethod = method;
        txn.externalRef = externalRef;
        txn.businessDate = businessDate;
        txn.recordedBy = recordedBy;
        txn.recordedAt = now;
        return txn;
    }

    void markReversed(Instant now) {
        if (reversedAt == null) {
            reversedAt = now;
        }
    }

    boolean isReversed() {
        return reversedAt != null;
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

    Type getTxnType() {
        return txnType;
    }

    Money getAmount() {
        return Money.of(amount);
    }

    Long getJournalId() {
        return journalId;
    }

    Method getPaymentMethod() {
        return paymentMethod;
    }

    String getExternalRef() {
        return externalRef;
    }

    LocalDate getBusinessDate() {
        return businessDate;
    }

    Instant getRecordedAt() {
        return recordedAt;
    }

    Instant getReversedAt() {
        return reversedAt;
    }
}
