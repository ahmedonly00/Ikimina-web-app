package com.example.ikimina.dto;

public class LoginRequest {
    private String email;
    private String password;
    private Long savingsGroupId;

    public LoginRequest() {
    }

    public LoginRequest(String email, String password, Long savingsGroupId) {
        this.email = email;
        this.password = password;
        this.savingsGroupId = savingsGroupId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Long getSavingsGroupId() {
        return savingsGroupId;
    }

    public void setSavingsGroupId(Long savingsGroupId) {
        this.savingsGroupId = savingsGroupId;
    }
}