package com.example.ikimina.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "savings_groups")
@Data
@NoArgsConstructor
@AllArgsConstructor
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
    
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
    
    @Column(name = "is_suspended", nullable = false)
    private Boolean isSuspended = false;
    
    @Column(name = "suspension_reason")
    private String suspensionReason;
    
    @Column(name = "suspended_at")
    private LocalDateTime suspendedAt;

    //Helper methods to manage the relationship
    public void addMember(User user) {
        this.members.add(user);
        user.getMemberGroups().add(this);
    }
    
    public void removeMember(User user) {
        this.members.remove(user);
        user.getMemberGroups().remove(this);
    }
     
}
