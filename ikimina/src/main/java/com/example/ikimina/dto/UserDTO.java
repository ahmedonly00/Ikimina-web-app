package com.example.ikimina.dto;

import java.util.Set;

public class UserDTO {
    private Long id;
    private String username;
    private String firstName;
    private String lastName;
    private String email;
    private String phoneNumber;
    private String password;
    private Set<Long> roleIds;
    private Set<String> roles;
    private Long savingsGroupId;
    private String savingsGroupName;
    private boolean active;

    public UserDTO() {
    }

    public UserDTO(Long id, String username, String firstName, String lastName, String email, 
                  String phoneNumber, Set<String> roles, boolean active) {
        this.id = id;
        this.username = username;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.phoneNumber = phoneNumber;
        this.roles = roles;
        this.active = active;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }
    public String getEmail() {
        return email;
    }
    public void setEmail(String email) {
        this.email = email;
    }
    public String getPhoneNumber() {
        return phoneNumber;
    }
    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }
    public String getPassword() {
        return password;
    }
    public void setPassword(String password) {
        this.password = password;
    }
    public Set<Long> getRoleIds() {
        return roleIds;
    }

    public void setRoleIds(Set<Long> roleIds) {
        this.roleIds = roleIds;
    }

    public Set<String> getRoles() {
        return roles;
    }

    public void setRoles(Set<String> roles) {
        this.roles = roles;
    }
    
    public Long getSavingsGroupId() {
        return savingsGroupId;
    }
    
    public void setSavingsGroupId(Long savingsGroupId) {
        this.savingsGroupId = savingsGroupId;
    }
    
    public String getSavingsGroupName() {
        return savingsGroupName;
    }
    
    public void setSavingsGroupName(String savingsGroupName) {
        this.savingsGroupName = savingsGroupName;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

}
