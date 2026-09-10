package com.example.ikimina.dto;

import java.math.BigDecimal;

import java.time.LocalDate;

import com.example.ikimina.enums.Period;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReportDTO {
    private Long id;
    private Period period;
    private LocalDate fromDate;
    private LocalDate toDate;
    private BigDecimal totalSavings;
    private BigDecimal totalFines;
    private LocalDate generatedOn;
    private Long userId;
}