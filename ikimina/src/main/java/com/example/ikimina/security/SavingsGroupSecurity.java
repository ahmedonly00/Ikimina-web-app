package com.example.ikimina.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component("savingsGroupSecurity")
public class SavingsGroupSecurity {
    
    public boolean isGroupMember(Authentication authentication, Long groupId) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        
        Object principal = authentication.getPrincipal();
        if (principal instanceof CustomUserDetails) {
            CustomUserDetails userDetails = (CustomUserDetails) principal;
            return groupId.equals(userDetails.getSavingsGroupId());
        }
        
        return false;
    }
    
    public static Long getCurrentUserGroupId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() 
                && authentication.getPrincipal() instanceof CustomUserDetails) {
            return ((CustomUserDetails) authentication.getPrincipal()).getSavingsGroupId();
        }
        return null;
    }
}
