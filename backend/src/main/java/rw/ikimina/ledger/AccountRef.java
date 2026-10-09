package rw.ikimina.ledger;

import java.util.Objects;

/**
 * Identifies a ledger account within the current group by its dimensions (spec 6.2). Accounts
 * are created on first use. Ids are internal (membership, bucket, loan rows of this group).
 */
public record AccountRef(AccountType type, Long membershipId, Long bucketId, Long loanId) {

    public AccountRef {
        Objects.requireNonNull(type, "type");
    }

    public static AccountRef of(AccountType type) {
        return new AccountRef(type, null, null, null);
    }

    public static AccountRef memberSavings(long membershipId, long bucketId) {
        return new AccountRef(AccountType.MEMBER_SAVINGS, membershipId, bucketId, null);
    }
}
