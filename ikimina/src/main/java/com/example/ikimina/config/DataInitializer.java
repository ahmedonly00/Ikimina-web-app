package com.example.ikimina.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.example.ikimina.model.User;
import com.example.ikimina.model.SavingsGroup;
import com.example.ikimina.enums.Role;
import com.example.ikimina.repository.UserRepository;
import com.example.ikimina.repository.SavingsGroupRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final SavingsGroupRepository savingsGroupRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        initializeSuperAdmin();
    }

    private void initializeSuperAdmin() {
        // Check if super admin already exists
        if (userRepository.existsByEmail("superadmin@ikimina.com")) {
            log.info("Super admin already exists");
            return;
        }

        // Create super admin user
        User superAdmin = new User();
        superAdmin.setUsername("superadmin");
        superAdmin.setEmail("superadmin@ikimina.com");
        superAdmin.setPassword(passwordEncoder.encode("REDACTED-ROTATED-PASSWORD"));
        superAdmin.setFirstName("Super");
        superAdmin.setLastName("Admin");
        superAdmin.setFullName("Super Admin");  // Set the full name
        superAdmin.setMemberNumber("SA001");  // Set member number
        superAdmin.setPhoneNumber("+250788123456");
        superAdmin.setRole(Role.ROLE_SUPER_ADMIN);
        superAdmin.setActive(true);
        superAdmin.setCreatedAt(LocalDateTime.now());
        superAdmin.setUpdatedAt(LocalDateTime.now());

        userRepository.save(superAdmin);
        log.info("Super admin created successfully with email: superadmin@ikimina.com");
        log.info("Default password: REDACTED-ROTATED-PASSWORD");
        log.info("Please change the password after first login!");
    }
}
