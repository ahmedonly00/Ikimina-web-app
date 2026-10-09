package rw.ikimina.groups.internal;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import rw.ikimina.groups.GroupAccess;
import rw.ikimina.groups.GroupRole;

/** Invitations to join a group (spec 17.2). */
@RestController
@RequestMapping("/api/v1/groups/{groupId}/invitations")
class InvitationController {

    record InviteRequest(@NotBlank @Size(max = 32) String phone, @NotNull GroupRole role) {
    }

    record AcceptRequest(@NotBlank @Size(max = 128) String token) {
    }

    private final InvitationService invitations;

    InvitationController(InvitationService invitations) {
        this.invitations = invitations;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@perm.has(#groupId, 'MEMBER_MANAGE')")
    InvitationService.InvitationView invite(@PathVariable UUID groupId, @Valid @RequestBody InviteRequest request) {
        return invitations.invite(request.phone(), request.role());
    }

    @GetMapping
    @PreAuthorize("@perm.has(#groupId, 'MEMBER_MANAGE')")
    List<InvitationService.InvitationView> pending(@PathVariable UUID groupId) {
        return invitations.pending();
    }

    @PostMapping("/{invitationId}/revoke")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@perm.has(#groupId, 'MEMBER_MANAGE')")
    void revoke(@PathVariable UUID groupId, @PathVariable UUID invitationId) {
        invitations.revoke(invitationId);
    }

    /** The invited person is not a member yet; the token from the SMS link is their credential. */
    @PostMapping("/accept")
    @GroupAccess.NonMemberAllowed
    InvitationService.Accepted accept(@PathVariable UUID groupId, @Valid @RequestBody AcceptRequest request) {
        return invitations.accept(groupId, request.token());
    }
}
