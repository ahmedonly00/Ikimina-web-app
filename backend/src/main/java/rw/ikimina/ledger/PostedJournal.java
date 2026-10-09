package rw.ikimina.ledger;

import java.time.LocalDate;
import java.util.UUID;

/**
 * A journal in the ledger.
 *
 * @param replayed true when the request had been posted before and this is the original journal (H7)
 */
public record PostedJournal(long id, UUID publicId, JournalType type, LocalDate businessDate, boolean replayed) {
}
