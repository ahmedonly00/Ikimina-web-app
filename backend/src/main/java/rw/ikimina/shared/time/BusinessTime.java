package rw.ikimina.shared.time;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Time conventions (spec 4.3, 20.2).
 *
 * <p>Instants are stored as {@code TIMESTAMPTZ} in UTC. Business dates - due dates,
 * meeting dates, contribution periods - are calendar dates in Africa/Kigali
 * (UTC+2, no daylight saving). Code never reads the wall clock directly; it asks
 * the injected {@link Clock}, so tests can pin time to month ends and overdue edges.
 */
public final class BusinessTime {

    public static final ZoneId ZONE = ZoneId.of("Africa/Kigali");

    private BusinessTime() {
    }

    /** Today's business date in Kigali, whatever zone the clock itself carries. */
    public static LocalDate today(Clock clock) {
        return LocalDate.ofInstant(clock.instant(), ZONE);
    }

    /** The Kigali business date on which {@code instant} falls. */
    public static LocalDate dateOf(Instant instant) {
        return LocalDate.ofInstant(instant, ZONE);
    }

    /** The first instant of {@code date} in Kigali, e.g. for "up to and including" report bounds. */
    public static Instant startOf(LocalDate date) {
        return date.atStartOfDay(ZONE).toInstant();
    }
}
