package com.example.ikimina.dto;

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
    private Double totalSavings;
    private Double totalFines;
    private LocalDate generatedOn;
    private Long userId;
}