package rw.ikimina.groups.internal;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import rw.ikimina.groups.GroupAccess;

/** Groups and the caller's groups (spec 17.1, 17.2). */
@RestController
class GroupController {

    record CreateGroupRequest(@NotBlank @Size(max = 200) String name,
                              @Size(max = 100) String registrationNumber,
                              @Size(max = 32) String phone,
                              @Email @Size(max = 255) String email,
                              @Size(max = 80) String province,
                              @Size(max = 80) String district,
                              @Size(max = 80) String sector,
                              @Size(max = 80) String cell,
                              @Size(max = 80) String village) {

        Group.Profile profile() {
            return new Group.Profile(name, registrationNumber, phone, email, province, district, sector, cell, village);
        }
    }

    /** Every field optional: null leaves it unchanged, an empty string clears it. */
    record UpdateGroupRequest(@Size(min = 1, max = 200) String name,
                              @Size(max = 100) String registrationNumber,
                              @Size(max = 32) String phone,
                              @Email @Size(max = 255) String email,
                              @Size(max = 80) String province,
                              @Size(max = 80) String district,
                              @Size(max = 80) String sector,
                              @Size(max = 80) String cell,
                              @Size(max = 80) String village) {

        Group.Profile profile() {
            return new Group.Profile(name, registrationNumber, phone, email, province, district, sector, cell, village);
        }
    }

    private final GroupService groups;

    GroupController(GroupService groups) {
        this.groups = groups;
    }

    /** Any signed-in person can start a group and becomes its President. */
    @PostMapping("/api/v1/groups")
    @ResponseStatus(HttpStatus.CREATED)
    GroupService.GroupView create(@Valid @RequestBody CreateGroupRequest request) {
        return groups.create(request.profile());
    }

    @GetMapping("/api/v1/me/groups")
    List<GroupService.MyGroup> myGroups() {
        return groups.myGroups();
    }

    @GetMapping("/api/v1/groups/{groupId}")
    @GroupAccess.AnyMember
    GroupService.GroupView get(@PathVariable UUID groupId) {
        return groups.current();
    }

    @PatchMapping("/api/v1/groups/{groupId}")
    @PreAuthorize("@perm.has(#groupId, 'SETTINGS_EDIT')")
    GroupService.GroupView update(@PathVariable UUID groupId, @Valid @RequestBody UpdateGroupRequest request) {
        return groups.updateProfile(request.profile());
    }
}
