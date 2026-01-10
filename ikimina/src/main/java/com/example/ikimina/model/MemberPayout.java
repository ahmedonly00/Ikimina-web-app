package com.example.ikimina.model;

import java.time.LocalDate;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
@Table(name = "member_payouts")
public class MemberPayout {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne
    @JoinColumn(name = "savings_cycle_id", nullable = false)
    private SavingsCycle savingsCycle;
    
    @ManyToOne
    @JoinColumn(name = "member_id", nullable = false)
    private User member;
    
    @Column(name = "total_ubwizigame", nullable = false)
    private Double totalUbwizigame = 0.0;
    
    @Column(name = "total_ingoboka", nullable = false)
    private Double totalIngoboka = 0.0;
    
    @Column(name = "payout_amount", nullable = false)
    private Double payoutAmount = 0.0;
    
    @Column(name = "created_at", nullable = false)
    private LocalDate createdAt = LocalDate.now();
    
    @Column(name = "paid_at")
    private LocalDate paidAt;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PayoutStatus status;
    
    public enum PayoutStatus {
        PENDING,
        PAID,
        FAILED
    }
}
