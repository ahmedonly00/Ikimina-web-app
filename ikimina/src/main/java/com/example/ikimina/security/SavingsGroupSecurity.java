package com.example.ikimina.security;

import com.example.ikimina.repository.MembershipRequestRepository;
import com.example.ikimina.repository.SavingsGroupRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.Objects;

/** Object-level authorization for group-scoped endpoints. */
@Component("savingsGroupSecurity")
public class SavingsGroupSecurity {

    private final SavingsGroupRepository savingsGroupRepository;
    private final MembershipRequestRepository membershipRequestRepository;

    public SavingsGroupSecurity(SavingsGroupRepository savingsGroupRepository,
                                MembershipRequestRepository membershipRequestRepository) {
        this.savingsGroupRepository = savingsGroupRepository;
        this.membershipRequestRepository = membershipRequestRepository;
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

    /**
     * The request actually belongs to the group named in the path.
     *
     * Without this, an administrator of group A could approve a request
     * belonging to group B by putting their own groupId in the path and
     * somebody else's requestId after it - canAdministerGroup would pass,
     * because it only ever looks at the path segment. Both halves have to
     * agree for the check to mean anything.
     */
    public boolean requestBelongsToGroup(Long requestId, Long groupId) {
        if (requestId == null || groupId == null) {
            return false;
        }
        return membershipRequestRepository.findById(requestId)
                .map(r -> Objects.equals(groupId, r.getSavingsGroup().getId()))
                .orElse(false);
    }

    private Object principal(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        return authentication.getPrincipal();
    }
}
