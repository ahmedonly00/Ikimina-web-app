package com.example.ikimina.model;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false, unique = true)
    private String phoneNumber;

    @Column(nullable = false, length = 120)
    @JsonIgnore
    private String password;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "user_roles",
        joinColumns = @JoinColumn(name = "user_id"),
        inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Set<Role> roles = new HashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "group_members",
        joinColumns = @JoinColumn(name = "user_id"),
        inverseJoinColumns = @JoinColumn(name = "group_id")
    )
    @JsonIgnore
    private Set<SavingsGroup> memberGroups = new HashSet<>();

    @OneToMany(mappedBy = "admin", fetch = FetchType.LAZY)
    @JsonIgnore
    private Set<SavingsGroup> adminOfGroups = new HashSet<>();
    
    @Column(name = "is_active")
    private boolean active = true;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    public enum RoleType {
        ROLE_SUPER_ADMIN,
        ROLE_GROUP_ADMIN,
        ROLE_USER
    }

    @Entity
    @Table(name = "roles")
    public static class Role {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @Enumerated(EnumType.STRING)
        @Column(length = 20, unique = true, nullable = false)
        private RoleType name;

        private String description;

        @CreationTimestamp
        @Column(updatable = false)
        private LocalDateTime createdAt;

        // Getters and Setters
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public RoleType getName() { return name; }
        public void setName(RoleType name) { this.name = name; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    }

    public User() {}

    public User(String username, String firstName, String lastName, String email, 
               String phoneNumber, String password) {
        this.username = username;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.phoneNumber = phoneNumber;
        this.password = password;
    }

    // Helper methods for roles
    public boolean hasRole(RoleType roleType) {
        return roles.stream().anyMatch(role -> role.getName() == roleType);
    }

    public void addRole(Role role) {
        this.roles.add(role);
    }

    public void removeRole(Role role) {
        this.roles.remove(role);
    }

    // Check if user is a super admin
    public boolean isSuperAdmin() {
        return hasRole(RoleType.ROLE_SUPER_ADMIN);
    }

    // Check if user is a group admin
    public boolean isGroupAdmin() {
        return hasRole(RoleType.ROLE_GROUP_ADMIN);
    }

    public Set<SavingsGroup> getMemberGroups() {
        return memberGroups;
    }

    public void setMemberGroups(Set<SavingsGroup> memberGroups) {
        this.memberGroups = memberGroups;
    }
    
    public void addMemberGroup(SavingsGroup group) {
        this.memberGroups.add(group);
        group.getMembers().add(this);
    }
    
    public void removeMemberGroup(SavingsGroup group) {
        this.memberGroups.remove(group);
        group.getMembers().remove(this);
    }

    public Long getId() { 
        return id; 
    }
    
    public void setId(Long id) { 
        this.id = id; 
    }
    
    public String getUsername() { 
        return username; 
    }
    
    public void setUsername(String username) { 
        this.username = username; 
    }
    
    public String getFirstName() { 
        return firstName; 
    }
    
    public void setFirstName(String firstName) { 
        this.firstName = firstName; 
    }
    
    public String getLastName() { 
        return lastName; 
    }
    
    public void setLastName(String lastName) { 
        this.lastName = lastName; 
    }

    public String getFullName() {
        return firstName + " " + lastName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Set<Role> getRoles() {
        return roles;
    }

    public void setRoles(Set<Role> roles) {
        this.roles = roles;
    }

}
