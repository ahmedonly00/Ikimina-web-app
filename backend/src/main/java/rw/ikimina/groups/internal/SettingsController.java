package rw.ikimina.groups.internal;

import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import rw.ikimina.groups.GroupAccess;
import rw.ikimina.shared.error.ApiException;
import rw.ikimina.shared.error.ErrorCode;
import rw.ikimina.shared.security.RequiresRecentAuthentication;

/**
 * A group's bylaws (spec 17.2). A PUT that touches financial parameters returns 202: it is
 * now a proposal that a second officer confirms.
 */
@RestController
@RequestMapping("/api/v1/groups/{groupId}/settings")
class SettingsController {

    /** {@code version} is the version read before editing; a stale one is refused rather than overwriting someone else's change. */
    record UpdateSettingsRequest(@NotNull Long version, @NotNull @Valid GroupSettingsV1 settings) {
    }

    record RejectRequest(@NotBlank @Size(max = 500) String reason) {
    }

    private final SettingsService settings;

    SettingsController(SettingsService settings) {
        this.settings = settings;
    }

    /** Every member may read the bylaws they are bound by. */
    @GetMapping
    @GroupAccess.AnyMember
    SettingsService.SettingsView get(@PathVariable UUID groupId) {
        return settings.view();
    }

    @PutMapping
    @PreAuthorize("@perm.has(#groupId, 'SETTINGS_EDIT')")
    ResponseEntity<SettingsService.SettingsView> update(@PathVariable UUID groupId,
                                                       @Valid @RequestBody UpdateSettingsRequest request) {
        SettingsService.UpdateResult result = settings.update(request.version(), request.settings());
        return ResponseEntity.status(result.applied() ? HttpStatus.OK : HttpStatus.ACCEPTED).body(result.view());
    }

    @PostMapping("/changes/{changeId}/confirm")
    @PreAuthorize("@perm.has(#groupId, 'SETTINGS_EDIT')")
    @RequiresRecentAuthentication
    SettingsService.SettingsView confirm(@PathVariable UUID groupId, @PathVariable UUID changeId) {
        return settings.confirm(changeId).orElseThrow(() -> new ApiException(ErrorCode.SETTINGS_CHANGE_STALE));
    }

    @PostMapping("/changes/{changeId}/reject")
    @PreAuthorize("@perm.has(#groupId, 'SETTINGS_EDIT')")
    SettingsService.SettingsView reject(@PathVariable UUID groupId, @PathVariable UUID changeId,
                                        @Valid @RequestBody RejectRequest request) {
        return settings.reject(changeId, request.reason());
    }
}
