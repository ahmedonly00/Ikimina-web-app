package com.example.ikimina.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.ikimina.dto.MembershipRequestDTO;
import com.example.ikimina.dto.RegistrationRequest;
import com.example.ikimina.model.User;
import com.example.ikimina.security.CustomUserDetails;
import com.example.ikimina.service.GroupMembershipService;
import com.example.ikimina.service.UserService;

import jakarta.validation.Valid;

/**
 * Administration of who belongs to a group.
 *
 * Every route here is checked at the object level with
 * {@code canAdministerGroup} rather than by role alone, so holding
 * GROUP_ADMIN does not let someone manage a group that is not theirs. Routes
 * addressed by request id resolve the owning group first and check that.
 */
@RestController
@RequestMapping("/api/savings-groups/{groupId}")
public class GroupMembershipController {

    private final GroupMembershipService membershipService;
    private final UserService userService;

    public GroupMembershipController(GroupMembershipService membershipService,
                                     UserService userService) {
        this.membershipService = membershipService;
        this.userService = userService;
    }

    // ---- Adding a member directly -------------------------------------

    /**
     * Add a member to this group.
     *
     * The group comes from the path and is authorised against the caller, so
     * unlike public registration nothing in the body can redirect the new
     * account into a different group.
     */
    @PostMapping("/members")
    @PreAuthorize("@savingsGroupSecurity.canAdministerGroup(authentication, #groupId)")
    public ResponseEntity<Map<String, Object>> addMember(
            @PathVariable Long groupId,
            @Valid @RequestBody RegistrationRequest request) {

        // registerMember resolves and validates the group itself.
        User user = userService.registerMember(request, groupId);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("userId", user.getId());
        body.put("memberNumber", user.getMemberNumber());
        body.put("email", user.getEmail());
        return ResponseEntity.ok(body);
    }

    // ---- Invite code ---------------------------------------------------

    @GetMapping("/join-code")
    @PreAuthorize("@savingsGroupSecurity.canAdministerGroup(authentication, #groupId)")
    public ResponseEntity<Map<String, Object>> joinCode(@PathVariable Long groupId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("joinCode", membershipService.currentJoinCode(groupId));
        return ResponseEntity.ok(body);
    }

    /** Generate a code, or replace one that has been shared too widely. */
    @PostMapping("/join-code")
    @PreAuthorize("@savingsGroupSecurity.canAdministerGroup(authentication, #groupId)")
    public ResponseEntity<Map<String, Object>> rotateJoinCode(@PathVariable Long groupId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("joinCode", membershipService.rotateJoinCode(groupId));
        return ResponseEntity.ok(body);
    }

    @DeleteMapping("/join-code")
    @PreAuthorize("@savingsGroupSecurity.canAdministerGroup(authentication, #groupId)")
    public ResponseEntity<Void> clearJoinCode(@PathVariable Long groupId) {
        membershipService.clearJoinCode(groupId);
        return ResponseEntity.noContent().build();
    }

    // ---- Requests to join ----------------------------------------------

    @GetMapping("/join-requests")
    @PreAuthorize("@savingsGroupSecurity.canAdministerGroup(authentication, #groupId)")
    public ResponseEntity<List<MembershipRequestDTO>> pending(@PathVariable Long groupId) {
        return ResponseEntity.ok(membershipService.pendingRequests(groupId));
    }

    @PostMapping("/join-requests/{requestId}/approve")
    @PreAuthorize("@savingsGroupSecurity.canAdministerGroup(authentication, #groupId)"
            + " and @savingsGroupSecurity.requestBelongsToGroup(#requestId, #groupId)")
    public ResponseEntity<MembershipRequestDTO> approve(
            @PathVariable Long groupId,
            @PathVariable Long requestId,
            @AuthenticationPrincipal CustomUserDetails principal) {
        return ResponseEntity.ok(membershipService.approve(requestId, principal.getUserId()));
    }

    @PostMapping("/join-requests/{requestId}/reject")
    @PreAuthorize("@savingsGroupSecurity.canAdministerGroup(authentication, #groupId)"
            + " and @savingsGroupSecurity.requestBelongsToGroup(#requestId, #groupId)")
    public ResponseEntity<MembershipRequestDTO> reject(
            @PathVariable Long groupId,
            @PathVariable Long requestId,
            @RequestBody(required = false) Map<String, String> body,
            @AuthenticationPrincipal CustomUserDetails principal) {
        String note = body == null ? null : body.get("note");
        return ResponseEntity.ok(membershipService.reject(requestId, principal.getUserId(), note));
    }
}
