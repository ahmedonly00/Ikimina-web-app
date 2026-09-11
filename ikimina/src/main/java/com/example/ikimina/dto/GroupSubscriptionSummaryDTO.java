package com.example.ikimina.dto;

import java.math.BigDecimal;

import java.time.LocalDate;

import lombok.Data;

@Data
public class GroupSubscriptionSummaryDTO {
    private Long groupId;
    private String groupName;
    private String groupAdminName;
    private String status;
    private String planName;
    private BigDecimal monthlyPrice;
    private LocalDate endDate;
    private LocalDate gracePeriodEnd;
    private Integer memberCount;
    private Boolean isSuspended;
    private String suspensionReason;
    private Integer daysUntilExpiry;
    private Boolean needsAttention;
}
