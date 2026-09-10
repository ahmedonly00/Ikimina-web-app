package com.example.ikimina.dto;

import java.math.BigDecimal;

import java.time.LocalDate;

import com.example.ikimina.enums.SavingsType;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SavingsDTO {

    private Long id;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Amount must be greater than zero")
    @Digits(integer = 15, fraction = 2, message = "Amount has too many digits")
    private BigDecimal amount;

    @NotNull(message = "Savings type is required")
    private SavingsType type;

    @NotNull(message = "Date is required")
    @PastOrPresent(message = "Date cannot be in the future")
    private LocalDate date;

    @NotNull(message = "Member is required")
    private Long userId;

    /*
     * Read-only display fields, populated on the way out and ignored on the way
     * in - deliberately unvalidated so they stay optional in a request body.
     * Without them a group-wide savings list can only print user ids, and the
     * admin screen has no way to say who a row belongs to.
     */
    private String memberName;

    private String memberNumber;
}
