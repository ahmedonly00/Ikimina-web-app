package com.example.ikimina.payments;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;

/**
 * A payment notification exactly as the provider sent it.
 *
 * Stored before it is interpreted, and never mutated afterwards except to
 * record the outcome of processing. Two reasons:
 *
 *  1. Disputes. When a member says "I paid and it is not showing", the raw
 *     payload is the evidence of what the provider actually told us and when.
 *  2. Replay. If ingestion has a bug, events can be reprocessed from this
 *     table rather than lost.
 */
@Entity
@Table(
    name = "inbound_payment_events",
    uniqueConstraints = {
        // The provider's event id is the idempotency key: a provider retrying
        // its webhook (which they all do) must not produce a second credit.
        @UniqueConstraint(name = "uk_inbound_payment_provider_event",
                          columnNames = {"provider", "provider_event_id"})
    },
    indexes = {
        @Index(name = "ix_inbound_payment_status", columnList = "processing_status"),
        @Index(name = "ix_inbound_payment_received", columnList = "received_at"),
        @Index(name = "ix_inbound_payment_reference", columnList = "provider_reference")
    }
)
@Getter
@Setter
@NoArgsConstructor
public class InboundPaymentEvent {

    public enum ProcessingStatus {
        /** Stored, signature valid, not yet applied to the ledger. */
        RECEIVED,
        /** A ledger entry exists for this event. */
        APPLIED,
        /** Could not be applied; needs a human. See processingError. */
        FAILED,
        /** Deliberately not applied (e.g. a duplicate of another provider's event). */
        IGNORED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "provider", nullable = false, length = 40, updatable = false)
    private String provider;

    @Column(name = "provider_event_id", nullable = false, length = 120, updatable = false)
    private String providerEventId;

    /** The provider's own transaction reference, quoted back to members. */
    @Column(name = "provider_reference", length = 120, updatable = false)
    private String providerReference;

    @Column(name = "amount", precision = 19, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(name = "currency", length = 3, updatable = false)
    private String currency;

    /**
     * Payer's phone number. Personal data under Law 058/2021, so it is stored
     * only because it is the only way a provider identifies the payer, and it
     * is cleared when a member exercises erasure.
     */
    @Column(name = "payer_msisdn", length = 32)
    private String payerMsisdn;

    @Column(name = "group_id")
    private Long groupId;

    @Column(name = "member_id")
    private Long memberId;

    @Column(name = "occurred_at", updatable = false)
    private Instant occurredAt;

    @CreationTimestamp
    @Column(name = "received_at", nullable = false, updatable = false)
    private LocalDateTime receivedAt;

    /** Verbatim request body. Kept for dispute evidence and replay. */
    @Column(name = "raw_payload", columnDefinition = "TEXT", updatable = false)
    private String rawPayload;

    @Enumerated(EnumType.STRING)
    @Column(name = "processing_status", nullable = false, length = 20)
    private ProcessingStatus processingStatus = ProcessingStatus.RECEIVED;

    @Column(name = "processing_error", length = 1000)
    private String processingError;

    /** Ledger entry created for this event, once applied. */
    @Column(name = "ledger_entry_id")
    private Long ledgerEntryId;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;
}