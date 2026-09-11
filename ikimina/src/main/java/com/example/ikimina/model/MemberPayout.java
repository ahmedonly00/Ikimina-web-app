package com.example.ikimina.model;

import java.math.BigDecimal;

import java.time.LocalDate;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
@Table(name = "member_payouts")
public class MemberPayout {
    /**
     * Optimistic lock. Two admins marking payouts paid concurrently would
     * otherwise both read the same cycle total and one update would be lost.
     */
    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne
    @JoinColumn(name = "savings_cycle_id", nullable = false)
    private SavingsCycle savingsCycle;
    
    @ManyToOne
    @JoinColumn(name = "member_id", nullable = false)
    private User member;
    
    @Column(name = "total_ubwizigame", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalUbwizigame = BigDecimal.ZERO;
    
    @Column(name = "total_ingoboka", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalIngoboka = BigDecimal.ZERO;
    
    @Column(name = "payout_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal payoutAmount = BigDecimal.ZERO;
    
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
