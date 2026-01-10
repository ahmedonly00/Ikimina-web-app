package com.example.ikimina.dto;

import java.time.LocalDate;

import lombok.Data;

@Data
public class MemberPayoutDTO {
    private Long id;
    private Long memberId;
    private String memberName;
    private String memberNumber;
    private Double totalUbwizigame;
    private Double totalIngoboka;
    private Double payoutAmount;
    private LocalDate createdAt;
    private LocalDate paidAt;
    private String status;
}
