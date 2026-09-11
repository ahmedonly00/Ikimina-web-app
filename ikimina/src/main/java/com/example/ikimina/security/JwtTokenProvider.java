package com.example.ikimina.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class JwtTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenProvider.class);

    /** HS256 requires at least 256 bits of key material. */
    private static final int MIN_SECRET_BYTES = 32;

    private static final String CLAIM_ROLES = "roles";
    private static final String CLAIM_GROUP_ID = "savingsGroupId";
    private static final String CLAIM_USER_ID = "userId";

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiration}")
    private long jwtExpiration;

    private SecretKey key;

    @PostConstruct
    public void init() {
        if (jwtSecret == null || jwtSecret.isBlank()) {
            throw new IllegalStateException(
                    "IKIMINA_JWT_SECRET is not set. Generate one with: openssl rand -hex 32");
        }
        byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "IKIMINA_JWT_SECRET is too short: " + keyBytes.length
                            + " bytes, need at least " + MIN_SECRET_BYTES);
        }
        this.key = Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Issues a token for an already-authenticated principal. The active group is
     * carried in the token so downstream authorization does not have to guess it.
     */
    public String generateToken(CustomUserDetails userDetails) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpiration);

        String authorities = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(","));

        return Jwts.builder()
                .subject(userDetails.getUsername())
                .claim(CLAIM_ROLES, authorities)
                .claim(CLAIM_GROUP_ID, userDetails.getSavingsGroupId())
                .claim(CLAIM_USER_ID, userDetails.getUserId())
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(key)
                .compact();
    }

    public boolean validateToken(String token) {
        try {
            parse(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            // Expected for expired or tampered tokens: log at debug so an
            // unauthenticated request storm cannot flood the logs.
            log.debug("Rejected JWT: {}", e.getMessage());
            return false;
        }
    }

    /** Rebuilds the principal from a verified token without a database round-trip. */
    public CustomUserDetails getUserDetails(String token) {
        Claims claims = parse(token);

        String roles = claims.get(CLAIM_ROLES, String.class);
        Collection<? extends GrantedAuthority> authorities = (roles == null || roles.isBlank())
                ? List.of()
                : Arrays.stream(roles.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .map(SimpleGrantedAuthority::new)
                        .collect(Collectors.toList());

        return new CustomUserDetails(
                claims.get(CLAIM_USER_ID, Long.class),
                claims.getSubject(),
                "",
                authorities,
                claims.get(CLAIM_GROUP_ID, Long.class),
                true
        );
    }

    public String getUsernameFromJWT(String token) {
        return parse(token).getSubject();
    }

    /**
     * jjwt 0.12 API: parser() rather than parserBuilder(), verifyWith rather
     * than setSigningKey, and parseSignedClaims/getPayload rather than
     * parseClaimsJws/getBody.
     */
    private Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}