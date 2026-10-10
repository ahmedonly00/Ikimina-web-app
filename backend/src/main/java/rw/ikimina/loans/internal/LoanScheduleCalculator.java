package rw.ikimina.loans.internal;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import rw.ikimina.shared.money.Money;

/**
 * Builds a loan's repayment schedule (spec 9.5). Pure: no clock, no database - every input is a
 * parameter, so it is tested against golden files.
 *
 * <ul>
 *   <li><b>FLAT</b>: interest = principal × rate × periods (periods = the term in months for a
 *       monthly rate, 1 for a rate over the whole term), rounded once to a whole franc, then spread
 *       evenly; principal is spread evenly too. The last installment takes what rounding left.</li>
 *   <li><b>REDUCING_BALANCE</b>: standard amortisation at a monthly rate, monthly installments.
 *       The level payment is rounded to a whole franc; each month's interest is the outstanding
 *       balance × rate, rounded; the last installment clears exactly what is left, so principal
 *       always sums to the amount lent.</li>
 * </ul>
 *
 * <p>Installment {@code k} falls due {@code k} months after the disbursement date (counted from
 * the disbursement date each time, so 31 January gives 28/29 February, 31 March, 30 April); an
 * AT_MATURITY loan has one installment at the end of the term. Weekly repayment is not offered
 * yet (owner decision, Phase 3), nor is a reducing balance with a whole-term rate or a single
 * repayment, whose meaning the spec leaves open - {@link #supports} refuses them.
 */
final class LoanScheduleCalculator {

    record Installment(int number, LocalDate dueDate, Money principal, Money interest) {

        Money total() {
            return principal.plus(interest);
        }
    }

    private LoanScheduleCalculator() {
    }

    /** Whether this combination of terms has a defined schedule. */
    static boolean supports(LoanTerms.InterestMethod method, LoanTerms.InterestPeriod period, LoanTerms.RepaymentFrequency frequency) {
        if (frequency == LoanTerms.RepaymentFrequency.WEEKLY) {
            return false;
        }
        return method == LoanTerms.InterestMethod.FLAT
                || period == LoanTerms.InterestPeriod.MONTH && frequency == LoanTerms.RepaymentFrequency.MONTHLY;
    }

    static List<Installment> schedule(Money principal, int termMonths, LoanTerms terms, LocalDate disbursedOn) {
        Objects.requireNonNull(disbursedOn, "disbursedOn");
        if (!principal.isPositive() || !principal.isWholeRwf() || termMonths < 1
                || !supports(terms.interestMethod(), terms.interestPeriod(), terms.repaymentFrequency())) {
            throw new IllegalArgumentException("No schedule for " + principal + " over " + termMonths + " months with " + terms);
        }
        int count = terms.repaymentFrequency() == LoanTerms.RepaymentFrequency.AT_MATURITY ? 1 : termMonths;
        List<LocalDate> dueDates = new ArrayList<>(count);
        for (int k = 1; k <= count; k++) {
            dueDates.add(disbursedOn.plusMonths(count == 1 ? termMonths : k));
        }
        return terms.interestMethod() == LoanTerms.InterestMethod.FLAT
                ? flat(principal, termMonths, terms, dueDates)
                : reducingBalance(principal, terms.interestRatePercent(), dueDates);
    }

    private static List<Installment> flat(Money principal, int termMonths, LoanTerms terms, List<LocalDate> dueDates) {
        BigDecimal periods = BigDecimal.valueOf(terms.interestPeriod() == LoanTerms.InterestPeriod.MONTH ? termMonths : 1);
        Money interest = principal.percent(terms.interestRatePercent()).times(periods).roundedToWholeRwf();
        List<Money> principalShares = principal.splitWholeRwf(dueDates.size());
        List<Money> interestShares = interest.splitWholeRwf(dueDates.size());
        List<Installment> rows = new ArrayList<>(dueDates.size());
        for (int i = 0; i < dueDates.size(); i++) {
            rows.add(new Installment(i + 1, dueDates.get(i), principalShares.get(i), interestShares.get(i)));
        }
        return List.copyOf(rows);
    }

    private static List<Installment> reducingBalance(Money principal, BigDecimal ratePercent, List<LocalDate> dueDates) {
        int n = dueDates.size();
        BigDecimal rate = ratePercent.divide(BigDecimal.valueOf(100), Money.MATH_CONTEXT);
        Money payment;
        if (rate.signum() == 0) {
            payment = principal.dividedBy(BigDecimal.valueOf(n)).roundedToWholeRwf();
        } else {
            // A = P * i * (1+i)^n / ((1+i)^n - 1)
            BigDecimal growth = BigDecimal.ONE.add(rate).pow(n, Money.MATH_CONTEXT);
            BigDecimal factor = rate.multiply(growth, Money.MATH_CONTEXT).divide(growth.subtract(BigDecimal.ONE), Money.MATH_CONTEXT);
            payment = principal.times(factor).roundedToWholeRwf();
        }
        List<Installment> rows = new ArrayList<>(n);
        Money balance = principal;
        for (int k = 1; k <= n; k++) {
            Money interest = balance.times(rate).roundedToWholeRwf();
            Money principalPart = k == n ? balance : payment.minus(interest);
            if (principalPart.compareTo(balance) > 0) {
                principalPart = balance;
            }
            if (principalPart.isNegative()) {
                principalPart = Money.ZERO;
            }
            rows.add(new Installment(k, dueDates.get(k - 1), principalPart, interest));
            balance = balance.minus(principalPart);
        }
        return List.copyOf(rows);
    }
}
