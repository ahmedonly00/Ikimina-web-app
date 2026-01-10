package com.example.ikimina.dto;

import java.time.LocalDate;

import com.example.ikimina.enums.LoanStatus;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoanDTO {
    private Long id;
    private Double amount;
    private Double interestRate;
    private LocalDate requestDate;
    private LocalDate dueDate;
    private LoanStatus status;
    private Long userId;
    
}
