package com.example.ikimina.controller;

import com.example.ikimina.dto.UserDTO;
import com.example.ikimina.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/users")
@CrossOrigin(origins = "*")
public class UserController {

    @Autowired
    private UserService userService;

    @GetMapping
    @PreAuthorize("hasRole('ROLE_SUPER_ADMIN')")
    public ResponseEntity<List<UserDTO>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    @GetMapping("/active")
    @PreAuthorize("hasRole('ROLE_GROUP_ADMIN') or hasRole('ROLE_SUPER_ADMIN')")
    public ResponseEntity<List<UserDTO>> getAllActiveUsers() {
        return ResponseEntity.ok(userService.getAllActiveUsers());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_SUPER_ADMIN') or hasRole('ROLE_GROUP_ADMIN')")
    public ResponseEntity<UserDTO> getUserById(@PathVariable Long id) {
        return userService.getUserById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_SUPER_ADMIN') or (hasRole('ROLE_GROUP_ADMIN') and @userSecurity.hasAccessToUser(authentication, #id))")
    public ResponseEntity<UserDTO> updateUser(@PathVariable Long id, @RequestBody UserDTO userDTO) {
        return ResponseEntity.ok(userService.updateUser(id, userDTO));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('ROLE_SUPER_ADMIN') or hasRole('ROLE_GROUP_ADMIN')")
    public ResponseEntity<UserDTO> updateUserStatus(
            @PathVariable Long id,
            @RequestParam boolean active) {
        return ResponseEntity.ok(userService.updateUserStatus(id, active));
    }

    @GetMapping("/group/{groupId}")
    @PreAuthorize("hasRole('ROLE_GROUP_ADMIN')")
    public ResponseEntity<List<UserDTO>> getUsersByGroup(@PathVariable Long groupId) {
        return ResponseEntity.ok(userService.getUsersBySavingsGroup(groupId));
    }

    @PostMapping("/{userId}/roles/{roleId}")
    @PreAuthorize("hasRole('ROLE_SUPER_ADMIN')")
    public ResponseEntity<UserDTO> addRoleToUser(
            @PathVariable Long userId,
            @PathVariable Long roleId) {
        return ResponseEntity.ok(userService.addRoleToUser(userId, roleId));
    }

    @DeleteMapping("/{userId}/roles/{roleId}")
    @PreAuthorize("hasRole('ROLE_SUPER_ADMIN')")
    public ResponseEntity<UserDTO> removeRoleFromUser(
            @PathVariable Long userId,
            @PathVariable Long roleId) {
        return ResponseEntity.ok(userService.removeRoleFromUser(userId, roleId));
    }
}
