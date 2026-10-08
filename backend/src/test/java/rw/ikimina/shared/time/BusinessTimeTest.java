package rw.ikimina.shared.time;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

class BusinessTimeTest {

    @ParameterizedTest(name = "{0} UTC is {1} in Kigali")
    @CsvSource({
            // Kigali is UTC+2 all year: the business day starts at 22:00 UTC the evening before.
            "2026-01-31T21:59:59Z, 2026-01-31",
            "2026-01-31T22:00:00Z, 2026-02-01",
            "2026-12-31T22:00:00Z, 2027-01-01",
            "2028-02-28T22:00:00Z, 2028-02-29",
            // No daylight saving: the offset is the same in July.
            "2026-07-15T21:59:59Z, 2026-07-15",
            "2026-07-15T22:00:00Z, 2026-07-16",
    })
    void businessDateIsTheKigaliCalendarDate(String utcInstant, String kigaliDate) {
        Clock clock = Clock.fixed(Instant.parse(utcInstant), ZoneOffset.UTC);
        assertThat(BusinessTime.today(clock)).isEqualTo(LocalDate.parse(kigaliDate));
        assertThat(BusinessTime.dateOf(Instant.parse(utcInstant))).isEqualTo(LocalDate.parse(kigaliDate));
    }

    @Test
    void businessDayStartsAtKigaliMidnight() {
        assertThat(BusinessTime.startOf(LocalDate.parse("2026-02-01"))).isEqualTo(Instant.parse("2026-01-31T22:00:00Z"));
    }

    @Test
    void applicationClockRunsInKigali() {
        assertThat(new ClockConfig().clock().getZone()).isEqualTo(BusinessTime.ZONE);
    }
}
