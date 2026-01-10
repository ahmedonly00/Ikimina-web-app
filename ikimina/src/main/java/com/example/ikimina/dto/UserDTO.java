package com.example.ikimina.dto;

import com.example.ikimina.enums.Role;
import java.util.Set;

import lombok.Data;

@Data
public class UserDTO {
    private Long id;
    private String username;
    private String firstName;
    private String lastName;
    private String email;
    private String phoneNumber;
    private String password;
    private Set<Long> roleIds;
    private Role role;
    private Long savingsGroupId;
    private String savingsGroupName;
    private boolean active;

}
