package com.example.ikimina.dto;

import java.time.LocalDate;
import java.util.List;


import lombok.Data;

@Data
public class BulkSavingsEntryDTO {
    private LocalDate date;
    private List<MemberSavingsDTO> members;
    
    @Data
    public static class MemberSavingsDTO {
        private Long userId;
        private Double ubwizigameAmount;
        private Double ingobokaAmount;
    }
}
