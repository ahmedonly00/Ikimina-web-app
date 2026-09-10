package com.example.ikimina.security;

import com.example.ikimina.model.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

public class CustomUserDetails implements UserDetails {

    private final Long userId;
    private final String username;
    private final String password;
    private final Collection<? extends GrantedAuthority> authorities;
    private final Long savingsGroupId;
    private final boolean enabled;

    public CustomUserDetails(Long userId,
                             String username,
                             String password,
                             Collection<? extends GrantedAuthority> authorities,
                             Long savingsGroupId,
                             boolean enabled) {
        this.userId = userId;
        this.username = username;
        this.password = password;
        this.authorities = authorities;
        this.savingsGroupId = savingsGroupId;
        this.enabled = enabled;
    }

    // NOTE: there is deliberately no create(User) overload that derives the group
    // from user.getMemberGroups(). That collection is lazy and cannot initialise
    // outside a transaction; the caller passes the group id explicitly instead.

    /** Builds the principal scoped to a specific group (the one used at login). */
    public static CustomUserDetails create(User user, Long savingsGroupId) {
        // The email is the login identifier and therefore the JWT subject. It
        // must match what CustomUserDetailsService looks up, or every request
        // after login fails to resolve the principal.
        return new CustomUserDetails(
                user.getId(),
                user.getEmail(),
                user.getPassword(),
                List.of(new SimpleGrantedAuthority(user.getRole().name())),
                savingsGroupId,
                user.isActive()
        );
    }

    public Long getUserId() {
        return userId;
    }

    public Long getSavingsGroupId() {
        return savingsGroupId;
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
        return enabled;
    }
}
