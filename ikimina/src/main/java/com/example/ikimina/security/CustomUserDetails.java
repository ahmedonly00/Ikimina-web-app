package com.example.ikimina.security;

import com.example.ikimina.model.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;

public class CustomUserDetails implements UserDetails {
    private final String username;
    private final String password;
    private final Collection<? extends GrantedAuthority> authorities;
    private final Long savingsGroupId;

    public CustomUserDetails(String username, String password, 
                           Collection<? extends GrantedAuthority> authorities, 
                           Long savingsGroupId) {
        this.username = username;
        this.password = password;
        this.authorities = authorities;
        this.savingsGroupId = savingsGroupId;
    }

    public static CustomUserDetails create(User user) {
        return new CustomUserDetails(
            user.getUsername(),
            user.getPassword(),
            user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority(role.name()))
                .collect(java.util.stream.Collectors.toList()),
            user.getMemberGroups().stream()
                .findFirst()
                .map(group -> group.getId())
                .orElse(null)
        );
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    public Long getSavingsGroupId() {
        return savingsGroupId;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
