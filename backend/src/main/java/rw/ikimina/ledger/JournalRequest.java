package rw.ikimina.ledger;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

import rw.ikimina.shared.money.Money;

/**
 * One financial event to post (spec 7.1): at least two lines whose debits equal their credits.
 *
 * @param idempotencyKey unique per group; posting the same request again returns the original journal (H7)
 * @param requestHash    fingerprint of the caller's request: the same key with a different hash is refused
 * @param memberId       the membership the event concerns, if any (internal id)
 */
public record JournalRequest(JournalType type, String idempotencyKey, String requestHash, LocalDate businessDate,
                             String description, JournalSource source, String externalRef, Long memberId,
                             List<Line> lines) {

    public record Line(AccountRef account, Direction direction, Money amount) {
        public Line {
            Objects.requireNonNull(account, "account");
            Objects.requireNonNull(direction, "direction");
            Objects.requireNonNull(amount, "amount");
        }

        public static Line debit(AccountRef account, Money amount) {
            return new Line(account, Direction.DEBIT, amount);
        }

        public static Line credit(AccountRef account, Money amount) {
            return new Line(account, Direction.CREDIT, amount);
        }
    }

    public JournalRequest {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        Objects.requireNonNull(requestHash, "requestHash");
        Objects.requireNonNull(businessDate, "businessDate");
        Objects.requireNonNull(source, "source");
        lines = List.copyOf(lines);
    }
}
