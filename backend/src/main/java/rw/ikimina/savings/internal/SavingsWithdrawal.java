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
import jakarta.persistence.Version;
import rw.ikimina.groups.GroupRole;
import rw.ikimina.shared.error.ApiException;
import rw.ikimina.shared.error.ErrorCode;
import rw.ikimina.shared.money.Money;

/**
 * A member's request to take savings out (spec 8.3; owner decisions, Phase 3c): asked by the member,
 * decided by the President or Treasurer, paid out by the Treasurer.
 */
@Entity
@Table(name = "savings_withdrawals")
public class SavingsWithdrawal {

    enum Status {
        REQUESTED, APPROVED, PAID, REJECTED, CANCELLED;

        /** Still holding part of the member's balance. */
        boolean isPending() {
            return this == REQUESTED || this == APPROVED;
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, updatable = false)
    private UUID publicId;

    @Column(name = "group_id", nullable = false, updatable = false)
    private Long groupId;

    @Column(name = "membership_id", nullable = false, updatable = false)
    private Long membershipId;

    @Column(name = "bucket_id", nullable = false, updatable = false)
    private Long bucketId;

    @Column(nullable = false, updatable = false)
    private BigDecimal amount;

    @Column(updatable = false)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(name = "requested_on", nullable = false, updatable = false)
    private LocalDate requestedOn;

    @Column(name = "earliest_payout_on", nullable = false, updatable = false)
    private LocalDate earliestPayoutOn;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    @Column(name = "decided_by")
    private Long decidedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "decided_role")
    private GroupRole decidedRole;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decision_reason")
    private String decisionReason;

    @Column(name = "transaction_id")
    private Long transactionId;

    @Column(name = "paid_by")
    private Long paidBy;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "pay_idempotency_key")
    private String payIdempotencyKey;

    @Column(name = "pay_request_hash")
    private String payRequestHash;

    @Column(name = "reversed_at")
    private Instant reversedAt;

    @Version
    private long version;

    protected SavingsWithdrawal() {
    }

    SavingsWithdrawal(long groupId, long membershipId, long bucketId, Money amount, String reason, LocalDate requestedOn,
                      LocalDate earliestPayoutOn, Instant now) {
        this.publicId = UUID.randomUUID();
        this.groupId = groupId;
        this.membershipId = membershipId;
        this.bucketId = bucketId;
        this.amount = amount.toStorageAmount();
        this.reason = reason;
        this.status = Status.REQUESTED;
        this.requestedOn = requestedOn;
        this.earliestPayoutOn = earliestPayoutOn;
        this.requestedAt = now;
    }

    void approve(long byMembership, GroupRole role, Instant now) {
        require(Status.REQUESTED);
        status = Status.APPROVED;
        decide(byMembership, role, null, now);
    }

    void reject(long byMembership, GroupRole role, String why, Instant now) {
        require(Status.REQUESTED);
        status = Status.REJECTED;
        decide(byMembership, role, why, now);
    }

    void cancel() {
        if (!status.isPending()) {
            throw new ApiException(ErrorCode.WITHDRAWAL_INVALID_STATE);
        }
        status = Status.CANCELLED;
    }

    void pay(long transaction, long byUser, String idempotencyKey, String requestHash, Instant now) {
        require(Status.APPROVED);
        status = Status.PAID;
        transactionId = transaction;
        paidBy = byUser;
        paidAt = now;
        payIdempotencyKey = idempotencyKey;
        payRequestHash = requestHash;
    }

    void markReversed(Instant now) {
        reversedAt = now;
    }

    private void decide(long byMembership, GroupRole role, String why, Instant now) {
        decidedBy = byMembership;
        decidedRole = role;
        decisionReason = why;
        decidedAt = now;
    }

    private void require(Status expected) {
        if (status != expected) {
            throw new ApiException(ErrorCode.WITHDRAWAL_INVALID_STATE);
        }
    }

    Long getId() {
        return id;
    }

    UUID getPublicId() {
        return publicId;
    }

    Long getMembershipId() {
        return membershipId;
    }

    Long getBucketId() {
        return bucketId;
    }

    Money getAmount() {
        return Money.of(amount);
    }

    String getReason() {
        return reason;
    }

    Status getStatus() {
        return status;
    }

    LocalDate getRequestedOn() {
        return requestedOn;
    }

    LocalDate getEarliestPayoutOn() {
        return earliestPayoutOn;
    }

    Instant getRequestedAt() {
        return requestedAt;
    }

    Long getDecidedBy() {
        return decidedBy;
    }

    GroupRole getDecidedRole() {
        return decidedRole;
    }

    Instant getDecidedAt() {
        return decidedAt;
    }

    String getDecisionReason() {
        return decisionReason;
    }

    Long getTransactionId() {
        return transactionId;
    }

    Long getPaidBy() {
        return paidBy;
    }

    Instant getPaidAt() {
        return paidAt;
    }

    String getPayRequestHash() {
        return payRequestHash;
    }

    Instant getReversedAt() {
        return reversedAt;
    }
}
