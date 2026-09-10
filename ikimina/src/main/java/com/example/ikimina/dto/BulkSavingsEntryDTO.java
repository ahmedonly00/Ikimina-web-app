package com.example.ikimina.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import lombok.Data;

@Data
public class BulkSavingsEntryDTO {

    @NotNull(message = "Date is required")
    @PastOrPresent(message = "Date cannot be in the future")
    private LocalDate date;

    // @Valid cascades into the list; without it the nested amounts are unchecked
    // and a null members list reached the service and threw an NPE.
    @NotEmpty(message = "At least one member entry is required")
    @Valid
    private List<MemberSavingsDTO> members;

    @Data
    public static class MemberSavingsDTO {

        @NotNull(message = "Member is required")
        private Long userId;

        // Both amounts are optional - an entry may record only one type - but a
        // supplied amount must be non-negative and within scale.
        @DecimalMin(value = "0.0", message = "Amount cannot be negative")
        @Digits(integer = 17, fraction = 2, message = "Amount has too many digits")
        private BigDecimal ubwizigameAmount;

        @DecimalMin(value = "0.0", message = "Amount cannot be negative")
        @Digits(integer = 17, fraction = 2, message = "Amount has too many digits")
        private BigDecimal ingobokaAmount;
    }
}
