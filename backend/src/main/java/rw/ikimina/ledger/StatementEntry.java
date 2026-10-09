package rw.ikimina.ledger;

import java.time.LocalDate;
import java.util.UUID;

import rw.ikimina.shared.money.Money;

/** One ledger line on an account, as a statement shows it; {@code balanceAfter} is the running balance. */
public record StatementEntry(UUID journalId, LocalDate businessDate, JournalType type, String description,
                             String externalRef, Direction direction, Money amount, Money balanceAfter) {
}
