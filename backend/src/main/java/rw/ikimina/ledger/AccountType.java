package rw.ikimina.ledger;

/**
 * Ledger account types (spec 6.2) with their normal side: assets and expenses grow with debits;
 * what the group owes (member savings), its income and its equity grow with credits.
 * Balances are stored signed by the normal side, so a healthy balance is positive.
 */
public enum AccountType {
    GROUP_CASH(Direction.DEBIT),
    MEMBER_SAVINGS(Direction.CREDIT),
    SOCIAL_FUND(Direction.CREDIT),
    LOAN_RECEIVABLE(Direction.DEBIT),
    INTEREST_INCOME(Direction.CREDIT),
    FINE_RECEIVABLE(Direction.DEBIT),
    FINE_INCOME(Direction.CREDIT),
    EXPENSE(Direction.DEBIT),
    SHARE_OUT_PAYABLE(Direction.CREDIT),
    EQUITY(Direction.CREDIT);

    private final Direction normalSide;

    AccountType(Direction normalSide) {
        this.normalSide = normalSide;
    }

    public Direction normalSide() {
        return normalSide;
    }
}
