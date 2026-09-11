package com.example.ikimina.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/** Read-only access to the authenticated principal. */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static Optional<CustomUserDetails> currentPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof CustomUserDetails principal)) {
            return Optional.empty();
        }
        return Optional.of(principal);
    }

    /**
     * Id of the acting user. Audit records and "performed by" fields must use
     * this rather than a placeholder, or the audit trail attributes every action
     * to the same account.
     */
    public static Long currentUserId() {
        return currentPrincipal()
                .map(CustomUserDetails::getUserId)
                .orElseThrow(() -> new IllegalStateException("No authenticated user in security context"));
    }

    public static Optional<Long> currentGroupId() {
        return currentPrincipal().map(CustomUserDetails::getSavingsGroupId);
    }

    public static Optional<String> currentEmail() {
        return currentPrincipal().map(CustomUserDetails::getUsername);
    }

    public static boolean hasRole(String roleWithPrefix) {
        return currentPrincipal()
                .map(p -> p.getAuthorities().stream()
                        .anyMatch(a -> roleWithPrefix.equals(a.getAuthority())))
                .orElse(false);
    }

    public static boolean isSuperAdmin() {
        return hasRole("ROLE_SUPER_ADMIN");
    }
}
