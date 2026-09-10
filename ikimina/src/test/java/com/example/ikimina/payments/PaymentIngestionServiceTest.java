package com.example.ikimina.payments;

import com.example.ikimina.audit.AuditService;
import com.example.ikimina.ledger.LedgerCommand;
import com.example.ikimina.ledger.LedgerEntry;
import com.example.ikimina.ledger.LedgerService;
import com.example.ikimina.model.User;
import com.example.ikimina.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * The properties that matter when money arrives from outside: a retry must not
 * double-credit, an unmatched payment must not be guessed at, and an
 * unsuccessful payment must never reach the ledger.
 */
class PaymentIngestionServiceTest {

    @Mock private InboundPaymentEventRepository eventRepository;
    @Mock private LedgerService ledgerService;
    @Mock private UserRepository userRepository;
    @Mock private AuditService auditService;

    private PaymentIngestionService service;

    private final List<InboundPaymentEvent> stored = new ArrayList<>();
    private long nextId;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        stored.clear();
        nextId = 1L;
        service = new PaymentIngestionService(eventRepository, ledgerService, userRepository, auditService);

        when(eventRepository.findByProviderAndProviderEventId(anyString(), anyString()))
                .thenAnswer(inv -> stored.stream()
                        .filter(e -> inv.getArgument(0).equals(e.getProvider())
                                && inv.getArgument(1).equals(e.getProviderEventId()))
                        .findFirst());
        when(eventRepository.saveAndFlush(any(InboundPaymentEvent.class))).thenAnswer(inv -> {
            InboundPaymentEvent e = inv.getArgument(0);
            e.setId(nextId++);
            stored.add(e);
            return e;
        });
        when(eventRepository.save(any(InboundPaymentEvent.class))).thenAnswer(inv -> inv.getArgument(0));
        when(eventRepository.findById(anyLong())).thenAnswer(inv ->
                stored.stream().filter(e -> inv.getArgument(0).equals(e.getId())).findFirst());

        LedgerEntry entry = new LedgerEntry();
        entry.setId(99L);
        when(ledgerService.record(any(LedgerCommand.class))).thenReturn(entry);
    }

    private PaymentNotification notification(String eventId, String amount, boolean successful,
                                             Long memberId, String msisdn) {
        return new PaymentNotification(
                eventId, "REF-" + eventId,
                amount == null ? null : new BigDecimal(amount),
                "RWF", msisdn, 7L, memberId,
                Instant.now(), successful, "{}");
    }

    private User member(long id, String phone) {
        User u = new User();
        u.setId(id);
        u.setPhoneNumber(phone);
        return u;
    }

    @Test
    @DisplayName("a settled payment is credited once")
    void appliesSettledPayment() {
        when(userRepository.existsById(2L)).thenReturn(true);

        InboundPaymentEvent event = service.ingest("generic-hmac",
                notification("evt-1", "5000.00", true, 2L, "+250788123456"));

        assertEquals(InboundPaymentEvent.ProcessingStatus.APPLIED, event.getProcessingStatus());
        assertEquals(99L, event.getLedgerEntryId());

        ArgumentCaptor<LedgerCommand> cmd = ArgumentCaptor.forClass(LedgerCommand.class);
        verify(ledgerService).record(cmd.capture());
        assertEquals(0, cmd.getValue().amount().compareTo(new BigDecimal("5000.00")));
        assertEquals("payment:generic-hmac:evt-1", cmd.getValue().idempotencyKey());
    }

    @Test
    @DisplayName("a provider retry of the same event does not credit twice")
    void providerRetryDoesNotDoubleCredit() {
        when(userRepository.existsById(2L)).thenReturn(true);

        service.ingest("generic-hmac", notification("evt-dup", "1000.00", true, 2L, null));
        service.ingest("generic-hmac", notification("evt-dup", "1000.00", true, 2L, null));

        // Recorded once, and the ledger asked exactly once.
        assertEquals(1, stored.size());
        verify(ledgerService, times(1)).record(any(LedgerCommand.class));
    }

    @Test
    @DisplayName("an unsuccessful payment is recorded but never credited")
    void unsuccessfulPaymentIsNotCredited() {
        InboundPaymentEvent event = service.ingest("generic-hmac",
                notification("evt-fail", "2000.00", false, 2L, null));

        assertEquals(InboundPaymentEvent.ProcessingStatus.IGNORED, event.getProcessingStatus());
        verify(ledgerService, never()).record(any(LedgerCommand.class));
    }

    @Test
    @DisplayName("a payment that cannot be matched to a member is parked, not guessed")
    void unmatchedPaymentIsParked() {
        when(userRepository.existsById(anyLong())).thenReturn(false);
        when(userRepository.findByPhoneNormalised(anyString())).thenReturn(List.of());

        InboundPaymentEvent event = service.ingest("generic-hmac",
                notification("evt-unmatched", "3000.00", true, null, "+250788999999"));

        assertEquals(InboundPaymentEvent.ProcessingStatus.FAILED, event.getProcessingStatus());
        assertNotNull(event.getProcessingError());
        verify(ledgerService, never()).record(any(LedgerCommand.class));
    }

    @Test
    @DisplayName("an ambiguous phone number is refused rather than credited to one of them")
    void ambiguousMsisdnIsRefused() {
        when(userRepository.existsById(anyLong())).thenReturn(false);
        // Two members share a handset - crediting either would be a guess.
        when(userRepository.findByPhoneNormalised(anyString()))
                .thenReturn(List.of(member(2L, "+250788123456"), member(3L, "0788123456")));

        InboundPaymentEvent event = service.ingest("generic-hmac",
                notification("evt-ambiguous", "4000.00", true, null, "+250788123456"));

        assertEquals(InboundPaymentEvent.ProcessingStatus.FAILED, event.getProcessingStatus());
        verify(ledgerService, never()).record(any(LedgerCommand.class));
    }

    @Test
    @DisplayName("a member is resolved from the phone number when the provider sends no id")
    void resolvesMemberFromMsisdn() {
        when(userRepository.existsById(anyLong())).thenReturn(false);
        when(userRepository.findByPhoneNormalised("788123456"))
                .thenReturn(List.of(member(5L, "+250788123456")));
        when(userRepository.findPrimaryGroupId(5L)).thenReturn(7L);

        InboundPaymentEvent event = service.ingest("generic-hmac",
                notification("evt-msisdn", "1500.00", true, null, "0788123456"));

        assertEquals(InboundPaymentEvent.ProcessingStatus.APPLIED, event.getProcessingStatus());
    }

    @Test
    @DisplayName("a non-positive amount is refused")
    void nonPositiveAmountRefused() {
        when(userRepository.existsById(2L)).thenReturn(true);

        InboundPaymentEvent zero = service.ingest("generic-hmac",
                notification("evt-zero", "0.00", true, 2L, null));
        assertEquals(InboundPaymentEvent.ProcessingStatus.FAILED, zero.getProcessingStatus());

        InboundPaymentEvent missing = service.ingest("generic-hmac",
                notification("evt-null", null, true, 2L, null));
        assertEquals(InboundPaymentEvent.ProcessingStatus.FAILED, missing.getProcessingStatus());

        verify(ledgerService, never()).record(any(LedgerCommand.class));
    }

    @Test
    @DisplayName("the raw payload is kept even when the event cannot be applied")
    void keepsEvidenceOnFailure() {
        when(userRepository.existsById(anyLong())).thenReturn(false);
        when(userRepository.findByPhoneNormalised(anyString())).thenReturn(List.of());

        service.ingest("generic-hmac", notification("evt-evidence", "500.00", true, null, null));

        // The row survives so a dispute can be answered from what the provider sent.
        assertEquals(1, stored.size());
        assertEquals("{}", stored.get(0).getRawPayload());
    }

    @Test
    @DisplayName("phone numbers in any Rwandan format reduce to the same subscriber number")
    void normalisesMsisdnFormats() {
        assertEquals("788123456", PaymentIngestionService.normaliseMsisdn("+250788123456"));
        assertEquals("788123456", PaymentIngestionService.normaliseMsisdn("250788123456"));
        assertEquals("788123456", PaymentIngestionService.normaliseMsisdn("0788123456"));
        assertEquals("788123456", PaymentIngestionService.normaliseMsisdn("+250 788 123 456"));
    }

    @Test
    @DisplayName("an applied event records the group and member it resolved to")
    void appliedEventIsSelfDescribing() {
        // Reconciliation sums applied events by group. While the resolved ids
        // were left off the event row, an applied payment counted as zero
        // against the provider and the report showed a discrepancy that did not
        // exist. Found by running the reconciliation query against real data.
        when(userRepository.existsById(anyLong())).thenReturn(false);
        when(userRepository.findByPhoneNormalised("788555111"))
                .thenReturn(List.of(member(2L, "+250788555111")));
        when(userRepository.findPrimaryGroupId(2L)).thenReturn(7L);

        InboundPaymentEvent event = service.ingest("generic-hmac",
                new PaymentNotification("evt-self", "REF", new BigDecimal("5000.50"), "RWF",
                        "+250788555111", null, null, Instant.now(), true, "{}"));

        assertEquals(InboundPaymentEvent.ProcessingStatus.APPLIED, event.getProcessingStatus());
        assertEquals(2L, event.getMemberId(), "resolved member must be written back");
        assertEquals(7L, event.getGroupId(), "resolved group must be written back");
    }

    @Test
    @DisplayName("every outcome is audited")
    void auditsEveryOutcome() {
        when(userRepository.existsById(2L)).thenReturn(true);
        service.ingest("generic-hmac", notification("evt-audit", "700.00", true, 2L, null));

        verify(auditService).record(eq("PAYMENTS"), eq("INBOUND_PAYMENT_EVENT"),
                any(), eq("APPLIED"), any(), any(), any());
    }
}
