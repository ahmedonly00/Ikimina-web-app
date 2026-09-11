package com.example.ikimina.money;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * These tests exist to pin down the behaviour that made the double-based code
 * wrong. Each one fails if money ever goes back to floating point.
 */
class MoneyTest {

    @Test
    @DisplayName("the classic case double gets wrong: 0.1 + 0.2 == 0.3 exactly")
    void addsDecimalsExactly() {
        BigDecimal sum = Money.add(Money.of("0.10"), Money.of("0.20"));

        assertEquals(0, sum.compareTo(Money.of("0.30")),
                "expected exactly 0.30 but got " + sum.toPlainString());

        // The same computation in double does not hold, which is the whole point.
        assertNotEquals(0.3, 0.1 + 0.2);
    }

    @Test
    @DisplayName("summing many small contributions does not drift")
    void sumOfManyContributionsDoesNotDrift() {
        // 1000 contributions of 0.01 must be exactly 10.00
        List<BigDecimal> contributions = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            contributions.add(Money.of("0.01"));
        }

        assertEquals(0, Money.sum(contributions).compareTo(Money.of("10.00")));

        // The double equivalent drifts measurably.
        double dbl = 0.0;
        for (int i = 0; i < 1000; i++) {
            dbl += 0.01;
        }
        assertNotEquals(10.0, dbl, "double accumulation is expected to drift");
    }

    @Test
    @DisplayName("a cycle's total equals the sum of member totals, for random amounts")
    void groupTotalReconcilesWithMemberTotals() {
        // Property check: however contributions are distributed across members,
        // summing per-member totals must equal the overall total exactly.
        Random random = new Random(20260910L);

        for (int trial = 0; trial < 200; trial++) {
            int members = 1 + random.nextInt(30);
            List<BigDecimal> memberTotals = new ArrayList<>();
            BigDecimal runningTotal = BigDecimal.ZERO;

            for (int m = 0; m < members; m++) {
                int entries = random.nextInt(20);
                BigDecimal memberTotal = BigDecimal.ZERO;
                for (int e = 0; e < entries; e++) {
                    // Amounts with cents, the shape that breaks floating point.
                    BigDecimal amount = BigDecimal.valueOf(random.nextInt(1_000_00), 2);
                    memberTotal = memberTotal.add(amount);
                    runningTotal = runningTotal.add(amount);
                }
                memberTotals.add(Money.of(memberTotal));
            }

            assertEquals(0, Money.sum(memberTotals).compareTo(Money.of(runningTotal)),
                    "trial " + trial + ": member totals must reconcile with the group total");
        }
    }

    @Test
    @DisplayName("interest is rounded once, at the boundary")
    void multipliesAndRoundsOnce() {
        // 5000.10 at 5.25% = 262.50525 -> 262.51 (HALF_UP)
        BigDecimal interest = Money.multiply(Money.of("5000.10"), new BigDecimal("0.0525"));
        assertEquals("262.51", interest.toPlainString());
    }

    @Test
    @DisplayName("HALF_UP is applied consistently at the half-cent")
    void roundsHalfUp() {
        assertEquals("0.13", Money.of(new BigDecimal("0.125")).toPlainString());
        assertEquals("0.13", Money.of(new BigDecimal("0.126")).toPlainString());
        assertEquals("0.12", Money.of(new BigDecimal("0.124")).toPlainString());
    }

    @Test
    @DisplayName("value equality ignores scale, unlike BigDecimal.equals")
    void comparesByValueNotScale() {
        BigDecimal a = new BigDecimal("100.00");
        BigDecimal b = new BigDecimal("100.000");

        assertNotEquals(a, b, "BigDecimal.equals is scale-sensitive");
        assertTrue(Money.eq(a, b), "Money.eq must compare by value");
    }

    @Test
    @DisplayName("nulls are treated as zero rather than throwing")
    void handlesNulls() {
        assertEquals(0, Money.of((BigDecimal) null).compareTo(BigDecimal.ZERO));
        assertEquals(0, Money.add(null, Money.of("5.00")).compareTo(Money.of("5.00")));
        assertEquals(0, Money.subtract(null, null).compareTo(BigDecimal.ZERO));
        assertTrue(Money.isZero(null));
        assertFalse(Money.isPositive(null));
    }

    @Test
    @DisplayName("subtraction is exact where double would leave a residue")
    void subtractsExactly() {
        // Distributing everything must leave exactly zero.
        BigDecimal collected = Money.of("1000.85");
        BigDecimal distributed = Money.add(Money.add(Money.of("0.10"), Money.of("0.20")), Money.of("1000.55"));

        assertEquals(0, Money.subtract(collected, distributed).compareTo(BigDecimal.ZERO),
                "fully distributed cycle must leave a zero balance");
    }
}
