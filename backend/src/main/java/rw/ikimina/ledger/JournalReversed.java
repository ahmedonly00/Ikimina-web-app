package rw.ikimina.ledger;

/**
 * Published inside the transaction that posts a reversal, so the module that owns the business
 * record of the original journal (e.g. a contribution) can mark it reversed in the same commit.
 */
public record JournalReversed(long groupId, long journalId, JournalType originalType, long reversalJournalId) {
}
