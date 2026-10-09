package rw.ikimina.loans.internal;

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

/**
 * A repayment (spec 6.4), linked to its ledger journal. Its parts add up to the amount (a database
 * CHECK). Only {@code reversed_at} ever changes, when the journal is reversed.
 */
@Entity
@Table(name = "loan_repayments")
public class LoanRepayment {

    enum Method { CASH, MOMO_MANUAL, MOMO_API, BANK }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, updatable = false)
    private UUID publicId;

    @Column(name = "group_id", nullable = false, updatable = false)
    private Long groupId;

    @Column(name = "loan_id", nullable = false, updatable = false)
    private Long loanId;

    @Column(nullable = false, updatable = false)
    private BigDecimal amount;

    @Column(name = "fines_part", nullable = false, updatable = false)
    private BigDecimal finesPart;

    @Column(name = "interest_part", nullable = false, updatable = false)
    private BigDecimal interestPart;

    @Column(name = "principal_part", nullable = false, updatable = false)
    private BigDecimal principalPart;

    @Column(name = "journal_id", nullable = false, updatable = false)
    private Long journalId;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, updatable = false)
    private Method paymentMethod;

    @Column(name = "external_ref", updatable = false)
    private String externalRef;

    @Column(name = "meeting_id", updatable = false)
    private Long meetingId;

    @Column(name = "business_date", nullable = false, updatable = false)
    private LocalDate businessDate;

    @Column(name = "recorded_by", nullable = false, updatable = false)
    private Long recordedBy;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt;

    @Column(name = "reversed_at")
    private Instant reversedAt;

    @Column(name = "idempotency_key", nullable = false, updatable = false)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, updatable = false)
    private String requestHash;

    protected LoanRepayment() {
    }

    LoanRepayment(long groupId, long loanId, Money interest, Money principal, long journalId, Method method, String externalRef,
                  LocalDate businessDate, long recordedBy, String idempotencyKey, String requestHash, Instant now) {
        this.publicId = UUID.randomUUID();
        this.groupId = groupId;
        this.loanId = loanId;
        this.amount = interest.plus(principal).toStorageAmount();
        this.finesPart = BigDecimal.ZERO;   // fines arrive in Phase 4
        this.interestPart = interest.toStorageAmount();
        this.principalPart = principal.toStorageAmount();
        this.journalId = journalId;
        this.paymentMethod = method;
        this.externalRef = externalRef;
        this.businessDate = businessDate;
        this.recordedBy = recordedBy;
        this.recordedAt = now;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
    }

    void markReversed(Instant now) {
        reversedAt = now;
    }

    boolean isReversed() {
        return reversedAt != null;
    }

    String getRequestHash() {
        return requestHash;
    }

    Long getId() {
        return id;
    }

    UUID getPublicId() {
        return publicId;
    }

    Long getLoanId() {
        return loanId;
    }

    Money getAmount() {
        return Money.of(amount);
    }

    Money getFinesPart() {
        return Money.of(finesPart);
    }

    Money getInterestPart() {
        return Money.of(interestPart);
    }

    Money getPrincipalPart() {
        return Money.of(principalPart);
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
}
