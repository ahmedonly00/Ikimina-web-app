package com.example.ikimina.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
@Table(name = "group_subscriptions")
public class GroupSubscription {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne
    @JoinColumn(name = "group_id", nullable = false, unique = true)
    private SavingsGroup group;
    
    @ManyToOne
    @JoinColumn(name = "plan_id", nullable = false)
    private SubscriptionPlan plan;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SubscriptionStatus status;
    
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;
    
    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;
    
    @Column(name = "grace_period_end")
    private LocalDate gracePeriodEnd;
    
    @Column(name = "last_payment_date")
    private LocalDate lastPaymentDate;
    
    @Column(name = "auto_renew", nullable = false)
    private Boolean autoRenew = true;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
    
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
    
    @Column(name = "suspended_at")
    private LocalDateTime suspendedAt;
    
    @Column(name = "suspended_reason")
    private String suspendedReason;
    
    @Column(name = "manually_activated_by")
    private Long manuallyActivatedBy;
    
    @Column(name = "manual_activation_reason")
    private String manualActivationReason;
    
    public enum SubscriptionStatus {
        ACTIVE,
        GRACE_PERIOD,
        SUSPENDED,
        CANCELLED
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
