package com.example.ikimina.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class LoginRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Must be a valid email address")
    private String email;

    @NotBlank(message = "Password is required")
    private String password;

    // Optional: required for group-scoped accounts, but a super admin belongs to
    // no group. Enforced in AuthController once the role is known.
    private Long savingsGroupId;
}
