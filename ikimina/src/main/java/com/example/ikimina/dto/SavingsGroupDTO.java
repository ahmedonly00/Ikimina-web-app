package com.example.ikimina.dto;

public class SavingsGroupDTO {
    private String name;
    private String description;
    private Long adminUserId;
    private String adminUserEmail;
    private String adminUserPassword;
    private String adminUserFullName;
    private String adminUserPhoneNumber;

    public SavingsGroupDTO() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Long getAdminUserId() {
        return adminUserId;
    }

    public void setAdminUserId(Long adminUserId) {
        this.adminUserId = adminUserId;
    }

    public String getAdminUserEmail() {
        return adminUserEmail;
    }

    public void setAdminUserEmail(String adminUserEmail) {
        this.adminUserEmail = adminUserEmail;
    }

    public String getAdminUserPassword() {
        return adminUserPassword;
    }

    public void setAdminUserPassword(String adminUserPassword) {
        this.adminUserPassword = adminUserPassword;
    }

    public String getAdminUserFullName() {
        return adminUserFullName;
    }

    public void setAdminUserFullName(String adminUserFullName) {
        this.adminUserFullName = adminUserFullName;
    }

    public String getAdminUserPhoneNumber() {
        return adminUserPhoneNumber;
    }

    public void setAdminUserPhoneNumber(String adminUserPhoneNumber) {
        this.adminUserPhoneNumber = adminUserPhoneNumber;
    }
}
