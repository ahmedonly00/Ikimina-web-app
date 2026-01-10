package com.example.ikimina.dto;

import java.time.LocalDate;
import java.util.List;

import lombok.Data;

@Data
public class SavingsCycleDTO {
    private Long id;
    private Long savingsGroupId;
    private String savingsGroupName;
    private LocalDate startDate;
    private LocalDate endDate;
    private String status;
    private Double totalUbwizigameCollected;
    private Double totalIngobokaCollected;
    private Double totalUbwizigameDistributed;
    private LocalDate createdAt;
    private LocalDate distributedAt;
    private List<MemberPayoutDTO> memberPayouts;
}
