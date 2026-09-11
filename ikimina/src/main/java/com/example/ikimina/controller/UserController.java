package com.example.ikimina.controller;

import com.example.ikimina.dto.UserDTO;
import com.example.ikimina.security.SecurityUtils;
import com.example.ikimina.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/users")
public class UserController {

    @Autowired
    private UserService userService;

    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<List<UserDTO>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    /**
     * Active members, scoped to a group.
     *
     * Previously returned every active user in the system to any group admin -
     * names, emails and phone numbers of members of other groups. A group admin
     * now only ever sees their own group; a super admin may name one explicitly.
     */
    @GetMapping("/active")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','GROUP_ADMIN')")
    public ResponseEntity<List<UserDTO>> getAllActiveUsers(
            @RequestParam(required = false) Long groupId) {

        if (SecurityUtils.isSuperAdmin()) {
            return ResponseEntity.ok(groupId == null
                    ? userService.getAllActiveUsers()
                    : userService.getActiveUsersForGroup(groupId));
        }

        Long callerGroup = SecurityUtils.currentGroupId()
                .orElseThrow(() -> new AccessDeniedException("No group associated with this account"));

        // A group admin may not read another group by asking for it.
        if (groupId != null && !groupId.equals(callerGroup)) {
            throw new AccessDeniedException("Not permitted to read another group's members");
        }

        return ResponseEntity.ok(userService.getActiveUsersForGroup(callerGroup));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@userSecurity.hasAccessToUser(authentication, #id)")
    public ResponseEntity<UserDTO> getUserById(@PathVariable Long id) {
        return userService.getUserById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN') or (hasRole('GROUP_ADMIN') and @userSecurity.hasAccessToUser(authentication, #id))")
    public ResponseEntity<UserDTO> updateUser(@PathVariable Long id, @RequestBody UserDTO userDTO) {
        return ResponseEntity.ok(userService.updateUser(id, userDTO));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('SUPER_ADMIN') or (hasRole('GROUP_ADMIN') and @userSecurity.hasAccessToUser(authentication, #id))")
    public ResponseEntity<UserDTO> updateUserStatus(
            @PathVariable Long id,
            @RequestParam boolean active) {
        return ResponseEntity.ok(userService.updateUserStatus(id, active));
    }

    @GetMapping("/group/{groupId}")
    @PreAuthorize("@savingsGroupSecurity.canAdministerGroup(authentication, #groupId)")
    public ResponseEntity<List<UserDTO>> getUsersByGroup(@PathVariable Long groupId) {
        return ResponseEntity.ok(userService.getUsersBySavingsGroup(groupId));
    }

    @PostMapping("/{userId}/roles/{roleId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<UserDTO> addRoleToUser(
            @PathVariable Long userId,
            @PathVariable Long roleId) {
        return ResponseEntity.ok(userService.addRoleToUser(userId, roleId));
    }

    @DeleteMapping("/{userId}/roles/{roleId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<UserDTO> removeRoleFromUser(
            @PathVariable Long userId,
            @PathVariable Long roleId) {
        return ResponseEntity.ok(userService.removeRoleFromUser(userId, roleId));
    }
}
