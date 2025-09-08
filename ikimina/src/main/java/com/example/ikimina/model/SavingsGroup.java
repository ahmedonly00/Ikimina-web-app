package com.example.ikimina.model;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "savings_groups")
public class SavingsGroup {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(length = 1000)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admin_id")
    @JsonIgnore
    private User admin;

    @ManyToMany(mappedBy = "memberGroups", fetch = FetchType.LAZY)
    @JsonIgnore
    private Set<User> members = new HashSet<>();
    
    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    private LocalDateTime updatedAt;

    public SavingsGroup() {}

    public SavingsGroup(String name, String description, User admin) {
        this.name = name;
        this.description = description;
        this.admin = admin;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
    
    public User getAdmin() {
        return admin;
    }
    
    public void setAdmin(User admin) {
        this.admin = admin;
    }

    public Set<User> getMembers() {
        return members;
    }
    
    public void addMember(User user) {
        this.members.add(user);
        user.getMemberGroups().add(this);
    }
    
    public void removeMember(User user) {
        this.members.remove(user);
        user.getMemberGroups().remove(this);
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
