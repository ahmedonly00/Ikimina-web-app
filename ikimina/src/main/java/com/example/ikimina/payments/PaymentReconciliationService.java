package com.example.ikimina.payments;

import com.example.ikimina.ledger.LedgerEntryRepository;
import com.example.ikimina.money.Money;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Compares what providers reported against what the ledger records.
 *
 * Reconciliation is the control that catches the failures idempotency cannot:
 * a notification that never arrived, one recorded but never applied, or a
 * ledger entry with no provider evidence behind it. Any of those means the
 * books and the money have diverged, and someone needs to know before a member
 * does.
 *
 * This reports; it never auto-corrects. Silently adjusting a member's balance
 * to match a provider is how real discrepancies get buried.
 */
@Service
@Transactional(readOnly = true)
public class PaymentReconciliationService {

    private final InboundPaymentEventRepository eventRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    public PaymentReconciliationService(InboundPaymentEventRepository eventRepository,
                                        LedgerEntryRepository ledgerEntryRepository) {
        this.eventRepository = eventRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
    }

    /**
     * @param providerTotal   what providers say settled and was applied
     * @param ledgerTotal     what the ledger credited from mobile money
     * @param unmatchedEvents notifications recorded but not applied - money that
     *                        arrived and has not reached a member
     */
    public record Report(Long groupId,
                         LocalDate from,
                         LocalDate to,
                         BigDecimal providerTotal,
                         BigDecimal ledgerTotal,
                         BigDecimal difference,
                         long unmatchedEvents,
                         long failedEvents,
                         List<Long> eventsNeedingAttention) {

        /** True when provider and ledger agree and nothing is stuck. */
        public boolean clean() {
            return Money.eq(providerTotal, ledgerTotal)
                    && unmatchedEvents == 0
                    && failedEvents == 0;
        }
    }

    public Report reconcile(Long groupId, LocalDate from, LocalDate to) {
        LocalDateTime fromTs = from.atStartOfDay();
        LocalDateTime toTs = to.plusDays(1).atStartOfDay();

        BigDecimal providerTotal = Money.of(
                eventRepository.appliedTotalForGroup(groupId, fromTs, toTs));

        BigDecimal ledgerTotal = Money.of(
                ledgerEntryRepository.mobileMoneyTotalForGroupBetween(groupId, from, to));

        List<InboundPaymentEvent> stuck = eventRepository.findByProcessingStatusIn(List.of(
                InboundPaymentEvent.ProcessingStatus.RECEIVED,
                InboundPaymentEvent.ProcessingStatus.FAILED));

        long unmatched = stuck.stream()
                .filter(e -> e.getProcessingStatus() == InboundPaymentEvent.ProcessingStatus.RECEIVED)
                .count();
        long failed = stuck.stream()
                .filter(e -> e.getProcessingStatus() == InboundPaymentEvent.ProcessingStatus.FAILED)
                .count();

        return new Report(
                groupId, from, to,
                providerTotal,
                ledgerTotal,
                Money.subtract(providerTotal, ledgerTotal),
                unmatched,
                failed,
                stuck.stream().map(InboundPaymentEvent::getId).toList());
    }
}