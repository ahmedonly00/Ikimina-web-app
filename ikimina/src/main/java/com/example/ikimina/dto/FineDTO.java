package com.example.ikimina.dto;

import java.time.LocalDate;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FineDTO {
    private Long id;
    private String reason;
    private Double amount;
    private LocalDate date;
    private Long userId;
}
