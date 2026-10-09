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
import org.hibernate.annotations.Immutable;
import rw.ikimina.shared.money.Money;

/** The record that a loan's money was handed over (spec 9.4). One per loan; append-only. */
@Entity
@Immutable
@Table(name = "loan_disbursements")
public class LoanDisbursement {

    enum Method { CASH, MOMO_MANUAL, BANK }

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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private Method method;

    @Column(name = "external_ref", updatable = false)
    private String externalRef;

    @Column(name = "journal_id", nullable = false, updatable = false)
    private Long journalId;

    @Column(name = "business_date", nullable = false, updatable = false)
    private LocalDate businessDate;

    @Column(name = "disbursed_by", nullable = false, updatable = false)
    private Long disbursedBy;

    @Column(name = "disbursed_at", nullable = false, updatable = false)
    private Instant disbursedAt;

    @Column(name = "idempotency_key", nullable = false, updatable = false)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, updatable = false)
    private String requestHash;

    protected LoanDisbursement() {
    }

    LoanDisbursement(long groupId, long loanId, Money amount, Method method, String externalRef, long journalId,
                     LocalDate businessDate, long disbursedBy, String idempotencyKey, String requestHash, Instant now) {
        this.publicId = UUID.randomUUID();
        this.groupId = groupId;
        this.loanId = loanId;
        this.amount = amount.toStorageAmount();
        this.method = method;
        this.externalRef = externalRef;
        this.journalId = journalId;
        this.businessDate = businessDate;
        this.disbursedBy = disbursedBy;
        this.disbursedAt = now;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
    }

    String getRequestHash() {
        return requestHash;
    }

    UUID getPublicId() {
        return publicId;
    }

    Money getAmount() {
        return Money.of(amount);
    }

    Method getMethod() {
        return method;
    }

    String getExternalRef() {
        return externalRef;
    }

    Long getJournalId() {
        return journalId;
    }

    LocalDate getBusinessDate() {
        return businessDate;
    }

    Long getDisbursedBy() {
        return disbursedBy;
    }

    Instant getDisbursedAt() {
        return disbursedAt;
    }
}
