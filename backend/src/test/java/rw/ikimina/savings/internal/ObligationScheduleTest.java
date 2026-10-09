package rw.ikimina.savings.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import rw.ikimina.savings.internal.ObligationSchedule.Period;

/** Owner decision, Phase 2: periods anchored at the bucket start date, due on their last day. */
class ObligationScheduleTest {

    private static List<Period> periods(String anchor, SavingsBucket.Frequency frequency, String end, String today, String notBefore) {
        return ObligationSchedule.periodsDue(LocalDate.parse(anchor), frequency, end == null ? null : LocalDate.parse(end),
                LocalDate.parse(today), LocalDate.parse(notBefore));
    }

    @Test
    void monthlyPeriodsAreDueOnTheirLastDay() {
        assertThat(periods("2026-01-01", SavingsBucket.Frequency.MONTHLY, null, "2026-03-10", "2026-01-01")).containsExactly(
                new Period(LocalDate.parse("2026-01-01"), LocalDate.parse("2026-01-31")),
                new Period(LocalDate.parse("2026-02-01"), LocalDate.parse("2026-02-28")),
                new Period(LocalDate.parse("2026-03-01"), LocalDate.parse("2026-03-31")));
    }

    @Test
    void monthlyPeriodsFromThe31stDoNotDrift() {
        assertThat(periods("2026-01-31", SavingsBucket.Frequency.MONTHLY, null, "2026-04-01", "2026-01-31"))
                .extracting(Period::start)
                .containsExactly(LocalDate.parse("2026-01-31"), LocalDate.parse("2026-02-28"), LocalDate.parse("2026-03-31"));
    }

    @Test
    void leapYearFebruaryIsHandled() {
        assertThat(periods("2028-01-29", SavingsBucket.Frequency.MONTHLY, null, "2028-02-29", "2028-01-29"))
                .extracting(Period::due)
                .containsExactly(LocalDate.parse("2028-02-28"), LocalDate.parse("2028-03-28"));
    }

    @Test
    void weeklyAndBiweeklyPeriods() {
        assertThat(periods("2026-03-02", SavingsBucket.Frequency.WEEKLY, null, "2026-03-16", "2026-03-02"))
                .extracting(Period::due)
                .containsExactly(LocalDate.parse("2026-03-08"), LocalDate.parse("2026-03-15"), LocalDate.parse("2026-03-22"));
        assertThat(periods("2026-03-02", SavingsBucket.Frequency.BIWEEKLY, null, "2026-03-16", "2026-03-02"))
                .extracting(Period::due)
                .containsExactly(LocalDate.parse("2026-03-15"), LocalDate.parse("2026-03-29"));
    }

    @Test
    void periodsThatEndedBeforeTheMemberJoinedAreNotCharged() {
        assertThat(periods("2026-01-01", SavingsBucket.Frequency.MONTHLY, null, "2026-03-10", "2026-02-15"))
                .extracting(Period::start)
                .containsExactly(LocalDate.parse("2026-02-01"), LocalDate.parse("2026-03-01"));
    }

    @Test
    void aFixedTermStopsAtItsEndDateAndFuturePeriodsWait() {
        assertThat(periods("2026-01-01", SavingsBucket.Frequency.MONTHLY, "2026-02-15", "2026-06-01", "2026-01-01"))
                .extracting(Period::start)
                .containsExactly(LocalDate.parse("2026-01-01"), LocalDate.parse("2026-02-01"));
        assertThat(periods("2026-05-01", SavingsBucket.Frequency.MONTHLY, null, "2026-04-30", "2026-04-30")).isEmpty();
    }

    @Test
    void meetingAndAdHocBucketsHaveNoCalendar() {
        assertThatThrownBy(() -> ObligationSchedule.advance(LocalDate.now(), SavingsBucket.Frequency.ADHOC, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
