package com.example.ikimina.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * What a person may send when registering.
 *
 * Public registration used to accept a whole {@code UserDTO}, which carries
 * {@code role}, {@code active} and {@code savingsGroupId}. The role was
 * ignored by the service and the account was always created as a member, but
 * {@code savingsGroupId} was honoured - so anyone could put themselves into
 * any group by guessing an id.
 *
 * This type exists so that the request simply has no field for choosing a
 * group directly. A registrant may present an invite code, or ask to join a
 * group and wait for an administrator; nothing here grants membership on its
 * own.
 */
@Data
public class RegistrationRequest {

    @NotBlank(message = "First name is required")
    @Size(max = 100)
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Size(max = 100)
    private String lastName;

    @NotBlank(message = "Email is required")
    @Email(message = "Email is not valid")
    @Size(max = 255)
    private String email;

    /*
     * Required, because users.phone_number is NOT NULL - and for a savings
     * group it is the contact that actually matters. Left optional here it
     * failed at the insert with a constraint violation instead of a usable
     * validation message.
     */
    @NotBlank(message = "Phone number is required")
    @Size(max = 30)
    private String phoneNumber;

    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String password;

    /**
     * Invite code. When present it alone decides the group, and membership is
     * granted immediately.
     */
    @Size(max = 32)
    private String joinCode;

    /**
     * Group the person is asking to join. Unlike the old savingsGroupId this
     * grants nothing: it records a request for an administrator to decide.
     */
    private Long requestGroupId;
}
