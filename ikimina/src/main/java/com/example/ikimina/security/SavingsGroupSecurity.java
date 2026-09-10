package com.example.ikimina.security;

import com.example.ikimina.repository.SavingsGroupRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.Objects;

/** Object-level authorization for group-scoped endpoints. */
@Component("savingsGroupSecurity")
public class SavingsGroupSecurity {

    private final SavingsGroupRepository savingsGroupRepository;

    public SavingsGroupSecurity(SavingsGroupRepository savingsGroupRepository) {
        this.savingsGroupRepository = savingsGroupRepository;
    }

    /** Caller belongs to the group (their token's active group matches). */
    public boolean isGroupMember(Authentication authentication, Long groupId) {
        if (groupId == null || !(principal(authentication) instanceof CustomUserDetails p)) {
            return false;
        }
        return Objects.equals(groupId, p.getSavingsGroupId());
    }

    /** Caller is the group's admin, or a super admin. */
    public boolean canAdministerGroup(Authentication authentication, Long groupId) {
        if (groupId == null || !(principal(authentication) instanceof CustomUserDetails p)) {
            return false;
        }
        boolean superAdmin = p.getAuthorities().stream()
                .anyMatch(a -> "ROLE_SUPER_ADMIN".equals(a.getAuthority()));
        if (superAdmin) {
            return true;
        }
        return p.getUserId() != null && savingsGroupRepository.isAdminOfGroup(p.getUserId(), groupId);
    }

    private Object principal(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        return authentication.getPrincipal();
    }
}
