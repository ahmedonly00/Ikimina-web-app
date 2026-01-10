package com.example.ikimina.model;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
@Table(name = "subscription_plans")
public class SubscriptionPlan {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, unique = true)
    private String name;
    
    @Column(nullable = false)
    private Double monthlyPrice;
    
    @Column(name = "currency", nullable = false)
    private String currency = "RWF";
    
    @Column(name = "billing_cycle_days", nullable = false)
    private Integer billingCycleDays = 30;
    
    @Column(name = "grace_period_days", nullable = false)
    private Integer gracePeriodDays = 7;
    
    @Column(name = "max_members")
    private Integer maxMembers;
    
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
    
    @Column(name = "created_at", nullable = false)
    private java.time.LocalDateTime createdAt = java.time.LocalDateTime.now();
    
    @Column(name = "description")
    private String description;
}
