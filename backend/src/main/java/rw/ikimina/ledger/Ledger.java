package rw.ikimina.ledger;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import rw.ikimina.shared.money.Money;

/**
 * The ledger's public API (spec 7). Every amount of money any module records goes through
 * {@link #post}; nothing else writes ledger tables (spec 7.1 #5). All calls act on the group in
 * the current tenant scope and must run inside the caller's transaction, so the business
 * record, the journal and the audit row commit together.
 */
public interface Ledger {

    /**
     * Posts a balanced journal and updates balances. Idempotent: the same request with the same
     * key returns the original journal ({@code replayed = true}); the same key with a different
     * request fails with IDEMPOTENCY_CONFLICT (H7).
     */
    PostedJournal post(JournalRequest request);

    /**
     * Posts the reversal of a journal: the same lines with directions swapped (spec 7.1 #3).
     * A journal can be reversed once; a reversal cannot itself be reversed. Publishes
     * {@link JournalReversed} in the same transaction.
     */
    PostedJournal reverse(long journalId, String reason);

    Optional<PostedJournal> findJournal(UUID publicId);

    /** Current balance of an account, signed by its normal side; zero if it has never been used. */
    Money balance(AccountRef account);

    /** The member's savings balance per bucket (internal bucket id), from the balance projection. */
    Map<Long, Money> memberSavingsByBucket(long membershipId);

    /** Balance of an account from all lines dated before {@code date} - point in time, from the lines themselves. */
    Money balanceBefore(AccountRef account, LocalDate date);

    /** Lines on an account dated from..to inclusive, oldest first, with running balances. */
    List<StatementEntry> entries(AccountRef account, LocalDate from, LocalDate to);
}
