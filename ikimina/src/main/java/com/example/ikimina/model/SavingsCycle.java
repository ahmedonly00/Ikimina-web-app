package com.example.ikimina.model;

import java.time.LocalDate;
import java.util.List;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
@Table(name = "savings_cycles")
public class SavingsCycle {
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
    
    @Column(name = "total_ubwizigame_collected")
    private Double totalUbwizigameCollected = 0.0;
    
    @Column(name = "total_ingoboka_collected")
    private Double totalIngobokaCollected = 0.0;
    
    @Column(name = "total_ubwizigame_distributed")
    private Double totalUbwizigameDistributed = 0.0;
    
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
