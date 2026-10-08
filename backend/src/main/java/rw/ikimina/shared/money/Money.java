package rw.ikimina.shared.money;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * An amount of Rwandan francs. The single place where money is scaled and rounded
 * (spec 4.3, Hard Rule H1); no other class calls {@code setScale} on an amount.
 *
 * <ul>
 *   <li>Held at {@link #CALCULATION_SCALE} (6) so intermediate results such as
 *       interest on a reducing balance keep their precision.</li>
 *   <li>Arithmetic that can produce long fractions uses {@link #MATH_CONTEXT}
 *       (DECIMAL64, 16 significant digits). Every amount below 10^10 RWF therefore
 *       keeps all six decimals, and whole-franc accuracy holds to 10^16.</li>
 *   <li>Anything posted to the ledger goes through {@link #toPostingAmount()}, which
 *       rounds to a whole franc with {@link RoundingMode#HALF_UP}. That is the only
 *       rounding to whole francs in the system.</li>
 *   <li>The magnitude is bounded by what a {@code NUMERIC(19,2)} column can hold, so
 *       an absurd amount fails here rather than at the database.</li>
 * </ul>
 *
 * <p>Values may be negative (a balance or a difference can be); whether a negative
 * amount is acceptable is a business rule decided by the caller.
 */
public final class Money implements Comparable<Money> {

    public static final String CURRENCY = "RWF";

    /** Scale of the {@code NUMERIC(19,2)} columns money is stored in. */
    public static final int STORAGE_SCALE = 2;

    /** Scale of intermediate calculations (spec 4.3). */
    public static final int CALCULATION_SCALE = 6;

    public static final MathContext MATH_CONTEXT = MathContext.DECIMAL64;

    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    /** {@code NUMERIC(19,2)} leaves 17 digits before the decimal point. */
    private static final BigDecimal MAGNITUDE_LIMIT = BigDecimal.TEN.pow(17);

    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    /**
     * What the API accepts: a plain decimal string with at most two decimals.
     * No exponent, no grouping separators, no leading '+', no leading zeros.
     */
    private static final Pattern WIRE_FORMAT = Pattern.compile("-?(0|[1-9][0-9]{0,16})(\\.[0-9]{1,2})?");

    // Declared after the constants the constructor reads: static fields initialise in source order.
    public static final Money ZERO = new Money(BigDecimal.ZERO);

    private final BigDecimal amount;

    private Money(BigDecimal value) {
        BigDecimal scaled = value.setScale(CALCULATION_SCALE, ROUNDING);
        if (scaled.abs().compareTo(MAGNITUDE_LIMIT) >= 0) {
            throw new ArithmeticException("Amount exceeds the storable range: " + value.toPlainString());
        }
        this.amount = scaled;
    }

    public static Money of(BigDecimal value) {
        Objects.requireNonNull(value, "value");
        return new Money(value);
    }

    public static Money ofWholeRwf(long francs) {
        return new Money(BigDecimal.valueOf(francs));
    }

    /**
     * Parses an amount as it arrives over the API (spec 17: money travels as strings).
     *
     * @throws MoneyFormatException if the text is not a plain decimal with at most
     *                              two decimals, or is out of range
     */
    public static Money parse(String text) {
        if (text == null || !WIRE_FORMAT.matcher(text).matches()) {
            throw new MoneyFormatException("Not a valid RWF amount: " + abbreviate(text));
        }
        return new Money(new BigDecimal(text));
    }

    public Money plus(Money other) {
        return new Money(amount.add(other.amount));
    }

    public Money minus(Money other) {
        return new Money(amount.subtract(other.amount));
    }

    public Money negate() {
        return new Money(amount.negate());
    }

    /** Multiplies by a dimensionless factor, e.g. a number of periods or a rate already expressed as a fraction. */
    public Money times(BigDecimal factor) {
        Objects.requireNonNull(factor, "factor");
        return new Money(amount.multiply(factor, MATH_CONTEXT));
    }

    /** {@code percent} per cent of this amount: {@code 150000.percent(5)} is 7500. */
    public Money percent(BigDecimal percent) {
        Objects.requireNonNull(percent, "percent");
        return new Money(amount.multiply(percent, MATH_CONTEXT).divide(ONE_HUNDRED, MATH_CONTEXT));
    }

    /** @throws ArithmeticException if {@code divisor} is zero */
    public Money dividedBy(BigDecimal divisor) {
        Objects.requireNonNull(divisor, "divisor");
        return new Money(amount.divide(divisor, MATH_CONTEXT));
    }

    /** This amount rounded to a whole franc, half up. */
    public Money roundedToWholeRwf() {
        return new Money(amount.setScale(0, ROUNDING));
    }

    public boolean isWholeRwf() {
        return amount.stripTrailingZeros().scale() <= 0;
    }

    /**
     * The value to write into a ledger line: rounded to a whole franc (spec 4.3)
     * and expressed at the column's scale.
     */
    public BigDecimal toPostingAmount() {
        return amount.setScale(0, ROUNDING).setScale(STORAGE_SCALE, RoundingMode.UNNECESSARY);
    }

    /**
     * The value to write into a non-ledger {@code NUMERIC(19,2)} column. Refuses
     * to round silently: an amount with more than two decimals must be rounded
     * deliberately first (normally via {@link #roundedToWholeRwf()}).
     *
     * @throws ArithmeticException if the amount has more than two decimals
     */
    public BigDecimal toStorageAmount() {
        try {
            return amount.setScale(STORAGE_SCALE, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException e) {
            throw new ArithmeticException("Amount " + amount.toPlainString()
                    + " has more than " + STORAGE_SCALE + " decimals; round it before storing");
        }
    }

    /** The API representation, e.g. {@code "150000.00"}. */
    public String toWireString() {
        return amount.setScale(STORAGE_SCALE, ROUNDING).toPlainString();
    }

    /** The exact value at calculation scale. */
    public BigDecimal toBigDecimal() {
        return amount;
    }

    public int signum() {
        return amount.signum();
    }

    public boolean isZero() {
        return amount.signum() == 0;
    }

    public boolean isPositive() {
        return amount.signum() > 0;
    }

    public boolean isNegative() {
        return amount.signum() < 0;
    }

    @Override
    public int compareTo(Money other) {
        return amount.compareTo(other.amount);
    }

    /** Equal when numerically equal; the scale is always normalised, so 5 and 5.00 are the same amount. */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Money other)) {
            return false;
        }
        return amount.equals(other.amount);
    }

    @Override
    public int hashCode() {
        return amount.hashCode();
    }

    @Override
    public String toString() {
        return toWireString() + " " + CURRENCY;
    }

    /** Untrusted input ends up in error messages and logs; keep it short. */
    private static String abbreviate(String text) {
        if (text == null) {
            return "null";
        }
        return text.length() <= 32 ? "'" + text + "'" : "'" + text.substring(0, 32) + "...'";
    }
}
