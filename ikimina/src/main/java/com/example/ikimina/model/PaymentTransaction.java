package com.example.ikimina.model;

import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
@Table(name = "payment_transactions")
public class PaymentTransaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne
    @JoinColumn(name = "group_id", nullable = false)
    private SavingsGroup group;
    
    @ManyToOne
    @JoinColumn(name = "subscription_id", nullable = false)
    private GroupSubscription subscription;
    
    @Column(name = "transaction_id", nullable = false, unique = true)
    private String transactionId;
    
    @Column(name = "amount", nullable = false)
    private Double amount;
    
    @Column(name = "currency", nullable = false)
    private String currency;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false)
    private PaymentMethod paymentMethod;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PaymentStatus status;
    
    @Column(name = "payment_date", nullable = false)
    private LocalDateTime paymentDate;
    
    @Column(name = "processed_at")
    private LocalDateTime processedAt;
    
    @Column(name = "external_reference")
    private String externalReference;
    
    @Column(name = "failure_reason")
    private String failureReason;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
    
    @Column(name = "processed_by")
    private Long processedBy;
    
    @Column(name = "notes")
    private String notes;
    
    public enum PaymentMethod {
        MOBILE_MONEY,
        BANK_TRANSFER,
        CASH,
        MANUAL_OVERRIDE
    }
    
    public enum PaymentStatus {
        PENDING,
        COMPLETED,
        FAILED,
        REFUNDED
    }
}
