package rw.ikimina.loans.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import rw.ikimina.shared.money.Money;

/**
 * Phase 3 acceptance: golden schedule tests (spec 9.5). Each file under {@code loans/golden} holds
 * the terms in a {@code # method=...} line and the expected rows (number, due date, principal,
 * interest), worked out independently of this code - the arithmetic is in the file's comments.
 */
class LoanScheduleCalculatorTest {

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "flat-1-month", "flat-3-months-month-end", "flat-6-months-awkward-rate", "flat-12-months-whole-term-rate",
            "flat-at-maturity", "reducing-12-months", "reducing-6-months-awkward-rate", "reducing-3-months-interest-free"})
    void matchesTheGoldenFile(String name) throws IOException {
        Golden golden = Golden.load(name);

        List<LoanScheduleCalculator.Installment> rows = LoanScheduleCalculator.schedule(golden.principal, golden.term, golden.terms,
                golden.disbursed);

        assertThat(rows).extracting(r -> r.number() + "," + r.dueDate() + "," + whole(r.principal()) + "," + whole(r.interest()))
                .containsExactlyElementsOf(golden.rows);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "flat-1-month", "flat-3-months-month-end", "flat-6-months-awkward-rate", "flat-12-months-whole-term-rate",
            "flat-at-maturity", "reducing-12-months", "reducing-6-months-awkward-rate", "reducing-3-months-interest-free"})
    void principalSumsExactlyAndEveryAmountIsWholeFrancs(String name) throws IOException {
        Golden golden = Golden.load(name);

        List<LoanScheduleCalculator.Installment> rows = LoanScheduleCalculator.schedule(golden.principal, golden.term, golden.terms,
                golden.disbursed);

        assertThat(rows.stream().map(LoanScheduleCalculator.Installment::principal).reduce(Money.ZERO, Money::plus))
                .isEqualTo(golden.principal);
        assertThat(rows).allSatisfy(r -> {
            assertThat(r.principal().isWholeRwf() && r.interest().isWholeRwf()).isTrue();
            assertThat(r.principal().isNegative() || r.interest().isNegative()).isFalse();
        });
    }

    @Test
    void manyAwkwardLoansNeverDrift() {
        String[] rates = {"0", "0.5", "1.25", "2.75", "3.3333", "7.125", "12.5"};
        for (String rate : rates) {
            for (int term = 1; term <= 24; term++) {
                for (long amount : new long[] {1_000, 9_999, 123_457, 1_000_001}) {
                    for (LoanTerms.InterestMethod method : LoanTerms.InterestMethod.values()) {
                        LoanTerms terms = terms(method, rate, LoanTerms.InterestPeriod.MONTH, LoanTerms.RepaymentFrequency.MONTHLY);
                        List<LoanScheduleCalculator.Installment> rows = LoanScheduleCalculator.schedule(Money.ofWholeRwf(amount), term,
                                terms, LocalDate.of(2026, 1, 31));
                        assertThat(rows).hasSize(term);
                        assertThat(rows.stream().map(LoanScheduleCalculator.Installment::principal).reduce(Money.ZERO, Money::plus))
                                .as("%s %s%% %d months %d", method, rate, term, amount).isEqualTo(Money.ofWholeRwf(amount));
                        assertThat(rows).allSatisfy(r -> assertThat(r.principal().isNegative() || r.interest().isNegative()).isFalse());
                    }
                }
            }
        }
    }

    @Test
    void combinationsWithoutADefinedMeaningAreRefused() {
        assertThat(LoanScheduleCalculator.supports(LoanTerms.InterestMethod.FLAT, LoanTerms.InterestPeriod.MONTH,
                LoanTerms.RepaymentFrequency.WEEKLY)).isFalse();
        assertThat(LoanScheduleCalculator.supports(LoanTerms.InterestMethod.REDUCING_BALANCE, LoanTerms.InterestPeriod.LOAN_TERM,
                LoanTerms.RepaymentFrequency.MONTHLY)).isFalse();
        assertThat(LoanScheduleCalculator.supports(LoanTerms.InterestMethod.REDUCING_BALANCE, LoanTerms.InterestPeriod.MONTH,
                LoanTerms.RepaymentFrequency.AT_MATURITY)).isFalse();
        assertThatThrownBy(() -> LoanScheduleCalculator.schedule(Money.ofWholeRwf(1000), 3,
                terms(LoanTerms.InterestMethod.FLAT, "1", LoanTerms.InterestPeriod.MONTH, LoanTerms.RepaymentFrequency.WEEKLY),
                LocalDate.of(2026, 1, 1))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> LoanScheduleCalculator.schedule(Money.parse("1000.50"), 3,
                terms(LoanTerms.InterestMethod.FLAT, "1", LoanTerms.InterestPeriod.MONTH, LoanTerms.RepaymentFrequency.MONTHLY),
                LocalDate.of(2026, 1, 1))).isInstanceOf(IllegalArgumentException.class);
    }

    private static LoanTerms terms(LoanTerms.InterestMethod method, String rate, LoanTerms.InterestPeriod period,
                                   LoanTerms.RepaymentFrequency frequency) {
        return new LoanTerms(method, new BigDecimal(rate), period, frequency, 0, LoanTerms.DEFAULT_ALLOCATION);
    }

    private static String whole(Money amount) {
        return amount.toBigDecimal().stripTrailingZeros().toPlainString();
    }

    private record Golden(LoanTerms terms, Money principal, int term, LocalDate disbursed, List<String> rows) {

        static Golden load(String name) throws IOException {
            try (InputStream in = LoanScheduleCalculatorTest.class.getResourceAsStream("/loans/golden/" + name + ".csv")) {
                assertThat(in).as(name).isNotNull();
                List<String> lines = new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().map(String::strip)
                        .filter(l -> !l.isEmpty()).toList();
                Map<String, String> params = new HashMap<>();
                lines.stream().filter(l -> l.startsWith("# method=")).findFirst().orElseThrow()
                        .substring(2).lines().flatMap(l -> List.of(l.split(" ")).stream())
                        .forEach(pair -> params.put(pair.substring(0, pair.indexOf('=')), pair.substring(pair.indexOf('=') + 1)));
                LoanTerms terms = LoanScheduleCalculatorTest.terms(LoanTerms.InterestMethod.valueOf(params.get("method")), params.get("rate"),
                        LoanTerms.InterestPeriod.valueOf(params.get("period")), LoanTerms.RepaymentFrequency.valueOf(params.get("frequency")));
                return new Golden(terms, Money.parse(params.get("principal")), Integer.parseInt(params.get("term")),
                        LocalDate.parse(params.get("disbursed")), lines.stream().filter(l -> !l.startsWith("#")).toList());
            }
        }
    }
}
