package rw.ikimina.loans.internal;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * The money terms that decide what a borrower repays (spec 6.4): how interest is charged and how
 * repayments fall due and are split. A loan keeps a copy of its product's terms from the moment it
 * is requested.
 *
 * @param interestRatePercent per month ({@link InterestPeriod#MONTH}) or for the whole term
 * @param allocationOrder     the order a repayment pays fines, interest and principal (spec 9.6)
 */
record LoanTerms(InterestMethod interestMethod, BigDecimal interestRatePercent, InterestPeriod interestPeriod,
                 RepaymentFrequency repaymentFrequency, int graceDays, List<Component> allocationOrder) {

    enum InterestMethod { FLAT, REDUCING_BALANCE }

    enum InterestPeriod { MONTH, LOAN_TERM }

    enum RepaymentFrequency { MONTHLY, WEEKLY, AT_MATURITY }

    enum Component { FINES, INTEREST, PRINCIPAL }

    static final List<Component> DEFAULT_ALLOCATION = List.of(Component.FINES, Component.INTEREST, Component.PRINCIPAL);

    LoanTerms {
        Objects.requireNonNull(interestMethod, "interestMethod");
        Objects.requireNonNull(interestRatePercent, "interestRatePercent");
        Objects.requireNonNull(interestPeriod, "interestPeriod");
        Objects.requireNonNull(repaymentFrequency, "repaymentFrequency");
        allocationOrder = List.copyOf(allocationOrder);
    }
}
