package com.example.ikimina.security;

import com.example.ikimina.model.User;
import com.example.ikimina.service.UserService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;
import java.util.Objects;

/**
 * Security component for user-related access control.
 */
@Component("userSecurity")
public class UserSecurity {
    
    private final UserService userService;
    
    public UserSecurity(UserService userService) {
        this.userService = userService;
    }
    
    /**
     * Check if the current user has access to another user's data.
     * 
     * @param authentication The authentication object containing the current user's details
     * @param userId The ID of the user being accessed
     * @return true if access is allowed, false otherwise
     */
    public boolean hasAccessToUser(Authentication authentication, Long userId) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        
        String currentUsername = authentication.getName();
        User currentUser = userService.findByEmail(currentUsername)
            .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + currentUsername));
            
        // Users can access their own data
        if (Objects.equals(currentUser.getId(), userId)) {
            return true;
        }
            
        // Super admins have access to all users
        if (currentUser.isSuperAdmin()) {
            return true;
        }
        
        // Group admins have access to users in their groups
        return currentUser.getMemberGroups().stream()
            .filter(group -> group.getAdmin() != null && group.getAdmin().getId().equals(currentUser.getId()))
            .flatMap(group -> group.getMembers().stream())
            .anyMatch(member -> member.getId().equals(userId));
    }
}
