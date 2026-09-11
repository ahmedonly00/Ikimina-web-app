package com.example.ikimina.money;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Conventions for monetary arithmetic.
 *
 * Every amount in this system is a {@link BigDecimal} with a fixed scale of
 * {@link #SCALE}. Money is never held in {@code double}/{@code float}: binary
 * floating point cannot represent decimal currency exactly, so accumulating
 * contributions over a cycle makes group totals drift from the sum of member
 * balances. In a savings group, books that do not balance destroy trust.
 *
 * All rounding is HALF_UP, applied only where a value is stored or reported -
 * never mid-computation.
 */
public final class Money {

    /** Minor units retained. RWF is used in whole francs, but interest and
     *  proportional splits need sub-unit precision before rounding. */
    public static final int SCALE = 2;

    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(SCALE);

    private Money() {
    }

    /** Normalises a value to the storage scale. Null becomes zero. */
    public static BigDecimal of(BigDecimal value) {
        return value == null ? ZERO : value.setScale(SCALE, ROUNDING);
    }

    public static BigDecimal of(String value) {
        return new BigDecimal(value).setScale(SCALE, ROUNDING);
    }

    public static BigDecimal of(long value) {
        return BigDecimal.valueOf(value).setScale(SCALE, ROUNDING);
    }

    /** Null-safe sum, normalised to scale. */
    public static BigDecimal add(BigDecimal a, BigDecimal b) {
        return of(nz(a).add(nz(b)));
    }

    public static BigDecimal subtract(BigDecimal a, BigDecimal b) {
        return of(nz(a).subtract(nz(b)));
    }

    /** Multiplies by a rate (e.g. interest) and rounds once, at the end. */
    public static BigDecimal multiply(BigDecimal amount, BigDecimal rate) {
        return of(nz(amount).multiply(nz(rate)));
    }

    /** Sums a sequence without intermediate rounding. */
    public static BigDecimal sum(Iterable<BigDecimal> values) {
        BigDecimal total = BigDecimal.ZERO;
        for (BigDecimal v : values) {
            total = total.add(nz(v));
        }
        return of(total);
    }

    public static boolean isPositive(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0;
    }

    public static boolean isZero(BigDecimal value) {
        return value == null || value.compareTo(BigDecimal.ZERO) == 0;
    }

    /**
     * Value equality that ignores scale. {@code BigDecimal.equals} treats
     * 100.00 and 100.000 as different; use this for any money comparison.
     */
    public static boolean eq(BigDecimal a, BigDecimal b) {
        return nz(a).compareTo(nz(b)) == 0;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
