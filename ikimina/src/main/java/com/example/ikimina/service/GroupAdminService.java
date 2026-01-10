package com.example.ikimina.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.ikimina.model.User;
import com.example.ikimina.model.SavingsGroup;
import com.example.ikimina.enums.Role;
import com.example.ikimina.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class GroupAdminService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public User createGroupAdmin(SavingsGroup savingsGroup, String adminEmail, String firstName, String lastName, String phoneNumber) {
        // Generate a default password for the group admin
        String defaultPassword = generateDefaultPassword();
        
        // Create group admin user
        User groupAdmin = new User();
        groupAdmin.setUsername("admin_" + savingsGroup.getName().toLowerCase().replace(" ", "_"));
        groupAdmin.setEmail(adminEmail);
        groupAdmin.setPassword(passwordEncoder.encode(defaultPassword));
        groupAdmin.setFirstName(firstName);
        groupAdmin.setLastName(lastName);
        groupAdmin.setFullName(firstName + " " + lastName);  // Set the full name
        groupAdmin.setMemberNumber(generateMemberNumber(savingsGroup.getId()));  // Generate member number
        groupAdmin.setPhoneNumber(phoneNumber);
        groupAdmin.setRole(Role.ROLE_GROUP_ADMIN);
        groupAdmin.setActive(true);
        groupAdmin.setCreatedAt(LocalDateTime.now());
        groupAdmin.setUpdatedAt(LocalDateTime.now());
        
        // Add the savings group to the admin's member groups
        groupAdmin.getMemberGroups().add(savingsGroup);
        
        // Save the group admin first
        User savedAdmin = userRepository.save(groupAdmin);
        
        // Set the admin for the savings group
        savingsGroup.setAdmin(savedAdmin);
        
        log.info("Group admin created successfully:");
        log.info("Email: {}", adminEmail);
        log.info("Default password: {}", defaultPassword);
        log.info("Group: {}", savingsGroup.getName());
        log.info("Please update the password after first login!");
        
        return savedAdmin;
    }
    
    @Transactional
    public User createGroupAdmin(SavingsGroup savingsGroup, GroupAdminRequest request) {
        return createGroupAdmin(savingsGroup, request.getEmail(), request.getFirstName(), 
                              request.getLastName(), request.getPhoneNumber());
    }
    
    private String generateDefaultPassword() {
        // Generate a random password
        return UUID.randomUUID().toString().substring(0, 8) + "!";
    }
    
    private String generateMemberNumber(Long groupId) {
        // Generate member number based on group ID
        return "GA" + String.format("%03d", groupId);
    }
    
    public static class GroupAdminRequest {
        private String email;
        private String firstName;
        private String lastName;
        private String phoneNumber;
        
        // Constructors
        public GroupAdminRequest() {}
        
        public GroupAdminRequest(String email, String firstName, String lastName, String phoneNumber) {
            this.email = email;
            this.firstName = firstName;
            this.lastName = lastName;
            this.phoneNumber = phoneNumber;
        }
        
        // Getters and setters
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
