package rw.ikimina.archfixtures.bad;

import java.time.Instant;
import java.time.LocalDate;

public class ReadsWallClock {

    public LocalDate dueDate() {
        return LocalDate.now();
    }

    public Instant recordedAt() {
        return Instant.now();
    }
}
