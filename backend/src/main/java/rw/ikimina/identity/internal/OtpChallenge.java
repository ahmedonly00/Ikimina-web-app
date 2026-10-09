package rw.ikimina.identity.internal;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A one-time code sent by SMS. Only a keyed hash of the code is stored. */
@Entity
@Table(name = "otp_challenges")
public class OtpChallenge {

    enum Purpose { REGISTRATION, PASSWORD_RESET }

    /** Spec 16.1: at most 5 attempts per code. */
    static final int MAX_ATTEMPTS = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private Purpose purpose;

    @Column(name = "code_hash", nullable = false, updatable = false)
    private String codeHash;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(updatable = false)
    private String payload;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "superseded_at")
    private Instant supersededAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected OtpChallenge() {
    }

    OtpChallenge(String phone, Purpose purpose, String codeHash, String payload, Instant expiresAt, Instant now) {
        this.phone = phone;
        this.purpose = purpose;
        this.codeHash = codeHash;
        this.payload = payload;
        this.expiresAt = expiresAt;
        this.createdAt = now;
    }

    boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }

    boolean attemptsExhausted() {
        return attempts >= MAX_ATTEMPTS;
    }

    /** A wrong guess. After the last allowed attempt the code is dead even if the next guess is right. */
    void recordFailedAttempt(Instant now) {
        attempts++;
        if (attempts >= MAX_ATTEMPTS) {
            supersededAt = now;
        }
    }

    void consume(Instant now) {
        consumedAt = now;
    }

    String getCodeHash() {
        return codeHash;
    }

    String getPayload() {
        return payload;
    }
}
