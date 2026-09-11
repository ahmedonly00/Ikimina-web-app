package com.example.ikimina.security;

import com.example.ikimina.repository.SavingsGroupRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Object-level authorization for user-scoped endpoints.
 *
 * Referenced from @PreAuthorize as {@code @userSecurity.hasAccessToUser(...)}.
 * Without this, any authenticated member could read another member's financial
 * records by changing the id in the URL.
 *
 * Uses repository queries rather than walking lazy collections, because method
 * security runs outside a transaction (spring.jpa.open-in-view=false).
 */
@Component("userSecurity")
public class UserSecurity {

    private final SavingsGroupRepository savingsGroupRepository;

    public UserSecurity(SavingsGroupRepository savingsGroupRepository) {
        this.savingsGroupRepository = savingsGroupRepository;
    }

    public boolean hasAccessToUser(Authentication authentication, Long userId) {
        if (authentication == null || !authentication.isAuthenticated() || userId == null) {
            return false;
        }
        if (!(authentication.getPrincipal() instanceof CustomUserDetails principal)) {
            return false;
        }

        // A user may always read their own records.
        if (Objects.equals(principal.getUserId(), userId)) {
            return true;
        }

        if (isSuperAdmin(principal)) {
            return true;
        }

        // A group admin may read records of members in the groups they administer.
        if (isGroupAdmin(principal) && principal.getUserId() != null) {
            return savingsGroupRepository.isAdminOfGroupContainingUser(principal.getUserId(), userId);
        }

        return false;
    }

    /** True when every id in the list is accessible to the caller. */
    public boolean hasAccessToAllUsers(Authentication authentication, java.util.List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return false;
        }
        return userIds.stream().allMatch(id -> hasAccessToUser(authentication, id));
    }

    private boolean isSuperAdmin(CustomUserDetails principal) {
        return hasAuthority(principal, "ROLE_SUPER_ADMIN");
    }

    private boolean isGroupAdmin(CustomUserDetails principal) {
        return hasAuthority(principal, "ROLE_GROUP_ADMIN");
    }

    private boolean hasAuthority(CustomUserDetails principal, String authority) {
        return principal.getAuthorities().stream()
                .anyMatch(a -> authority.equals(a.getAuthority()));
    }
}
