package com.example.ikimina.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.Data;

@Data
public class SubscriptionDTO {
    private Long id;
    private Long groupId;
    private String groupName;
    private Long planId;
    private String planName;
    private Double monthlyPrice;
    private String currency;
    private String status;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDate gracePeriodEnd;
    private LocalDate lastPaymentDate;
    private Boolean autoRenew;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime suspendedAt;
    private String suspendedReason;
    private Integer daysUntilExpiry;
    private Integer daysInGracePeriod;
    private Boolean isSuspended;
}
