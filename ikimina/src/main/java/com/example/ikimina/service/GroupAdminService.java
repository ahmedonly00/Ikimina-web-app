package com.example.ikimina.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.ikimina.audit.AuditService;
import com.example.ikimina.model.User;
import com.example.ikimina.model.SavingsGroup;
import com.example.ikimina.enums.Role;
import com.example.ikimina.repository.SavingsGroupRepository;
import com.example.ikimina.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

@Service
@RequiredArgsConstructor
@Slf4j
public class GroupAdminService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final SavingsGroupRepository savingsGroupRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    /**
     * Provisions the group's admin account.
     *
     * The generated password is returned to the caller for one-time display and
     * is deliberately NOT logged - credentials in application logs end up in log
     * aggregation and are a reportable data-protection incident.
     */
    @Transactional
    public CreatedGroupAdmin createGroupAdmin(SavingsGroup savingsGroup,
                                              String adminEmail,
                                              String firstName,
                                              String lastName,
                                              String phoneNumber) {
        String temporaryPassword = generateTemporaryPassword();

        User groupAdmin = new User();
        groupAdmin.setUsername("admin_" + savingsGroup.getName().toLowerCase().replace(" ", "_"));
        groupAdmin.setEmail(adminEmail);
        groupAdmin.setPassword(passwordEncoder.encode(temporaryPassword));
        groupAdmin.setFirstName(firstName);
        groupAdmin.setLastName(lastName);
        groupAdmin.setFullName(firstName + " " + lastName);
        groupAdmin.setMemberNumber(generateMemberNumber(savingsGroup.getId()));
        groupAdmin.setPhoneNumber(phoneNumber);
        groupAdmin.setRole(Role.ROLE_GROUP_ADMIN);
        groupAdmin.setActive(true);
        groupAdmin.setCreatedAt(LocalDateTime.now());
        groupAdmin.setUpdatedAt(LocalDateTime.now());

        groupAdmin.getMemberGroups().add(savingsGroup);

        User savedAdmin = userRepository.save(groupAdmin);

        // The group was saved in an earlier transaction and is detached here, so
        // the admin link has to be written back explicitly.
        savingsGroup.setAdmin(savedAdmin);
        savingsGroupRepository.save(savingsGroup);

        // Identifiers only - no secret material.
        log.info("Group admin provisioned for group id={} userId={}",
                savingsGroup.getId(), savedAdmin.getId());

        auditService.record("USER", "USER", savedAdmin.getId(), "CREATE_GROUP_ADMIN",
                null, "ROLE_GROUP_ADMIN",
                "Group admin provisioned for group " + savingsGroup.getId());

        return new CreatedGroupAdmin(savedAdmin, adminEmail, temporaryPassword);
    }

    @Transactional
    public CreatedGroupAdmin createGroupAdmin(SavingsGroup savingsGroup, GroupAdminRequest request) {
        return createGroupAdmin(savingsGroup, request.getEmail(), request.getFirstName(),
                request.getLastName(), request.getPhoneNumber());
    }

    /** 24 bytes of CSPRNG output, URL-safe. Replaces a truncated random UUID. */
    private String generateTemporaryPassword() {
        byte[] bytes = new byte[18];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String generateMemberNumber(Long groupId) {
        return "GA" + String.format("%03d", groupId);
    }

    /** The created admin plus its one-time password. */
    public record CreatedGroupAdmin(User user, String email, String temporaryPassword) {
    }

    public static class GroupAdminRequest {
        private String email;
        private String firstName;
        private String lastName;
        private String phoneNumber;

        public GroupAdminRequest() {
        }

        public GroupAdminRequest(String email, String firstName, String lastName, String phoneNumber) {
            this.email = email;
            this.firstName = firstName;
            this.lastName = lastName;
            this.phoneNumber = phoneNumber;
        }

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }

        public String getFirstName() { return firstName; }
        public void setFirstName(String firstName) { this.firstName = firstName; }

        public String getLastName() { return lastName; }
        public void setLastName(String lastName) { this.lastName = lastName; }

        public String getPhoneNumber() { return phoneNumber; }
        public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
    }
}
