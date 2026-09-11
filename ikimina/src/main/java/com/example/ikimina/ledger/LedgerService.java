package com.example.ikimina.ledger;

import com.example.ikimina.audit.AuditService;
import com.example.ikimina.exception.BusinessRuleException;
import com.example.ikimina.exception.ResourceNotFoundException;
import com.example.ikimina.money.Money;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The only way money movements enter the ledger.
 *
 * Two guarantees this exists to provide:
 *
 *  1. Idempotency. Every write carries a caller-supplied key. A retry - routine
 *     on a poor mobile connection - reuses the key and returns the entry that
 *     already exists, instead of recording the contribution twice.
 *
 *  2. Append-only history. Nothing is updated or deleted. A correction appends
 *     a reversing entry, so the ledger shows both the mistake and the fix.
 */
@Service
@Transactional(readOnly = true)
public class LedgerService {

    private static final Logger log = LoggerFactory.getLogger(LedgerService.class);

    /** RWF is the only currency in use; kept explicit so entries stay unambiguous. */
    public static final String DEFAULT_CURRENCY = "RWF";

    private final LedgerEntryRepository ledgerEntryRepository;
    private final AuditService auditService;

    public LedgerService(LedgerEntryRepository ledgerEntryRepository, AuditService auditService) {
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.auditService = auditService;
    }

    /**
     * Appends an entry, or returns the existing one when the key has already
     * been used.
     *
     * The unique constraint on idempotency_key is the real guard: two concurrent
     * retries can both pass the pre-check, so the loser catches the integrity
     * violation and reads back the winner's row.
     */
    @Transactional
    public LedgerEntry record(LedgerCommand command) {
        command.validate();

        Optional<LedgerEntry> existing =
                ledgerEntryRepository.findByIdempotencyKey(command.idempotencyKey());
        if (existing.isPresent()) {
            log.debug("Idempotent replay of ledger key {}", command.idempotencyKey());
            return existing.get();
        }

        LedgerEntry entry = new LedgerEntry();
        entry.setEntryType(command.entryType());
        entry.setDirection(command.direction());
        entry.setAmount(Money.of(command.amount()));
        entry.setCurrency(command.currency() == null ? DEFAULT_CURRENCY : command.currency());
        entry.setGroupId(command.groupId());
        entry.setMemberId(command.memberId());
        entry.setOccurredOn(command.occurredOn());
        entry.setIdempotencyKey(command.idempotencyKey());
        entry.setSourceType(command.sourceType());
        entry.setSourceId(command.sourceId());
        entry.setCreatedBy(command.createdBy());
        entry.setDescription(command.description());

        LedgerEntry saved;
        try {
            saved = ledgerEntryRepository.saveAndFlush(entry);
        } catch (DataIntegrityViolationException ex) {
            // Lost the race: another transaction inserted the same key.
            return ledgerEntryRepository.findByIdempotencyKey(command.idempotencyKey())
                    .orElseThrow(() -> ex);
        }

        auditService.record("LEDGER", "LEDGER_ENTRY", saved.getId(), "RECORD",
                null, saved.getDirection() + " " + saved.getAmount().toPlainString(),
                saved.getEntryType() + " for member " + saved.getMemberId()
                        + " in group " + saved.getGroupId());

        return saved;
    }

    /**
     * Appends the mirror image of an existing entry. The original stays exactly
     * as recorded; the correction is a new row.
     */
    @Transactional
    public LedgerEntry reverse(Long entryId, String idempotencyKey, Long actorId, String reason) {
        LedgerEntry original = ledgerEntryRepository.findById(entryId)
                .orElseThrow(() -> new ResourceNotFoundException("Ledger entry not found: " + entryId));

        if (original.getReversalOf() != null) {
            throw new BusinessRuleException("Cannot reverse a reversal; reverse the original entry instead");
        }
        if (ledgerEntryRepository.existsByReversalOf(entryId)) {
            throw new BusinessRuleException("Ledger entry has already been reversed");
        }

        Optional<LedgerEntry> existing = ledgerEntryRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return existing.get();
        }

        LedgerEntry reversal = new LedgerEntry();
        reversal.setEntryType(LedgerEntryType.ADJUSTMENT);
        reversal.setDirection(original.getDirection() == LedgerDirection.CREDIT
                ? LedgerDirection.DEBIT
                : LedgerDirection.CREDIT);
        reversal.setAmount(original.getAmount());
        reversal.setCurrency(original.getCurrency());
        reversal.setGroupId(original.getGroupId());
        reversal.setMemberId(original.getMemberId());
        reversal.setOccurredOn(LocalDate.now());
        reversal.setIdempotencyKey(idempotencyKey);
        reversal.setSourceType(original.getSourceType());
        reversal.setSourceId(original.getSourceId());
        reversal.setReversalOf(original.getId());
        reversal.setCreatedBy(actorId);
        reversal.setDescription(reason == null ? "Reversal of entry " + entryId : reason);

        LedgerEntry saved;
        try {
            saved = ledgerEntryRepository.saveAndFlush(reversal);
        } catch (DataIntegrityViolationException ex) {
            return ledgerEntryRepository.findByIdempotencyKey(idempotencyKey).orElseThrow(() -> ex);
        }

        auditService.record("LEDGER", "LEDGER_ENTRY", saved.getId(), "REVERSE",
                original.getDirection() + " " + original.getAmount().toPlainString(),
                saved.getDirection() + " " + saved.getAmount().toPlainString(),
                "Reversal of entry " + original.getId() + ": " + saved.getDescription());

        return saved;
    }

    public BigDecimal groupBalance(Long groupId) {
        return Money.of(ledgerEntryRepository.balanceForGroup(groupId));
    }

    public BigDecimal memberBalance(Long memberId) {
        return Money.of(ledgerEntryRepository.balanceForMember(memberId));
    }

    public Map<Long, BigDecimal> memberBalances(Long groupId) {
        return ledgerEntryRepository.memberBalancesForGroup(groupId).stream()
                .collect(Collectors.toMap(
                        row -> (Long) row[0],
                        row -> Money.of((BigDecimal) row[1])));
    }

    /**
     * Reconciliation: the fund balance must equal the sum of member balances
     * plus whatever is held at fund level (entries with no member).
     *
     * This is the invariant the ledger exists to protect, and the check to run
     * before trusting any report.
     */
    public Reconciliation reconcile(Long groupId) {
        BigDecimal fund = groupBalance(groupId);
        BigDecimal members = Money.sum(memberBalances(groupId).values());
        return new Reconciliation(groupId, fund, members, Money.subtract(fund, members));
    }

    /** Outcome of a reconciliation check; {@code balanced()} is what matters. */
    public record Reconciliation(Long groupId,
                                 BigDecimal fundBalance,
                                 BigDecimal sumOfMemberBalances,
                                 BigDecimal unallocated) {

        /**
         * True when the fund balance is fully explained by member balances plus
         * fund-level entries. Uses compareTo, not equals: BigDecimal equality is
         * scale-sensitive, so 100.00 and 100.000 would otherwise differ.
         */
        public boolean balanced() {
            return Money.eq(fundBalance, Money.add(sumOfMemberBalances, unallocated));
        }
    }

    public List<LedgerEntry> entriesForSource(String sourceType, Long sourceId) {
        return ledgerEntryRepository.findBySourceTypeAndSourceId(sourceType, sourceId);
    }
}
