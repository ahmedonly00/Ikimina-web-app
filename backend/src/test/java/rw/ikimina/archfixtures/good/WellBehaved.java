package rw.ikimina.archfixtures.good;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;

public class WellBehaved {

    private final Clock clock;

    public WellBehaved(Clock clock) {
        this.clock = clock;
    }

    public LocalDate dueDate() {
        return LocalDate.now(clock);
    }

    public Instant recordedAt() {
        return Instant.now(clock);
    }

    public BigDecimal tenCents() {
        return new BigDecimal("0.10");
    }

    public BigDecimal fromWholeFrancs(long francs) {
        return BigDecimal.valueOf(francs);
    }
}
