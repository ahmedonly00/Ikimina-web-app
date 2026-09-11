package com.example.ikimina.ledger;

/** What kind of money movement an entry records. */
public enum LedgerEntryType {
    SAVINGS_CONTRIBUTION,
    FINE_CHARGED,
    FINE_PAID,
    LOAN_DISBURSED,
    LOAN_REPAID,
    INTEREST_ACCRUED,
    PAYOUT,
    SUBSCRIPTION_PAYMENT,
    /** Manual correction. Always paired with the entry it reverses. */
    ADJUSTMENT
}
