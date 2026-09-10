package com.example.ikimina.ledger;

import com.example.ikimina.audit.AuditService;
import com.example.ikimina.exception.BusinessRuleException;
import com.example.ikimina.money.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Covers the two properties the ledger exists to guarantee: a retried write
 * does not double-count, and a correction never mutates history.
 */
class LedgerServiceTest {

    @Mock
    private LedgerEntryRepository ledgerEntryRepository;

    @Mock
    private AuditService auditService;

    private LedgerService ledgerService;

    /** Stands in for the unique constraint on idempotency_key. */
    private final List<LedgerEntry> stored = new ArrayList<>();
    private long nextId;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        stored.clear();
        nextId = 1L;
        ledgerService = new LedgerService(ledgerEntryRepository, auditService);

        when(ledgerEntryRepository.findByIdempotencyKey(anyString())).thenAnswer(inv ->
                stored.stream()
                        .filter(e -> inv.getArgument(0).equals(e.getIdempotencyKey()))
                        .findFirst());

        when(ledgerEntryRepository.saveAndFlush(any(LedgerEntry.class))).thenAnswer(inv -> {
            LedgerEntry e = inv.getArgument(0);
            boolean duplicate = stored.stream()
                    .anyMatch(s -> s.getIdempotencyKey().equals(e.getIdempotencyKey()));
            if (duplicate) {
                throw new DataIntegrityViolationException("duplicate idempotency_key");
            }
            e.setId(nextId++);
            stored.add(e);
            return e;
        });
    }

    private LedgerCommand contribution(String amount, Long memberId, String key) {
        return new LedgerCommand(
                LedgerEntryType.SAVINGS_CONTRIBUTION,
                LedgerDirection.CREDIT,
                amount == null ? null : new BigDecimal(amount),
                "RWF",
                7L,
                memberId,
                LocalDate.now(),
                key,
                "SAVINGS",
                100L,
                42L,
                "test contribution");
    }

    @Test
    @DisplayName("replaying the same idempotency key does not record a second entry")
    void replayDoesNotDoubleCount() {
        LedgerEntry first = ledgerService.record(contribution("500.00", 1L, "key-1"));
        LedgerEntry replay = ledgerService.record(contribution("500.00", 1L, "key-1"));

        assertEquals(first.getId(), replay.getId(), "replay must return the original entry");
        assertEquals(1, stored.size(), "a retried contribution must not be recorded twice");
    }

    @Test
    @DisplayName("losing the insert race still yields one entry, not an error")
    void concurrentRetryResolvesToOneEntry() {
        LedgerEntry winner = ledgerService.record(contribution("100.00", 1L, "race"));

        reset(ledgerEntryRepository);
        when(ledgerEntryRepository.findByIdempotencyKey("race"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(winner));
        when(ledgerEntryRepository.saveAndFlush(any(LedgerEntry.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        LedgerEntry loser = ledgerService.record(contribution("100.00", 1L, "race"));

        assertEquals(winner.getId(), loser.getId());
    }

    @Test
    @DisplayName("a reversal appends a mirrored entry and leaves the original untouched")
    void reversalIsAppendOnly() {
        LedgerEntry original = ledgerService.record(contribution("750.00", 1L, "orig"));
        BigDecimal originalAmount = original.getAmount();
        LedgerDirection originalDirection = original.getDirection();

        when(ledgerEntryRepository.findById(original.getId())).thenReturn(Optional.of(original));
        when(ledgerEntryRepository.existsByReversalOf(original.getId())).thenReturn(false);

        LedgerEntry reversal = ledgerService.reverse(original.getId(), "rev-1", 42L, "keyed twice");

        assertEquals(LedgerDirection.DEBIT, reversal.getDirection(), "reversal must flip direction");
        assertEquals(LedgerEntryType.ADJUSTMENT, reversal.getEntryType());
        assertEquals(0, reversal.getAmount().compareTo(originalAmount), "amount must match");
        assertEquals(original.getId(), reversal.getReversalOf(), "reversal must name its original");

        assertEquals(originalDirection, original.getDirection());
        assertEquals(0, original.getAmount().compareTo(originalAmount));
        assertNull(original.getReversalOf());
        assertEquals(2, stored.size(), "correction is an extra row, not an edit");
    }

    @Test
    @DisplayName("an entry cannot be reversed twice")
    void doubleReversalRejected() {
        LedgerEntry original = ledgerService.record(contribution("300.00", 1L, "orig-2"));
        when(ledgerEntryRepository.findById(original.getId())).thenReturn(Optional.of(original));
        when(ledgerEntryRepository.existsByReversalOf(original.getId())).thenReturn(true);

        assertThrows(BusinessRuleException.class,
                () -> ledgerService.reverse(original.getId(), "rev-2", 42L, "again"));
    }

    @Test
    @DisplayName("a contribution and its reversal net to zero")
    void reversalNetsToZero() {
        LedgerEntry original = ledgerService.record(contribution("1234.56", 1L, "net"));
        when(ledgerEntryRepository.findById(original.getId())).thenReturn(Optional.of(original));
        when(ledgerEntryRepository.existsByReversalOf(original.getId())).thenReturn(false);
        LedgerEntry reversal = ledgerService.reverse(original.getId(), "net-rev", 42L, null);

        BigDecimal net = Money.add(original.signedAmount(), reversal.signedAmount());
        assertEquals(0, net.compareTo(BigDecimal.ZERO), "original plus reversal must be zero");
    }

    @Test
    @DisplayName("writes without an idempotency key are refused")
    void keyIsMandatory() {
        assertThrows(BusinessRuleException.class,
                () -> ledgerService.record(contribution("10.00", 1L, "  ")));
        assertThrows(BusinessRuleException.class,
                () -> ledgerService.record(contribution("10.00", 1L, null)));
    }

    @Test
    @DisplayName("non-positive amounts are refused; direction carries the sign")
    void amountMustBePositive() {
        assertThrows(BusinessRuleException.class,
                () -> ledgerService.record(contribution("0.00", 1L, "zero")));
        assertThrows(BusinessRuleException.class,
                () -> ledgerService.record(contribution("-5.00", 1L, "negative")));
    }

    @Test
    @DisplayName("future-dated entries are refused")
    void futureDatesRejected() {
        LedgerCommand future = new LedgerCommand(
                LedgerEntryType.SAVINGS_CONTRIBUTION, LedgerDirection.CREDIT,
                new BigDecimal("10.00"), "RWF", 7L, 1L,
                LocalDate.now().plusDays(1), "future", "SAVINGS", 1L, 42L, null);

        assertThrows(BusinessRuleException.class, () -> ledgerService.record(future));
    }

    @Test
    @DisplayName("group balance equals the sum of member balances after many movements")
    void reconciles() {
        ledgerService.record(contribution("100.10", 1L, "a"));
        ledgerService.record(contribution("200.20", 2L, "b"));
        ledgerService.record(contribution("300.30", 3L, "c"));
        ledgerService.record(contribution("0.01", 1L, "d"));

        BigDecimal fund = Money.sum(stored.stream().map(LedgerEntry::signedAmount).toList());

        when(ledgerEntryRepository.balanceForGroup(7L)).thenReturn(fund);
        when(ledgerEntryRepository.memberBalancesForGroup(7L)).thenReturn(List.of(
                new Object[]{1L, Money.of("100.11")},
                new Object[]{2L, Money.of("200.20")},
                new Object[]{3L, Money.of("300.30")}));

        LedgerService.Reconciliation result = ledgerService.reconcile(7L);

        assertEquals(0, result.fundBalance().compareTo(Money.of("600.61")));
        assertEquals(0, result.sumOfMemberBalances().compareTo(Money.of("600.61")));
        assertEquals(0, result.unallocated().compareTo(BigDecimal.ZERO),
                "nothing should be unaccounted for");
        assertTrue(result.balanced(), "the fund must reconcile with member balances");
    }

    @Test
    @DisplayName("every ledger write is audited")
    void writesAreAudited() {
        ledgerService.record(contribution("50.00", 1L, "audited"));

        verify(auditService, times(1)).record(
                eq("LEDGER"), eq("LEDGER_ENTRY"), any(), eq("RECORD"), any(), any(), any());
    }
}