package rw.ikimina.groups.internal;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.ikimina.audit.AuditEvent;
import rw.ikimina.audit.AuditService;
import rw.ikimina.shared.error.ApiException;
import rw.ikimina.shared.error.ErrorCode;
import rw.ikimina.shared.security.StepUp;
import rw.ikimina.shared.tenancy.TenantContext;
import tools.jackson.databind.json.JsonMapper;

/**
 * A group's bylaws (spec 17.2 settings). Non-financial changes apply at once; a change to
 * any financial parameter becomes a proposal that a second, different officer must confirm
 * (spec 5.5, Hard Rule H9), and both steps require a recent password (spec 16.1).
 */
@Service
class SettingsService {

    record PendingChange(UUID changeId, GroupSettingsV1 proposed, UUID proposedBy, Instant proposedAt) {
    }

    record SettingsView(GroupSettingsV1 settings, int schemaVersion, long version, Instant updatedAt,
                        PendingChange pendingChange) {
    }

    /** {@code applied}: the change took effect now; otherwise it awaits confirmation as {@code pendingChange}. */
    record UpdateResult(boolean applied, SettingsView view) {
    }

    private final GroupSettingsRepository rows;
    private final SettingsChangeRequestRepository changes;
    private final MembershipRepository memberships;
    private final AuditService audit;
    private final StepUp stepUp;
    private final JsonMapper json;
    private final Clock clock;

    SettingsService(GroupSettingsRepository rows, SettingsChangeRequestRepository changes, MembershipRepository memberships,
                    AuditService audit, StepUp stepUp, JsonMapper json, Clock clock) {
        this.rows = rows;
        this.changes = changes;
        this.memberships = memberships;
        this.audit = audit;
        this.stepUp = stepUp;
        this.json = json;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    SettingsView view() {
        long groupId = TenantContext.requireGroup().groupId();
        GroupSettingsRow row = rows.findById(groupId).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        return view(groupId, row);
    }

    @Transactional
    UpdateResult update(long expectedVersion, GroupSettingsV1 proposed) {
        TenantContext.GroupScope scope = TenantContext.requireGroup();
        requireNonNegative(proposed);
        GroupSettingsRow row = rows.findByGroupIdForUpdate(scope.groupId()).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        if (row.getVersion() != expectedVersion) {
            throw new ApiException(ErrorCode.CONCURRENT_MODIFICATION);
        }
        GroupSettingsV1 current = parse(row.getSettings());

        if (!current.financialDiffersFrom(proposed)) {
            apply(row, current, proposed, "SETTINGS_CHANGED");
            return new UpdateResult(true, view(scope.groupId(), row));
        }

        stepUp.require();
        // One open proposal at a time: a newer one replaces the older.
        for (SettingsChangeRequest open : changes.findByGroupIdAndStatus(scope.groupId(), SettingsChangeRequest.Status.PENDING)) {
            open.decide(SettingsChangeRequest.Status.SUPERSEDED, scope.membershipId(), "replaced by a newer proposal", clock.instant());
        }
        SettingsChangeRequest change = changes.save(new SettingsChangeRequest(scope.groupId(), row.getVersion(),
                json.writeValueAsString(proposed), GroupSettingsV1.SCHEMA_VERSION, scope.membershipId(), clock.instant()));
        audit.record(AuditEvent.of("SETTINGS_CHANGE_PROPOSED").entity("settings_change", change.getPublicId())
                .before(current).after(proposed));
        return new UpdateResult(false, view(scope.groupId(), row));
    }

    /**
     * @return the new settings, or empty if the settings changed after the proposal was made - the
     *         proposal is then closed as superseded (committed), and the caller reports SETTINGS_CHANGE_STALE
     */
    @Transactional
    Optional<SettingsView> confirm(UUID changeId) {
        TenantContext.GroupScope scope = TenantContext.requireGroup();
        SettingsChangeRequest change = pendingChange(scope.groupId(), changeId);
        if (change.getProposedByMembership().equals(scope.membershipId())) {
            throw new ApiException(ErrorCode.SELF_APPROVAL_FORBIDDEN);
        }
        GroupSettingsRow row = rows.findByGroupIdForUpdate(scope.groupId()).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        if (row.getVersion() != change.getBaseVersion()) {
            change.decide(SettingsChangeRequest.Status.SUPERSEDED, scope.membershipId(), "settings changed after the proposal",
                    clock.instant());
            audit.record(AuditEvent.of("SETTINGS_CHANGE_SUPERSEDED").entity("settings_change", change.getPublicId()));
            return Optional.empty();
        }
        GroupSettingsV1 current = parse(row.getSettings());
        GroupSettingsV1 proposed = parse(change.getProposedSettings());
        change.decide(SettingsChangeRequest.Status.APPLIED, scope.membershipId(), null, clock.instant());
        audit.record(AuditEvent.of("SETTINGS_CHANGE_CONFIRMED").entity("settings_change", change.getPublicId()));
        apply(row, current, proposed, "SETTINGS_CHANGED");
        return Optional.of(view(scope.groupId(), row));
    }

    @Transactional
    SettingsView reject(UUID changeId, String reason) {
        TenantContext.GroupScope scope = TenantContext.requireGroup();
        SettingsChangeRequest change = pendingChange(scope.groupId(), changeId);
        change.decide(SettingsChangeRequest.Status.REJECTED, scope.membershipId(), reason, clock.instant());
        audit.record(AuditEvent.of("SETTINGS_CHANGE_REJECTED").entity("settings_change", change.getPublicId()).reason(reason));
        return view(scope.groupId(), rows.findById(scope.groupId()).orElseThrow());
    }

    private SettingsChangeRequest pendingChange(long groupId, UUID changeId) {
        SettingsChangeRequest change = changes.findByGroupIdAndPublicIdForUpdate(groupId, changeId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        if (!change.isPending()) {
            throw new ApiException(ErrorCode.SETTINGS_CHANGE_STALE);
        }
        return change;
    }

    private void apply(GroupSettingsRow row, GroupSettingsV1 before, GroupSettingsV1 after, String action) {
        row.replace(json.writeValueAsString(after), GroupSettingsV1.SCHEMA_VERSION, clock.instant());
        rows.flush();
        audit.record(AuditEvent.of(action).entity("group_settings", TenantContext.requireGroup().groupPublicId())
                .before(before).after(after));
    }

    private SettingsView view(long groupId, GroupSettingsRow row) {
        PendingChange pending = changes.findByGroupIdAndStatus(groupId, SettingsChangeRequest.Status.PENDING).stream()
                .findFirst()
                .map(change -> new PendingChange(change.getPublicId(), parse(change.getProposedSettings()),
                        memberships.findById(change.getProposedByMembership()).map(Membership::getPublicId).orElse(null),
                        change.getCreatedAt()))
                .orElse(null);
        return new SettingsView(parse(row.getSettings()), row.getSchemaVersion(), row.getVersion(), row.getUpdatedAt(), pending);
    }

    private GroupSettingsV1 parse(String stored) {
        return json.readValue(stored, GroupSettingsV1.class);
    }

    private static void requireNonNegative(GroupSettingsV1 settings) {
        if (settings.exitFee().isNegative()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED);
        }
    }
}
