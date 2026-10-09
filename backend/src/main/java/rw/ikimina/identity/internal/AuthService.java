package rw.ikimina.identity.internal;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.ikimina.audit.AuditEvent;
import rw.ikimina.audit.AuditService;
import rw.ikimina.identity.internal.SessionTokens.IssuedSession;
import rw.ikimina.notifications.SmsNotifier;
import rw.ikimina.shared.error.ApiException;
import rw.ikimina.shared.error.ErrorCode;
import rw.ikimina.shared.phone.PhoneNumber;
import rw.ikimina.shared.ratelimit.RateLimiter;
import rw.ikimina.shared.ratelimit.RateLimiter.Limit;
import rw.ikimina.shared.security.AuthProperties;
import rw.ikimina.shared.security.CurrentUser;
import rw.ikimina.shared.web.RequestContext;
import tools.jackson.databind.json.JsonMapper;

/**
 * Registration, sign-in, refresh, sign-out, password reset and step-up (spec 16.1, 17.1).
 *
 * <p>Methods whose failure must still be recorded - a wrong password increments the
 * lockout counter and is audited - return an outcome instead of throwing, so the
 * transaction commits and the controller then reports the failure.
 *
 * <p>Responses never reveal whether a phone number has an account (spec 16.1): unknown
 * numbers, wrong passwords and locked or disabled accounts all fail the same way, and
 * the password is hashed or checked on every path so timing does not differ either.
 */
@Service
class AuthService {

    // Spec 16.5: strict on auth, very strict on sending codes. Windows are fixed, per instance-independent counters.
    static final Limit REGISTER_PER_IP = new Limit("register-ip", 10, Duration.ofHours(1));
    static final Limit OTP_SEND_PER_PHONE = new Limit("otp-send-phone", 3, Duration.ofMinutes(15));
    static final Limit OTP_SEND_PER_IP = new Limit("otp-send-ip", 10, Duration.ofMinutes(15));
    static final Limit OTP_VERIFY_PER_PHONE = new Limit("otp-verify-phone", 10, Duration.ofMinutes(15));
    static final Limit OTP_VERIFY_PER_IP = new Limit("otp-verify-ip", 30, Duration.ofMinutes(15));
    static final Limit LOGIN_PER_PHONE = new Limit("login-phone", 10, Duration.ofMinutes(15));
    static final Limit LOGIN_PER_IP = new Limit("login-ip", 30, Duration.ofMinutes(15));
    static final Limit REFRESH_PER_IP = new Limit("refresh-ip", 120, Duration.ofMinutes(15));
    static final Limit REAUTH_PER_USER = new Limit("reauth-user", 10, Duration.ofMinutes(15));

    sealed interface Outcome permits Succeeded, Failed {
    }

    record Succeeded(IssuedSession session) implements Outcome {
    }

    record Failed(ErrorCode reason) implements Outcome {
    }

    /** What waits in the OTP challenge until the phone is proven. */
    record PendingRegistration(String fullName, String locale, String passwordHash) {
    }

    private final UserRepository users;
    private final OtpService otps;
    private final SessionTokens tokens;
    private final PasswordPolicy passwordPolicy;
    private final PasswordEncoder passwordEncoder;
    private final RateLimiter rateLimiter;
    private final SmsNotifier sms;
    private final AuditService audit;
    private final AuthProperties properties;
    private final JsonMapper json;
    private final Clock clock;
    /** Checked against when the phone is unknown, so that path costs the same as a real check. */
    private final String timingDecoyHash;

    AuthService(UserRepository users, OtpService otps, SessionTokens tokens, PasswordPolicy passwordPolicy,
                PasswordEncoder passwordEncoder, RateLimiter rateLimiter, SmsNotifier sms, AuditService audit,
                AuthProperties properties, JsonMapper json, Clock clock) {
        this.users = users;
        this.otps = otps;
        this.tokens = tokens;
        this.passwordPolicy = passwordPolicy;
        this.passwordEncoder = passwordEncoder;
        this.rateLimiter = rateLimiter;
        this.sms = sms;
        this.audit = audit;
        this.properties = properties;
        this.json = json;
        this.clock = clock;
        this.timingDecoyHash = passwordEncoder.encode("timing-decoy-not-a-password");
    }

    // --- registration ----------------------------------------------------------------

    /**
     * Starts registration: sends a code to the phone. The account is created only when the
     * code comes back ({@link #verifyPhone}), so nobody can pre-register someone else's
     * number. For a number that already has an account nothing is sent, and the response is
     * the same.
     */
    @Transactional
    void register(String rawPhone, String fullName, String password, String locale, boolean acceptTerms) {
        rateLimiter.consume(REGISTER_PER_IP, clientIp());
        PhoneNumber phone = PhoneNumber.parse(rawPhone);
        if (!acceptTerms) {
            throw new ApiException(ErrorCode.TERMS_NOT_ACCEPTED);
        }
        passwordPolicy.check(password, phone);
        rateLimiter.consume(OTP_SEND_PER_IP, clientIp());
        rateLimiter.consume(OTP_SEND_PER_PHONE, phone.e164());
        // Hash before looking the number up: both branches then take the same time.
        String passwordHash = passwordEncoder.encode(password);
        if (users.findByPhone(phone.e164()).isPresent()) {
            return;
        }
        String payload = json.writeValueAsString(new PendingRegistration(fullName.trim(), locale, passwordHash));
        String code = otps.issue(phone, OtpChallenge.Purpose.REGISTRATION, payload);
        sms.send(phone, Locale.of(locale), "otp_code", code);
    }

    @Transactional
    Outcome verifyPhone(String rawPhone, String code) {
        PhoneNumber phone = PhoneNumber.parse(rawPhone);
        rateLimiter.consume(OTP_VERIFY_PER_IP, clientIp());
        rateLimiter.consume(OTP_VERIFY_PER_PHONE, phone.e164());
        OtpService.Verification verification = otps.verify(phone, OtpChallenge.Purpose.REGISTRATION, code);
        if (verification instanceof OtpService.Rejected(ErrorCode reason)) {
            return new Failed(reason);
        }
        if (users.findByPhone(phone.e164()).isPresent()) {
            return new Failed(ErrorCode.OTP_INVALID);   // registered meanwhile through another code
        }
        PendingRegistration pending = json.readValue(((OtpService.Verified) verification).payload(), PendingRegistration.class);
        Instant now = clock.instant();
        User user = users.saveAndFlush(User.verifiedRegistration(phone, pending.fullName(), pending.locale(),
                pending.passwordHash(), properties.termsVersion(), now));
        audit.record(AuditEvent.of("USER_REGISTERED")
                .entity("user", user.getPublicId())
                .after(Map.of("phone", phone.masked(), "locale", user.getLocale(), "termsVersion", properties.termsVersion()))
                .actor(user.getId()));
        return new Succeeded(tokens.startSession(user));
    }

    // --- sign-in -----------------------------------------------------------------------

    @Transactional(noRollbackFor = ApiException.class)
    Outcome login(String rawPhone, String password) {
        rateLimiter.consume(LOGIN_PER_IP, clientIp());
        PhoneNumber phone;
        try {
            phone = PhoneNumber.parse(rawPhone);
        } catch (ApiException malformed) {
            passwordEncoder.matches(password, timingDecoyHash);
            return new Failed(ErrorCode.INVALID_CREDENTIALS);
        }
        rateLimiter.consume(LOGIN_PER_PHONE, phone.e164());
        Instant now = clock.instant();
        Optional<User> found = users.findByPhoneForUpdate(phone.e164());
        if (found.isEmpty()) {
            passwordEncoder.matches(password, timingDecoyHash);
            audit.record(AuditEvent.of("LOGIN_FAILED").entity("phone", phone.masked()).reason("unknown phone"));
            return new Failed(ErrorCode.INVALID_CREDENTIALS);
        }
        User user = found.get();
        boolean passwordMatches = password != null && passwordEncoder.matches(password, user.getPasswordHash());
        if (!user.isActive() || user.isLocked(now)) {
            audit.record(AuditEvent.of("LOGIN_FAILED").entity("user", user.getPublicId())
                    .reason(user.isActive() ? "account locked" : "account disabled").actor(user.getId()));
            return new Failed(ErrorCode.INVALID_CREDENTIALS);
        }
        if (!passwordMatches) {
            user.recordFailedLogin(now);
            audit.record(AuditEvent.of("LOGIN_FAILED").entity("user", user.getPublicId())
                    .after(Map.of("failedLogins", user.getFailedLogins()))
                    .reason("wrong password").actor(user.getId()));
            return new Failed(ErrorCode.INVALID_CREDENTIALS);
        }
        user.recordSuccessfulLogin();
        audit.record(AuditEvent.of("LOGIN_SUCCEEDED").entity("user", user.getPublicId()).actor(user.getId()));
        return new Succeeded(tokens.startSession(user));
    }

    @Transactional
    Outcome refresh(String rawRefreshToken) {
        rateLimiter.consume(REFRESH_PER_IP, clientIp());
        SessionTokens.Rotation rotation = tokens.rotate(rawRefreshToken);
        return switch (rotation) {
            case SessionTokens.Rotated rotated -> new Succeeded(rotated.session());
            case SessionTokens.ReuseDetected reuse -> {
                audit.record(AuditEvent.of("REFRESH_TOKEN_REUSE_DETECTED").entity("user", reuse.userId())
                        .reason("a rotated refresh token was presented again; all sessions of that sign-in revoked")
                        .actor(reuse.userId()));
                yield new Failed(ErrorCode.INVALID_REFRESH_TOKEN);
            }
            case SessionTokens.Rejected rejected -> new Failed(ErrorCode.INVALID_REFRESH_TOKEN);
        };
    }

    @Transactional
    void logout(String rawRefreshToken) {
        tokens.revokeFamilyOf(rawRefreshToken).ifPresent(userId ->
                audit.record(AuditEvent.of("LOGOUT").entity("user", userId).actor(userId)));
    }

    // --- password reset --------------------------------------------------------------

    /** Always looks the same to the caller; a code is sent only if the number has an account. */
    @Transactional
    void forgotPassword(String rawPhone) {
        PhoneNumber phone = PhoneNumber.parse(rawPhone);
        rateLimiter.consume(OTP_SEND_PER_IP, clientIp());
        rateLimiter.consume(OTP_SEND_PER_PHONE, phone.e164());
        users.findByPhone(phone.e164()).filter(User::isActive).ifPresent(user -> {
            String code = otps.issue(phone, OtpChallenge.Purpose.PASSWORD_RESET, null);
            sms.send(phone, Locale.of(user.getLocale()), "otp_code", code);
        });
    }

    /** Sets a new password, then signs out every session of the account. */
    @Transactional
    Optional<ErrorCode> resetPassword(String rawPhone, String code, String newPassword) {
        PhoneNumber phone = PhoneNumber.parse(rawPhone);
        rateLimiter.consume(OTP_VERIFY_PER_IP, clientIp());
        rateLimiter.consume(OTP_VERIFY_PER_PHONE, phone.e164());
        passwordPolicy.check(newPassword, phone);
        OtpService.Verification verification = otps.verify(phone, OtpChallenge.Purpose.PASSWORD_RESET, code);
        if (verification instanceof OtpService.Rejected(ErrorCode reason)) {
            return Optional.of(reason);
        }
        Optional<User> found = users.findByPhoneForUpdate(phone.e164()).filter(User::isActive);
        if (found.isEmpty()) {
            return Optional.of(ErrorCode.OTP_INVALID);
        }
        User user = found.get();
        user.changePassword(passwordEncoder.encode(newPassword), clock.instant());
        tokens.revokeAllFor(user.getId());
        audit.record(AuditEvent.of("PASSWORD_RESET").entity("user", user.getPublicId())
                .reason("all sessions revoked").actor(user.getId()));
        return Optional.empty();
    }

    // --- step-up -----------------------------------------------------------------------

    /** Re-entering the password refreshes auth_time for sensitive actions (spec 16.1). */
    @Transactional(noRollbackFor = ApiException.class)
    Optional<String> reauthenticate(String password) {
        long userId = CurrentUser.require().id();
        rateLimiter.consume(REAUTH_PER_USER, Long.toString(userId));
        Instant now = clock.instant();
        User user = users.findByIdForUpdate(userId).orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED));
        boolean matches = password != null && passwordEncoder.matches(password, user.getPasswordHash());
        if (!user.isActive() || user.isLocked(now) || !matches) {
            if (user.isActive() && !user.isLocked(now)) {
                user.recordFailedLogin(now);
            }
            audit.record(AuditEvent.of("REAUTHENTICATION_FAILED").entity("user", user.getPublicId()));
            return Optional.empty();
        }
        user.recordSuccessfulLogin();
        audit.record(AuditEvent.of("REAUTHENTICATED").entity("user", user.getPublicId()));
        return Optional.of(tokens.reauthenticatedAccessToken(user));
    }

    long accessTokenTtlSeconds() {
        return tokens.accessTokenTtlSeconds();
    }

    private static String clientIp() {
        return RequestContext.current().map(RequestContext::clientIp).orElse("unknown");
    }
}
