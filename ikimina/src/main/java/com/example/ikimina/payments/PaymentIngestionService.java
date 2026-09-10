package com.example.ikimina.payments;

import com.example.ikimina.audit.AuditService;
import com.example.ikimina.ledger.LedgerCommand;
import com.example.ikimina.ledger.LedgerDirection;
import com.example.ikimina.ledger.LedgerEntry;
import com.example.ikimina.ledger.LedgerEntryType;
import com.example.ikimina.ledger.LedgerService;
import com.example.ikimina.model.User;
import com.example.ikimina.money.Money;
import com.example.ikimina.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

/**
 * Turns a verified provider notification into a ledger entry, exactly once.
 *
 * The order of operations is deliberate:
 *
 *  1. Record the raw event in its own transaction, so it survives even if
 *     interpretation fails. Losing the evidence is worse than failing to post.
 *  2. Interpret and post to the ledger.
 *  3. Mark the event applied, with the ledger entry id.
 *
 * An event that cannot be interpreted is left FAILED with a reason rather than
 * discarded or guessed at. Unmatched money is a question for a human, not
 * something to resolve automatically - crediting the wrong member is worse
 * than crediting nobody yet.
 */
@Service
public class PaymentIngestionService {

    private static final Logger log = LoggerFactory.getLogger(PaymentIngestionService.class);

    private static final String SOURCE_TYPE = "MOBILE_MONEY";

    /** Attributed to the system, since no human is logged in on a webhook. */
    private static final long SYSTEM_ACTOR = 0L;

    private final InboundPaymentEventRepository eventRepository;
    private final LedgerService ledgerService;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public PaymentIngestionService(InboundPaymentEventRepository eventRepository,
                                   LedgerService ledgerService,
                                   UserRepository userRepository,
                                   AuditService auditService) {
        this.eventRepository = eventRepository;
        this.ledgerService = ledgerService;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    /**
     * Stores the notification and applies it.
     *
     * @return the stored event, whether or not it could be applied
     */
    public InboundPaymentEvent ingest(String provider, PaymentNotification notification) {
        InboundPaymentEvent event = store(provider, notification);

        // A provider retry lands on the row we already applied; nothing to do.
        if (event.getProcessingStatus() == InboundPaymentEvent.ProcessingStatus.APPLIED) {
            log.debug("Provider {} replayed event {}; already applied", provider, notification.providerEventId());
            return event;
        }

        return apply(event.getId(), notification);
    }

    /**
     * Persists the raw event in its own transaction.
     *
     * REQUIRES_NEW so a later failure to interpret cannot roll away the record
     * of what the provider sent.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected InboundPaymentEvent store(String provider, PaymentNotification n) {
        Optional<InboundPaymentEvent> existing =
                eventRepository.findByProviderAndProviderEventId(provider, n.providerEventId());
        if (existing.isPresent()) {
            return existing.get();
        }

        InboundPaymentEvent event = new InboundPaymentEvent();
        event.setProvider(provider);
        event.setProviderEventId(n.providerEventId());
        event.setProviderReference(n.providerReference());
        event.setAmount(n.amount() == null ? null : Money.of(n.amount()));
        event.setCurrency(n.currency());
        event.setPayerMsisdn(n.payerMsisdn());
        event.setGroupId(n.groupId());
        event.setMemberId(n.memberId());
        event.setOccurredAt(n.occurredAt());
        event.setRawPayload(n.rawPayload());
        event.setProcessingStatus(InboundPaymentEvent.ProcessingStatus.RECEIVED);

        try {
            return eventRepository.saveAndFlush(event);
        } catch (DataIntegrityViolationException ex) {
            // Concurrent delivery of the same event; the other transaction won.
            return eventRepository.findByProviderAndProviderEventId(provider, n.providerEventId())
                    .orElseThrow(() -> ex);
        }
    }

    @Transactional
    protected InboundPaymentEvent apply(Long eventId, PaymentNotification n) {
        InboundPaymentEvent event = eventRepository.findById(eventId).orElseThrow();

        if (event.getProcessingStatus() == InboundPaymentEvent.ProcessingStatus.APPLIED) {
            return event;
        }

        try {
            if (!n.successful()) {
                // A failed or pending payment is recorded but never credited.
                return finish(event, InboundPaymentEvent.ProcessingStatus.IGNORED,
                        "Provider did not report the payment as settled", null);
            }
            if (!Money.isPositive(n.amount())) {
                return finish(event, InboundPaymentEvent.ProcessingStatus.FAILED,
                        "Amount is missing or not positive", null);
            }

            Long memberId = resolveMember(n);
            if (memberId == null) {
                return finish(event, InboundPaymentEvent.ProcessingStatus.FAILED,
                        "Could not identify the paying member; needs manual matching", null);
            }

            Long groupId = n.groupId() != null
                    ? n.groupId()
                    : userRepository.findPrimaryGroupId(memberId);
            if (groupId == null) {
                return finish(event, InboundPaymentEvent.ProcessingStatus.FAILED,
                        "Member does not belong to a savings group", null);
            }

            // Write back what we resolved, so the event row is self-describing.
            // Without this the reconciliation query sees a NULL group on an
            // applied event and reports a discrepancy that does not exist.
            event.setMemberId(memberId);
            event.setGroupId(groupId);

            // The provider's event id is the idempotency key, so replaying this
            // notification can never double-credit the member.
            LedgerEntry entry = ledgerService.record(new LedgerCommand(
                    LedgerEntryType.SAVINGS_CONTRIBUTION,
                    LedgerDirection.CREDIT,
                    Money.of(n.amount()),
                    n.currency() == null ? LedgerService.DEFAULT_CURRENCY : n.currency(),
                    groupId,
                    memberId,
                    n.occurredAt() == null
                            ? LocalDateTime.now().toLocalDate()
                            : n.occurredAt().atZone(ZoneOffset.UTC).toLocalDate(),
                    idempotencyKey(event.getProvider(), n.providerEventId()),
                    SOURCE_TYPE,
                    event.getId(),
                    SYSTEM_ACTOR,
                    "Mobile money contribution, provider reference " + n.providerReference()));

            return finish(event, InboundPaymentEvent.ProcessingStatus.APPLIED, null, entry.getId());

        } catch (Exception ex) {
            log.error("Failed to apply payment event " + event.getId(), ex);
            return finish(event, InboundPaymentEvent.ProcessingStatus.FAILED,
                    truncate(ex.getMessage()), null);
        }
    }

    /**
     * Resolves the paying member.
     *
     * Prefers an explicit id from the provider. Falls back to the phone number,
     * matched only when it identifies exactly one member - an ambiguous match is
     * treated as no match, because crediting the wrong person is the worse
     * failure.
     */
    private Long resolveMember(PaymentNotification n) {
        if (n.memberId() != null && userRepository.existsById(n.memberId())) {
            return n.memberId();
        }
        if (n.payerMsisdn() == null || n.payerMsisdn().isBlank()) {
            return null;
        }

        String normalised = normaliseMsisdn(n.payerMsisdn());
        var matches = userRepository.findByPhoneNormalised(normalised);
        if (matches.size() == 1) {
            return matches.get(0).getId();
        }
        if (matches.size() > 1) {
            log.warn("Phone number matched {} members; refusing to guess", matches.size());
        }
        return null;
    }

    /**
     * Reduces a phone number to comparable digits.
     *
     * Rwandan numbers arrive as +250788123456, 250788123456 or 0788123456
     * depending on the provider and how the member typed it, so the last nine
     * digits (the subscriber number) are what get compared.
     */
    static String normaliseMsisdn(String msisdn) {
        String digits = msisdn.replaceAll("\\D", "");
        return digits.length() > 9 ? digits.substring(digits.length() - 9) : digits;
    }

    private InboundPaymentEvent finish(InboundPaymentEvent event,
                                       InboundPaymentEvent.ProcessingStatus status,
                                       String error,
                                       Long ledgerEntryId) {
        event.setProcessingStatus(status);
        event.setProcessingError(error);
        event.setLedgerEntryId(ledgerEntryId);
        event.setProcessedAt(LocalDateTime.now());
        InboundPaymentEvent saved = eventRepository.save(event);

        auditService.record("PAYMENTS", "INBOUND_PAYMENT_EVENT", saved.getId(),
                status.name(), null,
                saved.getAmount() == null ? null : saved.getAmount().toPlainString(),
                error != null ? error : "Applied as ledger entry " + ledgerEntryId);

        return saved;
    }

    static String idempotencyKey(String provider, String providerEventId) {
        return "payment:" + provider + ":" + providerEventId;
    }

    private String truncate(String value) {
        if (value == null) {
            return "Unknown error";
        }
        return value.length() <= 1000 ? value : value.substring(0, 1000);
    }
}