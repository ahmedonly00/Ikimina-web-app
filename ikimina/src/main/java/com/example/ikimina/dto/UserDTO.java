package com.example.ikimina.dto;

import com.example.ikimina.enums.Role;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UserDTO {

    private Long id;

    private String username;

    @NotBlank(message = "First name is required")
    @Size(max = 100, message = "First name must be at most 100 characters")
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Size(max = 100, message = "Last name must be at most 100 characters")
    private String lastName;

    @NotBlank(message = "Email is required")
    @Email(message = "Must be a valid email address")
    private String email;

    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^[+]?[0-9 ]{9,15}$", message = "Must be a valid phone number")
    private String phoneNumber;

    // Accepted on write (registration, password change) but never serialised
    // back to a client.
    @Size(min = 8, message = "Password must be at least 8 characters")
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;

    private String memberNumber;

    private Set<Long> roleIds;

    // Role is assigned by the server, never accepted from the client - otherwise
    // a registrant could grant themselves ROLE_SUPER_ADMIN.
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Role role;

    private Long savingsGroupId;

    private String savingsGroupName;

    private boolean active;
}
