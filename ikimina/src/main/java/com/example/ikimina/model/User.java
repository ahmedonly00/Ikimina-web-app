package com.example.ikimina.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.example.ikimina.enums.Role;
import com.fasterxml.jackson.annotation.JsonIgnore;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
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

    @Column(name = "member_number", nullable = false, unique = true)
    private String memberNumber;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false, unique = true)
    private String phoneNumber;

    @Column(nullable = false, length = 120)
    @JsonIgnore
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private Role role;
    
    @Column(name = "is_active")
    private boolean active = true;

    @Column(name = "full_name")
    private String fullName;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

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

    // Helper methods to manage the relationship
    public void addMemberGroup(SavingsGroup group) {
        this.memberGroups.add(group);
        group.getMembers().add(this);
    }
    
    public void removeMemberGroup(SavingsGroup group) {
        this.memberGroups.remove(group);
        group.getMembers().remove(this);
    }
    
    // Helper methods for role management
    public Set<Role> getRoles() {
        Set<Role> roles = new HashSet<>();
        roles.add(this.role);
        return roles;
    }
    
    // Helper method to update full name when first or last name changes
    public void updateFullName() {
        if (firstName != null && lastName != null) {
            this.fullName = firstName + " " + lastName;
        }
    }
    
    // Override setters to automatically update fullName
    public void setFirstName(String firstName) {
        this.firstName = firstName;
        updateFullName();
    }
    
    public void setLastName(String lastName) {
        this.lastName = lastName;
        updateFullName();
    }
    
    public boolean isSuperAdmin() {
        return this.role == Role.ROLE_SUPER_ADMIN;
    }
    
    public boolean isGroupAdmin() {
        return this.role == Role.ROLE_GROUP_ADMIN;
    }
    
    public boolean isMember() {
        return this.role == Role.ROLE_USER;
    }
    
    public String getFullName() {
        return this.firstName + " " + this.lastName;
    }
}