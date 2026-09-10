package com.example.ikimina.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.example.ikimina.model.User;
import com.example.ikimina.enums.Role;
import com.example.ikimina.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${ikimina.superadmin.email}")
    private String superAdminEmail;

    @Value("${ikimina.superadmin.password:}")
    private String configuredPassword;

    @Override
    @Transactional
    public void run(String... args) {
        initializeSuperAdmin();
    }

    private void initializeSuperAdmin() {
        if (userRepository.existsByEmail(superAdminEmail)) {
            log.info("Super admin already present; skipping bootstrap");
            return;
        }

        // Prefer an operator-supplied password. Only if none is configured do we
        // generate one and print it once, so there is no hardcoded credential in
        // source control.
        boolean generated = configuredPassword == null || configuredPassword.isBlank();
        String password = generated ? generateBootstrapPassword() : configuredPassword;

        User superAdmin = new User();
        superAdmin.setUsername("superadmin");
        superAdmin.setEmail(superAdminEmail);
        superAdmin.setPassword(passwordEncoder.encode(password));
        superAdmin.setFirstName("Super");
        superAdmin.setLastName("Admin");
        superAdmin.setFullName("Super Admin");
        superAdmin.setMemberNumber("SA001");
        superAdmin.setPhoneNumber("+250000000000");
        superAdmin.setRole(Role.ROLE_SUPER_ADMIN);
        superAdmin.setActive(true);
        superAdmin.setCreatedAt(LocalDateTime.now());
        superAdmin.setUpdatedAt(LocalDateTime.now());

        userRepository.save(superAdmin);

        if (generated) {
            log.warn("""

                    ============================================================
                    Super admin created: {}
                    Generated one-time password: {}
                    This is printed only once. Sign in and change it now, or set
                    IKIMINA_SUPERADMIN_PASSWORD and recreate the account.
                    ============================================================""",
                    superAdminEmail, password);
        } else {
            log.info("Super admin created: {} (password taken from IKIMINA_SUPERADMIN_PASSWORD)",
                    superAdminEmail);
        }
    }

    private String generateBootstrapPassword() {
        byte[] bytes = new byte[18];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
