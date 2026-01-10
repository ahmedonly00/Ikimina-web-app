package com.example.ikimina.controller;

import com.example.ikimina.dto.SavingsGroupDTO;
import com.example.ikimina.model.SavingsGroup;
import com.example.ikimina.service.GroupService;
import com.example.ikimina.service.GroupAdminService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/savings-groups")
@CrossOrigin(origins = "*")
public class SavingsGroupController {

    @Autowired
    private GroupService groupService;
    
    @Autowired
    private GroupAdminService groupAdminService;
    

    @PostMapping
    public ResponseEntity<?> createSavingsGroup(@RequestBody SavingsGroupDTO savingsGroupDTO) {
        try {
            // Check if group name is already taken
            if (groupService.groupExists(savingsGroupDTO.getName())) {
                return ResponseEntity.badRequest().body("Group name is already taken");
            }
            
            // Create the savings group
            SavingsGroup group = new SavingsGroup();
            group.setName(savingsGroupDTO.getName());
            group.setDescription(savingsGroupDTO.getDescription());
            
            // Save the group first
            SavingsGroup savedGroup = groupService.createGroup(group, null);
            
            // Create admin user for this group
            GroupAdminService.GroupAdminRequest adminRequest = new GroupAdminService.GroupAdminRequest(
                savingsGroupDTO.getAdminUserEmail(),
                savingsGroupDTO.getAdminUserFirstName(),
                savingsGroupDTO.getAdminUserLastName(),
                savingsGroupDTO.getAdminUserPhoneNumber()
            );
            
            groupAdminService.createGroupAdmin(savedGroup, adminRequest);
            
            return ResponseEntity.ok(savedGroup);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error creating group: " + e.getMessage());
        }
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getAllGroups() {
        try {
            return ResponseEntity.ok(groupService.getAllGroups());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error retrieving groups: " + e.getMessage());
        }
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or @savingsGroupSecurity.isGroupMember(authentication, #id)")
    public ResponseEntity<?> getGroup(@PathVariable Long id) {
        try {
            return groupService.getGroupById(id)
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error retrieving group: " + e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteGroup(@PathVariable Long id) {
        try {
            groupService.deleteGroup(id);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error deleting group: " + e.getMessage());
        }
    }
}
