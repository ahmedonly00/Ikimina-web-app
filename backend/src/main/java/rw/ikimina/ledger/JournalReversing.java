package rw.ikimina.ledger;

/**
 * Published inside the reversing transaction just before the ledger locks any balance, so the
 * module that owns the original journal's business record can lock its own rows first - in the same
 * order its normal writes use (business rows, then ledger balances) - and two writers never wait on
 * each other in opposite orders. {@link JournalReversed} follows once the reversal is posted.
 */
public record JournalReversing(long groupId, long journalId, JournalType originalType) {
}
