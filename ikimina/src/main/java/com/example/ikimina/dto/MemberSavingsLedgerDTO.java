package com.example.ikimina.dto;

import java.time.LocalDate;
import java.util.Map;

import lombok.Data;

@Data
public class MemberSavingsLedgerDTO {
    private Long memberId;
    private String memberName;
    private Map<LocalDate, DailySavingsDTO> dailySavings;
    
    @Data
    public static class DailySavingsDTO {
        private Double ubwizigame;
        private Double ingoboka;
    }
}
