package com.example.ikimina.dto;

import java.time.LocalDate;

import com.example.ikimina.enums.SavingsType;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SavingsDTO {
    private Long id;
    private Double amount;
    private SavingsType type;
    private LocalDate date;
    private Long userId;
}
