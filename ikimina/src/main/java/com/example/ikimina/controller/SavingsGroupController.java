package com.example.ikimina.controller;

import com.example.ikimina.dto.GroupCreatedDTO;
import com.example.ikimina.dto.PublicGroupDTO;
import com.example.ikimina.dto.SavingsGroupDTO;
import com.example.ikimina.exception.BusinessRuleException;
import com.example.ikimina.exception.ResourceNotFoundException;
import com.example.ikimina.model.SavingsGroup;
import com.example.ikimina.repository.SavingsGroupRepository;
import com.example.ikimina.service.GroupAdminService;
import com.example.ikimina.service.GroupService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/savings-groups")
public class SavingsGroupController {

    @Autowired
    private GroupService groupService;

    @Autowired
    private GroupAdminService groupAdminService;

    @Autowired
    private SavingsGroupRepository savingsGroupRepository;

    /**
     * Unauthenticated group picker for the login and register screens. Returns
     * id and name only; the full listing below stays restricted.
     */
    @GetMapping("/public")
    public ResponseEntity<List<PublicGroupDTO>> getPublicGroups() {
        List<PublicGroupDTO> groups = savingsGroupRepository.findByIsActiveTrue().stream()
                .map(g -> new PublicGroupDTO(g.getId(), g.getName()))
                .toList();
        return ResponseEntity.ok(groups);
    }

    /**
     * Creating a group also provisions a ROLE_GROUP_ADMIN account, so this is a
     * privilege-granting operation and must stay super-admin only.
     */
    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Transactional
    public ResponseEntity<GroupCreatedDTO> createSavingsGroup(
            @Valid @RequestBody SavingsGroupDTO savingsGroupDTO) {

        if (groupService.groupExists(savingsGroupDTO.getName())) {
            throw new BusinessRuleException("Group name is already taken");
        }

        SavingsGroup group = new SavingsGroup();
        group.setName(savingsGroupDTO.getName());
        group.setDescription(savingsGroupDTO.getDescription());

        SavingsGroup savedGroup = groupService.createGroup(group, null);

        GroupAdminService.GroupAdminRequest adminRequest = new GroupAdminService.GroupAdminRequest(
                savingsGroupDTO.getAdminUserEmail(),
                savingsGroupDTO.getAdminUserFirstName(),
                savingsGroupDTO.getAdminUserLastName(),
                savingsGroupDTO.getAdminUserPhoneNumber()
        );

        GroupAdminService.CreatedGroupAdmin createdAdmin =
                groupAdminService.createGroupAdmin(savedGroup, adminRequest);

        // The temporary password is returned once, here, and never logged.
        return ResponseEntity.ok(new GroupCreatedDTO(
                savedGroup.getId(),
                savedGroup.getName(),
                savedGroup.getDescription(),
                createdAdmin.email(),
                createdAdmin.temporaryPassword()
        ));
    }

    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<List<SavingsGroup>> getAllGroups() {
        return ResponseEntity.ok(groupService.getAllGroups());
    }

    @GetMapping("/{id}")
    @PreAuthorize("@savingsGroupSecurity.canAdministerGroup(authentication, #id) "
            + "or @savingsGroupSecurity.isGroupMember(authentication, #id)")
    public ResponseEntity<SavingsGroup> getGroup(@PathVariable Long id) {
        return ResponseEntity.ok(groupService.getGroupById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Savings group not found: " + id)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Void> deleteGroup(@PathVariable Long id) {
        groupService.deleteGroup(id);
        return ResponseEntity.noContent().build();
    }
}
