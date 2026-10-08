package rw.ikimina.shared.money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class MoneyTest {

    private static Money rwf(String value) {
        return Money.of(new BigDecimal(value));
    }

    @Nested
    class Posting {

        @ParameterizedTest(name = "{0} posts as {1}")
        @CsvSource({
                "0.5, 1.00",
                "1.49, 1.00",
                "1.5, 2.00",
                // HALF_UP, not banker's rounding: 2.5 must not become 2.
                "2.5, 3.00",
                "-0.5, -1.00",
                "-1.5, -2.00",
                "150000, 150000.00",
                "0, 0.00",
        })
        void roundsHalfUpToWholeFrancs(String amount, String posted) {
            assertThat(rwf(amount).toPostingAmount()).isEqualTo(new BigDecimal(posted));
        }

        @Test
        void roundsOnceFromFullPrecisionWithoutDoubleRounding() {
            // Rounding to cents first (1.50) and then to francs would give 2.
            assertThat(rwf("1.499999").toPostingAmount()).isEqualTo(new BigDecimal("1.00"));
        }

        @Test
        void postingAmountIsAlwaysAtStorageScale() {
            assertThat(rwf("7").toPostingAmount().scale()).isEqualTo(Money.STORAGE_SCALE);
        }

        @Test
        void interestOnAnAwkwardBalanceRoundsOnlyAtTheBoundary() {
            Money monthlyInterest = Money.ofWholeRwf(123_457).percent(new BigDecimal("1.5"));
            assertThat(monthlyInterest.toBigDecimal()).isEqualByComparingTo("1851.855");
            assertThat(monthlyInterest.toPostingAmount()).isEqualTo(new BigDecimal("1852.00"));
        }
    }

    @Nested
    class Arithmetic {

        @Test
        void addsAndSubtracts() {
            assertThat(rwf("100.25").plus(rwf("0.75"))).isEqualTo(Money.ofWholeRwf(101));
            assertThat(Money.ofWholeRwf(100).minus(Money.ofWholeRwf(150))).isEqualTo(Money.ofWholeRwf(-50));
            assertThat(Money.ofWholeRwf(30).negate()).isEqualTo(Money.ofWholeRwf(-30));
        }

        @Test
        void isImmutable() {
            Money original = Money.ofWholeRwf(100);
            original.plus(Money.ofWholeRwf(1));
            assertThat(original).isEqualTo(Money.ofWholeRwf(100));
        }

        @Test
        void multipliesByAFactorAndAPercentage() {
            assertThat(Money.ofWholeRwf(150_000).times(new BigDecimal("0.05"))).isEqualTo(Money.ofWholeRwf(7_500));
            assertThat(Money.ofWholeRwf(150_000).percent(new BigDecimal("5"))).isEqualTo(Money.ofWholeRwf(7_500));
            assertThat(Money.ofWholeRwf(100_000).percent(new BigDecimal("2.5"))).isEqualTo(Money.ofWholeRwf(2_500));
        }

        @Test
        void keepsSixDecimalsInIntermediateResults() {
            Money third = Money.ofWholeRwf(100).dividedBy(new BigDecimal("3"));
            assertThat(third.toBigDecimal()).isEqualTo(new BigDecimal("33.333333"));
            assertThat(third.times(new BigDecimal("3")).toBigDecimal()).isEqualTo(new BigDecimal("99.999999"));
            assertThat(third.times(new BigDecimal("3")).toPostingAmount()).isEqualTo(new BigDecimal("100.00"));
        }

        @Test
        void refusesDivisionByZero() {
            assertThatThrownBy(() -> Money.ofWholeRwf(1).dividedBy(BigDecimal.ZERO))
                    .isInstanceOf(ArithmeticException.class);
        }

        @Test
        void comparesNumerically() {
            assertThat(rwf("10.5")).isGreaterThan(rwf("10.49"));
            assertThat(Money.ofWholeRwf(-1).signum()).isEqualTo(-1);
            assertThat(Money.ZERO.isZero()).isTrue();
            assertThat(Money.ofWholeRwf(1).isPositive()).isTrue();
            assertThat(Money.ofWholeRwf(-1).isNegative()).isTrue();
        }
    }

    @Nested
    class Equality {

        @Test
        void ignoresTheScaleTheValueWasWrittenWith() {
            assertThat(rwf("5")).isEqualTo(rwf("5.00")).hasSameHashCodeAs(rwf("5.000000"));
            assertThat(Money.ZERO).isEqualTo(Money.ofWholeRwf(0)).isEqualTo(rwf("-0.00"));
        }

        @Test
        void distinguishesDifferentAmounts() {
            assertThat(rwf("5.01")).isNotEqualTo(rwf("5"));
            assertThat(rwf("5")).isNotEqualTo(new BigDecimal("5"));
        }
    }

    @Nested
    class Range {

        @Test
        void acceptsTheLargestAmountANumeric19Point2ColumnHolds() {
            assertThat(rwf("99999999999999999.99").toStorageAmount()).isEqualTo(new BigDecimal("99999999999999999.99"));
        }

        @Test
        void rejectsAmountsTheColumnCannotHold() {
            assertThatThrownBy(() -> rwf("100000000000000000")).isInstanceOf(ArithmeticException.class);
            assertThatThrownBy(() -> rwf("-100000000000000000")).isInstanceOf(ArithmeticException.class);
        }

        @Test
        void refusesToStoreAmountsWithMoreThanTwoDecimalsWithoutAnExplicitRounding() {
            assertThat(rwf("10.25").toStorageAmount()).isEqualTo(new BigDecimal("10.25"));
            assertThatThrownBy(() -> rwf("10.255").toStorageAmount()).isInstanceOf(ArithmeticException.class);
            assertThat(rwf("10.255").roundedToWholeRwf().toStorageAmount()).isEqualTo(new BigDecimal("10.00"));
        }

        @Test
        void knowsWhetherItIsAWholeFranc() {
            assertThat(rwf("5.00").isWholeRwf()).isTrue();
            assertThat(Money.ZERO.isWholeRwf()).isTrue();
            assertThat(rwf("5.5").isWholeRwf()).isFalse();
            assertThat(rwf("5.000001").isWholeRwf()).isFalse();
        }
    }

    @Nested
    class WireFormat {

        @ParameterizedTest
        @CsvSource({"0, 0.00", "150000, 150000.00", "150000.5, 150000.50", "150000.50, 150000.50", "-12.34, -12.34"})
        void parsesPlainDecimalStrings(String text, String wire) {
            assertThat(Money.parse(text).toWireString()).isEqualTo(wire);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {" 1", "1 ", "+1", "01", "1.", ".5", "1.234", "1,000", "1e3", "0x10", "NaN",
                "Infinity", "--1", "100000000000000000", "١٢"})
        void rejectsAnythingElse(String text) {
            assertThatThrownBy(() -> Money.parse(text)).isInstanceOf(MoneyFormatException.class);
        }

        @Test
        void doesNotEchoLongUntrustedInputInFull() {
            String hostile = "9".repeat(5_000);
            assertThatThrownBy(() -> Money.parse(hostile))
                    .isInstanceOf(MoneyFormatException.class)
                    .satisfies(e -> assertThat(e.getMessage()).hasSizeLessThan(100));
        }

        @Test
        void serialisesWithTwoDecimals() {
            assertThat(Money.ofWholeRwf(150_000).toWireString()).isEqualTo("150000.00");
            assertThat(rwf("0.125").toWireString()).isEqualTo("0.13");
            assertThat(Money.ofWholeRwf(150_000)).hasToString("150000.00 RWF");
        }
    }
}
