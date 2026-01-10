package com.example.ikimina.dto;

import lombok.Data;

@Data
public class SavingsGroupDTO {
    private String name;
    private String description;
    private Long adminUserId;
    private String adminUserEmail;
    private String adminUserPassword;
    private String adminUserFirstName;
    private String adminUserLastName;
    private String adminUserPhoneNumber;

}
