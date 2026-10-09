package rw.ikimina.identity.internal;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.Arrays;
import java.util.Base64;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import rw.ikimina.shared.phone.PhoneNumber;
import rw.ikimina.shared.security.AuthProperties;

/**
 * Development only (owner decision, Phase 1): creates a PLATFORM_ADMIN account if none
 * exists, so the platform side can be exercised locally. How the first production
 * administrator is created is still to be decided; this never runs outside the dev
 * profile and refuses to run if "prod" is active as well.
 *
 * <p>The password comes from IKIMINA_DEV_PLATFORM_ADMIN_PASSWORD, or is generated and
 * printed once - acceptable only because this is a local development account (spec 22).
 */
@Component
@Profile("dev")
class DevPlatformAdminSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevPlatformAdminSeeder.class);

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AuthProperties properties;
    private final TransactionTemplate transaction;
    private final Environment environment;
    private final Clock clock;
    private final String phone;
    private final String configuredPassword;

    DevPlatformAdminSeeder(UserRepository users, PasswordEncoder passwordEncoder, AuthProperties properties,
                           PlatformTransactionManager transactionManager, Environment environment, Clock clock,
                           @Value("${ikimina.dev.platform-admin.phone:+250780000001}") String phone,
                           @Value("${ikimina.dev.platform-admin.password:}") String configuredPassword) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
        this.transaction = new TransactionTemplate(transactionManager);
        this.environment = environment;
        this.clock = clock;
        this.phone = phone;
        this.configuredPassword = configuredPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (Arrays.asList(environment.getActiveProfiles()).contains("prod")) {
            throw new IllegalStateException("DevPlatformAdminSeeder must never run with the prod profile");
        }
        transaction.executeWithoutResult(status -> {
            if (users.existsByPlatformRole(User.ROLE_PLATFORM_ADMIN)) {
                return;
            }
            boolean generated = configuredPassword.isBlank();
            String password = generated ? randomPassword() : configuredPassword;
            users.save(User.platformAdmin(PhoneNumber.parse(phone), "Platform Admin (dev)",
                    passwordEncoder.encode(password), properties.termsVersion(), clock.instant()));
            if (generated) {
                log.warn("DEV ONLY - created platform admin {} with generated password: {}", phone, password);
            } else {
                log.warn("DEV ONLY - created platform admin {} with the configured password", phone);
            }
        });
    }

    private static String randomPassword() {
        byte[] bytes = new byte[18];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
