package com.example.ikimina.model;

import java.math.BigDecimal;

import java.time.LocalDate;
import java.util.List;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
@Table(name = "savings_cycles")
public class SavingsCycle {
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
    @JoinColumn(name = "savings_group_id", nullable = false)
    private SavingsGroup savingsGroup;
    
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;
    
    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private CycleStatus status;
    
    @Column(name = "total_ubwizigame_collected", precision = 19, scale = 2)
    private BigDecimal totalUbwizigameCollected = BigDecimal.ZERO;
    
    @Column(name = "total_ingoboka_collected", precision = 19, scale = 2)
    private BigDecimal totalIngobokaCollected = BigDecimal.ZERO;
    
    @Column(name = "total_ubwizigame_distributed", precision = 19, scale = 2)
    private BigDecimal totalUbwizigameDistributed = BigDecimal.ZERO;
    
    @OneToMany(mappedBy = "savingsCycle", cascade = CascadeType.ALL)
    private List<MemberPayout> memberPayouts;
    
    @Column(name = "created_at", nullable = false)
    private LocalDate createdAt = LocalDate.now();
    
    @Column(name = "distributed_at")
    private LocalDate distributedAt;
    
    public enum CycleStatus {
        ACTIVE,
        COMPLETED,
        DISTRIBUTED
    }
}
