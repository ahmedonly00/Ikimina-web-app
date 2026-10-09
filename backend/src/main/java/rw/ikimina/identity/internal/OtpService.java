package rw.ikimina.identity.internal;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import rw.ikimina.shared.error.ErrorCode;
import rw.ikimina.shared.phone.PhoneNumber;
import rw.ikimina.shared.security.AuthProperties;

/**
 * One-time codes (spec 16.1): six digits, five minutes, at most five attempts. A new code
 * for the same phone and purpose supersedes the previous one. Codes are stored only as an
 * HMAC keyed with a server secret, so a database copy alone cannot recover them.
 */
@Service
class OtpService {

    static final Duration VALIDITY = Duration.ofMinutes(5);
    private static final Duration RETENTION_AFTER_EXPIRY = Duration.ofDays(1);

    sealed interface Verification permits Verified, Rejected {
    }

    /** @param payload what was stored with the challenge (a pending registration), may be null */
    record Verified(String payload) implements Verification {
    }

    record Rejected(ErrorCode reason) implements Verification {
    }

    private final OtpChallengeRepository challenges;
    private final Clock clock;
    private final SecretKeySpec pepper;
    private final SecureRandom random = new SecureRandom();
    private final TransactionTemplate ownTransaction;

    OtpService(OtpChallengeRepository challenges, Clock clock, AuthProperties properties,
               PlatformTransactionManager transactionManager) {
        this.challenges = challenges;
        this.clock = clock;
        this.pepper = new SecretKeySpec(properties.otpPepper().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        this.ownTransaction = new TransactionTemplate(transactionManager);
        this.ownTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Creates a new code (superseding any open one) and returns it, to be sent by SMS. */
    @Transactional(propagation = Propagation.MANDATORY)
    String issue(PhoneNumber phone, OtpChallenge.Purpose purpose, String payload) {
        Instant now = clock.instant();
        challenges.supersedeOpen(phone.e164(), purpose, now);
        String code = "%06d".formatted(random.nextInt(1_000_000));
        challenges.save(new OtpChallenge(phone.e164(), purpose, hash(phone, purpose, code), payload, now.plus(VALIDITY), now));
        return code;
    }

    /**
     * Checks a code. Runs in its own transaction so a wrong guess is counted even though
     * the caller's request then fails.
     */
    Verification verify(PhoneNumber phone, OtpChallenge.Purpose purpose, String code) {
        return ownTransaction.execute(status -> {
            Instant now = clock.instant();
            Optional<OtpChallenge> open = challenges
                    .findFirstByPhoneAndPurposeAndConsumedAtIsNullAndSupersededAtIsNullOrderByIdDesc(phone.e164(), purpose);
            if (open.isEmpty() || open.get().attemptsExhausted()) {
                return new Rejected(ErrorCode.OTP_INVALID);
            }
            OtpChallenge challenge = open.get();
            if (challenge.isExpired(now)) {
                return new Rejected(ErrorCode.OTP_EXPIRED);
            }
            boolean wellFormed = code != null && code.matches("[0-9]{6}");
            if (!wellFormed || !constantTimeEquals(challenge.getCodeHash(), hash(phone, purpose, code))) {
                challenge.recordFailedAttempt(now);
                return new Rejected(ErrorCode.OTP_INVALID);
            }
            challenge.consume(now);
            return new Verified(challenge.getPayload());
        });
    }

    /** Expired challenges may hold a pending password hash: keep them no longer than needed. */
    @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT10M")
    @SchedulerLock(name = "otp-challenge-purge", lockAtMostFor = "PT10M")
    void purgeExpired() {
        ownTransaction.executeWithoutResult(status ->
                challenges.deleteExpiredBefore(clock.instant().minus(RETENTION_AFTER_EXPIRY)));
    }

    private String hash(PhoneNumber phone, OtpChallenge.Purpose purpose, String code) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(pepper);
            mac.update((purpose.name() + ':' + phone.e164() + ':' + code).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(mac.doFinal());
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.US_ASCII), b.getBytes(StandardCharsets.US_ASCII));
    }
}
