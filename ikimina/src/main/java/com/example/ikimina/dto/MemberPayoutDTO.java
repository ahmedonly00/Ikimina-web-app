package com.example.ikimina.dto;

import java.math.BigDecimal;

import java.time.LocalDate;

import lombok.Data;

@Data
public class MemberPayoutDTO {
    private Long id;
    private Long memberId;
    private String memberName;
    private String memberNumber;
    private BigDecimal totalUbwizigame;
    private BigDecimal totalIngoboka;
    private BigDecimal payoutAmount;
    private LocalDate createdAt;
    private LocalDate paidAt;
    private String status;
}
