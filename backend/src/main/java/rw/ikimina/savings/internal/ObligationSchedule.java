package rw.ikimina.savings.internal;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Contribution periods of a bucket (owner decision, Phase 2): anchored at the bucket's start
 * date, each due on its last day. Monthly periods are computed from the anchor every time
 * (start + k months), so a bucket starting on the 31st gives the 28th/29th in February and the
 * 31st again in March, without drifting.
 */
final class ObligationSchedule {

    record Period(LocalDate start, LocalDate due) {
    }

    private ObligationSchedule() {
    }

    /**
     * Periods that have started by {@code today} and are still relevant to someone who joined on
     * {@code notBefore}: a period that ended before the bucket existed or the member joined is
     * never charged (history is imported separately, spec 3.2 V1.1).
     */
    static List<Period> periodsDue(LocalDate anchor, SavingsBucket.Frequency frequency, LocalDate endDate,
                                   LocalDate today, LocalDate notBefore) {
        List<Period> periods = new ArrayList<>();
        for (int k = 0; ; k++) {
            LocalDate start = advance(anchor, frequency, k);
            if (start.isAfter(today) || (endDate != null && start.isAfter(endDate))) {
                return periods;
            }
            LocalDate due = advance(anchor, frequency, k + 1).minusDays(1);
            if (!due.isBefore(notBefore)) {
                periods.add(new Period(start, due));
            }
        }
    }

    static LocalDate advance(LocalDate anchor, SavingsBucket.Frequency frequency, int periods) {
        return switch (frequency) {
            case WEEKLY -> anchor.plusWeeks(periods);
            case BIWEEKLY -> anchor.plusWeeks(2L * periods);
            case MONTHLY -> anchor.plusMonths(periods);
            case PER_MEETING, ADHOC -> throw new IllegalArgumentException(frequency + " has no calendar schedule");
        };
    }
}
