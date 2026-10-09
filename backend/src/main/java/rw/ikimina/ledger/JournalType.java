package rw.ikimina.ledger;

/** Spec 6.2 journal types. */
public enum JournalType {
    CONTRIBUTION,
    WITHDRAWAL,
    LOAN_DISBURSEMENT,
    LOAN_REPAYMENT,
    INTEREST_ACCRUAL,
    FINE_ASSESSED,
    FINE_PAID,
    FINE_WAIVED,
    EXPENSE,
    INCOME,
    SHARE_OUT,
    REVERSAL,
    ADJUSTMENT
}
