package rw.ikimina.shared.security;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * Access tokens: HS256 JWTs carrying the user's public id, platform role and auth_time,
 * valid for 15 minutes (spec 16.1). Validation checks signature, algorithm, issuer,
 * audience and expiry against the injected clock; the subject is then re-checked against
 * the database on every request (see {@link AuthenticatedUserResolver}).
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AuthProperties.class)
public class JwtConfig {

    public static final String CLAIM_PLATFORM_ROLE = "platform_role";
    public static final String CLAIM_AUTH_TIME = "auth_time";

    private static final Duration CLOCK_SKEW = Duration.ofSeconds(30);

    @Bean
    public SecretKey accessTokenKey(AuthProperties properties) {
        return new SecretKeySpec(properties.jwtSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    @Bean
    public JwtEncoder jwtEncoder(SecretKey accessTokenKey) {
        return NimbusJwtEncoder.withSecretKey(accessTokenKey).algorithm(MacAlgorithm.HS256).build();
    }

    @Bean
    public JwtDecoder jwtDecoder(SecretKey accessTokenKey, AuthProperties properties, Clock clock) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(accessTokenKey).macAlgorithm(MacAlgorithm.HS256).build();
        JwtTimestampValidator expiry = new JwtTimestampValidator(CLOCK_SKEW);
        expiry.setClock(clock);
        List<OAuth2TokenValidator<Jwt>> validators = List.of(
                expiry,
                new JwtIssuerValidator(properties.issuer()),
                new JwtClaimValidator<List<String>>("aud", audience -> audience != null && audience.contains(properties.issuer())),
                new JwtClaimValidator<Object>(CLAIM_AUTH_TIME, authTime -> authTime != null));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(validators));
        return decoder;
    }

    @Bean
    public Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter(AuthenticatedUserResolver resolver) {
        return jwt -> {
            Instant authTime = Instant.ofEpochSecond(((Number) jwt.getClaim(CLAIM_AUTH_TIME)).longValue());
            UUID subject;
            try {
                subject = UUID.fromString(jwt.getSubject());
            } catch (IllegalArgumentException | NullPointerException e) {
                throw new BadCredentialsException("Malformed token subject");
            }
            return resolver.resolve(subject, jwt.getIssuedAt(), authTime)
                    .map(IkiminaAuthentication::new)
                    .orElseThrow(() -> new BadCredentialsException("Token subject is no longer valid"));
        };
    }
}
