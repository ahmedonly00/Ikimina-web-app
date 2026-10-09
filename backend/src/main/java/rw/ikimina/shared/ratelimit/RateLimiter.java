package rw.ikimina.shared.ratelimit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Fixed-window rate limiting shared by every application instance through PostgreSQL
 * (spec 16.5), so it holds with more than one instance and needs no Redis.
 *
 * <p>Each hit is counted in its own transaction: a request that then fails and rolls back
 * still counts, which is the point - failed logins and wrong OTPs are what must be limited.
 * Subjects (phone numbers, IPs) are hashed before storage.
 */
@Component
public class RateLimiter {

    /** A named limit: at most {@code maxHits} per {@code window} for one subject. */
    public record Limit(String name, int maxHits, Duration window) {
        public Limit {
            if (maxHits < 1 || window.isNegative() || window.isZero()) {
                throw new IllegalArgumentException("Invalid rate limit " + name);
            }
        }
    }

    private static final Logger log = LoggerFactory.getLogger(RateLimiter.class);

    private static final String HIT = """
            INSERT INTO rate_limit_counters (bucket_key, window_start, hits, expires_at)
            VALUES (?, ?, 1, ?)
            ON CONFLICT (bucket_key, window_start) DO UPDATE SET hits = rate_limit_counters.hits + 1
            RETURNING hits""";

    private final JdbcTemplate jdbc;
    private final TransactionTemplate ownTransaction;
    private final Clock clock;

    public RateLimiter(JdbcTemplate jdbc, PlatformTransactionManager transactionManager, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.ownTransaction = new TransactionTemplate(transactionManager);
        this.ownTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Counts one hit against {@code limit} for {@code subject}.
     *
     * @throws RateLimitedException if this hit exceeds the limit
     */
    public void consume(Limit limit, String subject) {
        Instant now = clock.instant();
        long windowSeconds = limit.window().toSeconds();
        long startSecond = Math.floorDiv(now.getEpochSecond(), windowSeconds) * windowSeconds;
        Instant windowStart = Instant.ofEpochSecond(startSecond);
        Instant windowEnd = windowStart.plusSeconds(windowSeconds);
        String bucket = bucketKey(limit.name(), subject == null ? "" : subject);

        Integer hits = ownTransaction.execute(status -> jdbc.queryForObject(HIT, Integer.class,
                bucket, utc(windowStart), utc(windowEnd)));
        if (hits != null && hits > limit.maxHits()) {
            log.info("Rate limit {} exceeded", limit.name());
            throw new RateLimitedException(Duration.between(now, windowEnd).toSeconds());
        }
    }

    @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT5M")
    @SchedulerLock(name = "rate-limit-purge", lockAtMostFor = "PT10M")
    public void purgeExpired() {
        Integer removed = ownTransaction.execute(status ->
                jdbc.update("DELETE FROM rate_limit_counters WHERE expires_at < ?", utc(clock.instant())));
        log.debug("Purged {} expired rate-limit windows", removed);
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    private static String bucketKey(String limitName, String subject) {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            sha256.update(limitName.getBytes(StandardCharsets.UTF_8));
            sha256.update((byte) 0);
            sha256.update(subject.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(sha256.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
