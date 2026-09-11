package com.example.ikimina.dto;

import java.math.BigDecimal;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class PaymentTransactionDTO {
    private Long id;
    private Long groupId;
    private String groupName;
    private Long subscriptionId;
    private String transactionId;
    private BigDecimal amount;
    private String currency;
    private String paymentMethod;
    private String status;
    private LocalDateTime paymentDate;
    private LocalDateTime processedAt;
    private String externalReference;
    private String failureReason;
    private Long processedBy;
    private String processedByName;
    private String notes;
}
