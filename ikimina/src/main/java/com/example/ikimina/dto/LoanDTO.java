package com.example.ikimina.dto;

import java.math.BigDecimal;

import java.time.LocalDate;

import com.example.ikimina.enums.LoanStatus;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoanDTO {

    private Long id;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Amount must be greater than zero")
    @Digits(integer = 15, fraction = 2, message = "Amount has too many digits")
    private BigDecimal amount;

    @NotNull(message = "Interest rate is required")
    @PositiveOrZero(message = "Interest rate cannot be negative")
    private BigDecimal interestRate;

    private LocalDate requestDate;

    @NotNull(message = "Due date is required")
    private LocalDate dueDate;

    private LoanStatus status;

    @NotNull(message = "Member is required")
    private Long userId;
}
