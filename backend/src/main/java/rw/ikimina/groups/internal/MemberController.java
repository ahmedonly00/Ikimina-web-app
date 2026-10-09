package rw.ikimina.groups.internal;

import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import rw.ikimina.groups.GroupAccess;
import rw.ikimina.groups.GroupRole;

/** A group's members (spec 17.2). */
@RestController
@RequestMapping("/api/v1/groups/{groupId}/members")
class MemberController {

    /** Either or both may be given. Removing someone needs a reason and a recent password. */
    record UpdateMemberRequest(GroupRole role, Membership.Status status, @Size(max = 500) String reason) {
    }

    private final MemberService members;

    MemberController(MemberService members) {
        this.members = members;
    }

    @GetMapping
    @PreAuthorize("@perm.hasAny(#groupId, 'MEMBER_MANAGE', 'REPORT_VIEW_GROUP')")
    MemberService.PageView<MemberService.MemberView> list(@PathVariable UUID groupId,
                                                         @RequestParam(defaultValue = "0") @Min(0) int page,
                                                         @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size) {
        // Fixed order: clients do not choose sort fields, so they cannot sort by internal columns.
        return members.list(PageRequest.of(page, size, Sort.by("memberNumber")));
    }

    /** Own record for anyone; other people's need MEMBER_MANAGE or REPORT_VIEW_GROUP (checked in the service). */
    @GetMapping("/{memberId}")
    @GroupAccess.AnyMember
    MemberService.MemberView get(@PathVariable UUID groupId, @PathVariable UUID memberId) {
        return members.get(memberId);
    }

    @PatchMapping("/{memberId}")
    @PreAuthorize("@perm.has(#groupId, 'MEMBER_MANAGE')")
    MemberService.MemberView update(@PathVariable UUID groupId, @PathVariable UUID memberId,
                                    @Valid @RequestBody UpdateMemberRequest request) {
        return members.update(memberId, request.role(), request.status(), request.reason());
    }
}
