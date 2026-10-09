package rw.ikimina.identity.internal;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import rw.ikimina.shared.phone.PhoneNumber;

@Entity
@Table(name = "users")
public class User {

    static final String ROLE_USER = "USER";
    static final String ROLE_PLATFORM_ADMIN = "PLATFORM_ADMIN";
    static final String STATUS_ACTIVE = "ACTIVE";

    /** Failed sign-ins before the account starts locking (spec 16.1: progressive delay). */
    static final int LOCKOUT_THRESHOLD = 5;
    static final Duration MAX_LOCKOUT = Duration.ofMinutes(60);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, updatable = false)
    private UUID publicId;

    @Column(nullable = false, updatable = false)
    private String phone;

    @Column(name = "phone_verified_at")
    private Instant phoneVerifiedAt;

    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(nullable = false)
    private String locale;

    @Column(name = "platform_role", nullable = false)
    private String platformRole;

    @Column(nullable = false)
    private String status;

    @Column(name = "failed_logins", nullable = false)
    private int failedLogins;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "credentials_changed_at", nullable = false)
    private Instant credentialsChangedAt;

    @Column(name = "terms_accepted_at", nullable = false, updatable = false)
    private Instant termsAcceptedAt;

    @Column(name = "terms_version", nullable = false, updatable = false)
    private String termsVersion;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Version
    private long version;

    protected User() {
    }

    /** A person whose phone number has just been proven by a one-time code. */
    static User verifiedRegistration(PhoneNumber phone, String fullName, String locale, String passwordHash,
                                     String termsVersion, Instant now) {
        User user = new User();
        user.publicId = UUID.randomUUID();
        user.phone = phone.e164();
        user.phoneVerifiedAt = now;
        user.passwordHash = passwordHash;
        user.fullName = fullName;
        user.locale = locale;
        user.platformRole = ROLE_USER;
        user.status = STATUS_ACTIVE;
        user.credentialsChangedAt = now;
        user.termsAcceptedAt = now;
        user.termsVersion = termsVersion;
        user.createdAt = now;
        return user;
    }

    static User platformAdmin(PhoneNumber phone, String fullName, String passwordHash, String termsVersion, Instant now) {
        User user = verifiedRegistration(phone, fullName, "en", passwordHash, termsVersion, now);
        user.platformRole = ROLE_PLATFORM_ADMIN;
        return user;
    }

    boolean isActive() {
        return STATUS_ACTIVE.equals(status);
    }

    boolean isLocked(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    /** Each failure past the threshold doubles the lock: 1, 2, 4 ... minutes, capped at an hour. */
    void recordFailedLogin(Instant now) {
        failedLogins++;
        if (failedLogins >= LOCKOUT_THRESHOLD) {
            int doublings = Math.min(failedLogins - LOCKOUT_THRESHOLD, 6);
            Duration lock = Duration.ofMinutes(1L << doublings);
            lockedUntil = now.plus(lock.compareTo(MAX_LOCKOUT) > 0 ? MAX_LOCKOUT : lock);
        }
    }

    void recordSuccessfulLogin() {
        failedLogins = 0;
        lockedUntil = null;
    }

    void changePassword(String newPasswordHash, Instant now) {
        passwordHash = newPasswordHash;
        credentialsChangedAt = now;
        failedLogins = 0;
        lockedUntil = null;
    }

    void updateProfile(String newFullName, String newLocale) {
        if (newFullName != null) {
            fullName = newFullName;
        }
        if (newLocale != null) {
            locale = newLocale;
        }
    }

    /**
     * Tokens issued before the credentials last changed are no longer honoured. JWT
     * {@code iat} has whole-second precision, so the comparison is at whole seconds.
     */
    boolean acceptsTokenIssuedAt(Instant issuedAt) {
        return issuedAt != null && !issuedAt.isBefore(credentialsChangedAt.truncatedTo(ChronoUnit.SECONDS));
    }

    Long getId() {
        return id;
    }

    UUID getPublicId() {
        return publicId;
    }

    PhoneNumber getPhone() {
        return new PhoneNumber(phone);
    }

    Instant getPhoneVerifiedAt() {
        return phoneVerifiedAt;
    }

    String getEmail() {
        return email;
    }

    String getPasswordHash() {
        return passwordHash;
    }

    String getFullName() {
        return fullName;
    }

    String getLocale() {
        return locale;
    }

    String getPlatformRole() {
        return platformRole;
    }

    int getFailedLogins() {
        return failedLogins;
    }

    Instant getLockedUntil() {
        return lockedUntil;
    }
}
