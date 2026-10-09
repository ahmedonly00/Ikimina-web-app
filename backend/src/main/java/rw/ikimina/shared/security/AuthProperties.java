package rw.ikimina.shared.security;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Authentication settings (spec 16.1). The two secrets come from the environment
 * (Hard Rule H6) and must each be at least 32 bytes; the application refuses to start otherwise.
 *
 * @param jwtSecret          HMAC key signing access tokens
 * @param otpPepper          HMAC key for one-time codes, so a database dump cannot brute-force the 10^6 codes
 * @param issuer             JWT {@code iss} and {@code aud}
 * @param accessTokenTtl     15 minutes per spec
 * @param refreshTokenTtl    30 days per spec
 * @param recentAuthMaxAge   how recently the password must have been entered for step-up actions
 * @param refreshCookieSecure false only for plain-http local development
 * @param termsVersion       version of the terms/privacy text a new user accepts (spec 16.8)
 */
@ConfigurationProperties("ikimina.auth")
public record AuthProperties(
        String jwtSecret,
        String otpPepper,
        String issuer,
        Duration accessTokenTtl,
        Duration refreshTokenTtl,
        Duration recentAuthMaxAge,
        boolean refreshCookieSecure,
        String termsVersion) {

    private static final int MIN_SECRET_BYTES = 32;

    public AuthProperties {
        requireSecret("ikimina.auth.jwt-secret (IKIMINA_JWT_SECRET)", jwtSecret);
        requireSecret("ikimina.auth.otp-pepper (IKIMINA_OTP_PEPPER)", otpPepper);
        Objects.requireNonNull(issuer, "ikimina.auth.issuer");
        Objects.requireNonNull(accessTokenTtl, "ikimina.auth.access-token-ttl");
        Objects.requireNonNull(refreshTokenTtl, "ikimina.auth.refresh-token-ttl");
        Objects.requireNonNull(recentAuthMaxAge, "ikimina.auth.recent-auth-max-age");
        Objects.requireNonNull(termsVersion, "ikimina.auth.terms-version");
    }

    private static void requireSecret(String name, String value) {
        if (value == null || value.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(name + " must be set to at least " + MIN_SECRET_BYTES
                    + " bytes of random data (e.g. openssl rand -hex 32)");
        }
    }

    /** Never print secrets, even in a startup failure report. */
    @Override
    public String toString() {
        return "AuthProperties[issuer=" + issuer + ", accessTokenTtl=" + accessTokenTtl
                + ", refreshTokenTtl=" + refreshTokenTtl + ", secrets=****]";
    }
}
