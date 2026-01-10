package com.example.ikimina.model;

import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
@Table(name = "audit_logs")
public class AuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "entity_type", nullable = false)
    private String entityType;
    
    @Column(name = "entity_id", nullable = false)
    private Long entityId;
    
    @Column(name = "action", nullable = false)
    private String action;
    
    @Column(name = "old_value", columnDefinition = "TEXT")
    private String oldValue;
    
    @Column(name = "new_value", columnDefinition = "TEXT")
    private String newValue;
    
    @Column(name = "performed_by", nullable = false)
    private Long performedBy;
    
    @Column(name = "performed_by_name", nullable = false)
    private String performedByName;
    
    @Column(name = "performed_by_role", nullable = false)
    private String performedByRole;
    
    @Column(name = "ip_address")
    private String ipAddress;
    
    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
    
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
    
    @Column(name = "module", nullable = false)
    private String module;
    
    public enum Module {
        SUBSCRIPTION,
        GROUP,
        USER,
        PAYMENT,
        SYSTEM
    }
}
