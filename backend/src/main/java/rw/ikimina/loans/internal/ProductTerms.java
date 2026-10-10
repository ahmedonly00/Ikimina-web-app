package rw.ikimina.loans.internal;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import rw.ikimina.shared.error.ApiException;
import rw.ikimina.shared.error.ErrorCode;
import rw.ikimina.shared.money.Money;

/**
 * A loan product's money terms (spec 6.4): everything that decides who may borrow how much and
 * what they repay. Once a product exists, changing any of these needs a second officer (owner
 * decision, Phase 3).
 *
 * @param maxMultipleOfSavings  the most a member may borrow as a multiple of their savings; null = no limit
 * @param dualApprovalThreshold from this amount up, President and Treasurer must both approve; null = always both (spec 9.3)
 * @param allowConcurrentLoans  whether a member with an unfinished loan may still borrow from this product
 */
record ProductTerms(
        @NotNull LoanTerms.InterestMethod interestMethod,
        // Rates and multiples travel as strings, like money, so no client reads them as binary floating point (H1).
        @NotNull @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal interestRatePercent,
        @NotNull LoanTerms.InterestPeriod interestPeriod,
        Money minAmount,
        Money maxAmount,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal maxMultipleOfSavings,
        @NotNull @Min(1) @Max(120) Integer minTermMonths,
        @NotNull @Min(1) @Max(120) Integer maxTermMonths,
        @NotNull LoanTerms.RepaymentFrequency repaymentFrequency,
        @NotNull @Min(0) @Max(365) Integer graceDays,
        Money dualApprovalThreshold,
        @Size(min = 3, max = 3) List<LoanTerms.Component> allocationOrder,
        @NotNull Boolean allowConcurrentLoans) {

    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    ProductTerms {
        allocationOrder = allocationOrder == null ? LoanTerms.DEFAULT_ALLOCATION : List.copyOf(allocationOrder);
    }

    /** Equal terms, comparing numbers by value (a stored {@code 5.0000} is the same rate as {@code 5}). */
    boolean sameAs(ProductTerms other) {
        return interestMethod == other.interestMethod && interestRatePercent.compareTo(other.interestRatePercent) == 0
                && interestPeriod == other.interestPeriod
                && Objects.equals(minAmount, other.minAmount) && Objects.equals(maxAmount, other.maxAmount)
                && (maxMultipleOfSavings == null ? other.maxMultipleOfSavings == null
                    : other.maxMultipleOfSavings != null && maxMultipleOfSavings.compareTo(other.maxMultipleOfSavings) == 0)
                && minTermMonths.equals(other.minTermMonths) && maxTermMonths.equals(other.maxTermMonths)
                && repaymentFrequency == other.repaymentFrequency && graceDays.equals(other.graceDays)
                && Objects.equals(dualApprovalThreshold, other.dualApprovalThreshold)
                && allocationOrder.equals(other.allocationOrder) && allowConcurrentLoans.equals(other.allowConcurrentLoans);
    }

    LoanTerms loanTerms() {
        return new LoanTerms(interestMethod, interestRatePercent, interestPeriod, repaymentFrequency, graceDays, allocationOrder);
    }

    /**
     * Spec 6.4 and 9.5 rules the terms must satisfy, whoever sets them.
     *
     * @throws ApiException VALIDATION_FAILED, or LOAN_TERMS_UNSUPPORTED for terms whose schedule is not defined yet
     */
    void validate() {
        boolean valid = interestRatePercent.signum() >= 0 && interestRatePercent.compareTo(ONE_HUNDRED) <= 0
                && interestRatePercent.scale() <= 4
                && wholePositive(minAmount) && wholePositive(maxAmount) && wholePositive(dualApprovalThreshold)
                && (minAmount == null || maxAmount == null || maxAmount.compareTo(minAmount) >= 0)
                && (maxMultipleOfSavings == null || maxMultipleOfSavings.signum() > 0 && maxMultipleOfSavings.scale() <= 2
                    && maxMultipleOfSavings.compareTo(BigDecimal.valueOf(100_000)) < 0)
                && maxTermMonths >= minTermMonths
                && allocationOrder.size() == 3 && EnumSet.copyOf(allocationOrder).size() == 3;
        if (!valid) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED);
        }
        if (!LoanScheduleCalculator.supports(interestMethod, interestPeriod, repaymentFrequency)) {
            throw new ApiException(ErrorCode.LOAN_TERMS_UNSUPPORTED);
        }
    }

    private static boolean wholePositive(Money amount) {
        return amount == null || amount.isPositive() && amount.isWholeRwf();
    }
}
