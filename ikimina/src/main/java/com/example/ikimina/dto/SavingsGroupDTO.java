package com.example.ikimina.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SavingsGroupDTO {

    @NotBlank(message = "Group name is required")
    @Size(max = 150, message = "Group name must be at most 150 characters")
    private String name;

    @Size(max = 1000, message = "Description must be at most 1000 characters")
    private String description;

    private Long adminUserId;

    @NotBlank(message = "Group admin email is required")
    @Email(message = "Must be a valid email address")
    private String adminUserEmail;

    @NotBlank(message = "Group admin first name is required")
    private String adminUserFirstName;

    @NotBlank(message = "Group admin last name is required")
    private String adminUserLastName;

    @NotBlank(message = "Group admin phone number is required")
    @Pattern(regexp = "^[+]?[0-9 ]{9,15}$", message = "Must be a valid phone number")
    private String adminUserPhoneNumber;
}
