package rw.ikimina.identity.internal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import rw.ikimina.shared.security.AuthProperties;
import rw.ikimina.shared.security.JwtConfig;

/**
 * Issues access tokens and rotating refresh tokens (spec 16.1).
 *
 * <p>Refresh tokens are 256-bit random values; only their SHA-256 is stored. Each use
 * rotates the token. Presenting a token that was already rotated or revoked means a copy
 * exists somewhere else, so the whole family (that sign-in) is revoked.
 */
@Component
class SessionTokens {

    /** What a successful sign-in, verification or refresh hands back to the controller. */
    record IssuedSession(String accessToken, long accessTokenExpiresInSeconds, String refreshToken) {
    }

    sealed interface Rotation permits Rotated, ReuseDetected, Rejected {
    }

    record Rotated(User user, IssuedSession session) implements Rotation {
    }

    /** A rotated or revoked token was presented again: the family has been revoked. */
    record ReuseDetected(long userId) implements Rotation {
    }

    record Rejected() implements Rotation {
    }

    private final JwtEncoder encoder;
    private final RefreshTokenRepository refreshTokens;
    private final UserRepository users;
    private final AuthProperties properties;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    SessionTokens(JwtEncoder encoder, RefreshTokenRepository refreshTokens, UserRepository users,
                  AuthProperties properties, Clock clock) {
        this.encoder = encoder;
        this.refreshTokens = refreshTokens;
        this.users = users;
        this.properties = properties;
        this.clock = clock;
    }

    /** A new sign-in: new refresh-token family, password proven now. */
    @Transactional(propagation = Propagation.MANDATORY)
    IssuedSession startSession(User user) {
        Instant now = clock.instant();
        return new IssuedSession(accessToken(user, now), properties.accessTokenTtl().toSeconds(),
                newRefreshToken(user.getId(), UUID.randomUUID(), now, now));
    }

    /** Step-up: a fresh access token with a new auth_time; the refresh token is unchanged. */
    String reauthenticatedAccessToken(User user) {
        return accessToken(user, clock.instant());
    }

    long accessTokenTtlSeconds() {
        return properties.accessTokenTtl().toSeconds();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    Rotation rotate(String rawRefreshToken) {
        Instant now = clock.instant();
        Optional<RefreshToken> found = rawRefreshToken == null ? Optional.empty()
                : refreshTokens.findByTokenHashForUpdate(hash(rawRefreshToken));
        if (found.isEmpty()) {
            return new Rejected();
        }
        RefreshToken token = found.get();
        if (token.isRevoked()) {
            refreshTokens.revokeFamily(token.getFamilyId(), now);
            return new ReuseDetected(token.getUserId());
        }
        if (token.isExpired(now)) {
            return new Rejected();
        }
        Optional<User> user = users.findById(token.getUserId()).filter(User::isActive);
        if (user.isEmpty()) {
            refreshTokens.revokeFamily(token.getFamilyId(), now);
            return new Rejected();
        }
        token.revoke(now);
        String next = newRefreshToken(token.getUserId(), token.getFamilyId(), token.getAuthTime(), now);
        return new Rotated(user.get(), new IssuedSession(accessToken(user.get(), token.getAuthTime()),
                properties.accessTokenTtl().toSeconds(), next));
    }

    /** Sign-out: revoke the presented token's family. Unknown tokens are ignored. */
    @Transactional(propagation = Propagation.MANDATORY)
    Optional<Long> revokeFamilyOf(String rawRefreshToken) {
        if (rawRefreshToken == null) {
            return Optional.empty();
        }
        return refreshTokens.findByTokenHashForUpdate(hash(rawRefreshToken)).map(token -> {
            refreshTokens.revokeFamily(token.getFamilyId(), clock.instant());
            return token.getUserId();
        });
    }

    @Transactional(propagation = Propagation.MANDATORY)
    void revokeAllFor(long userId) {
        refreshTokens.revokeAllForUser(userId, clock.instant());
    }

    private String accessToken(User user, Instant authTime) {
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .audience(List.of(properties.issuer()))
                .subject(user.getPublicId().toString())
                .issuedAt(now)
                .expiresAt(now.plus(properties.accessTokenTtl()))
                .id(UUID.randomUUID().toString())
                .claim(JwtConfig.CLAIM_PLATFORM_ROLE, user.getPlatformRole())
                .claim(JwtConfig.CLAIM_AUTH_TIME, authTime.getEpochSecond())
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    private String newRefreshToken(long userId, UUID familyId, Instant authTime, Instant now) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        refreshTokens.save(new RefreshToken(userId, hash(raw), familyId, authTime, now.plus(properties.refreshTokenTtl()), now));
        return raw;
    }

    static String hash(String raw) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
