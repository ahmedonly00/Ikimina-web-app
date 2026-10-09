package rw.ikimina.groups.internal;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.ikimina.audit.AuditEvent;
import rw.ikimina.audit.AuditService;
import rw.ikimina.groups.GroupRole;
import rw.ikimina.shared.error.ApiException;
import rw.ikimina.shared.error.ErrorCode;
import rw.ikimina.shared.phone.PhoneNumber;
import rw.ikimina.shared.security.CurrentUser;
import rw.ikimina.shared.tenancy.TenantContext;
import rw.ikimina.shared.tenancy.TenantSession;
import tools.jackson.databind.json.JsonMapper;

/** Creating groups, their profile, and "my groups" (spec 17.1, 17.2). */
@Service
class GroupService {

    record GroupView(UUID groupId, String name, String registrationNumber, String phone, String email,
                     String province, String district, String sector, String cell, String village,
                     String status, Instant createdAt, GroupRole myRole, UUID myMemberId, long activeMembers) {
    }

    record MyGroup(UUID groupId, String name, UUID memberId, String memberNumber, GroupRole role) {
    }

    private record CreatedIds(long groupId, UUID groupPublicId) {
    }

    private final JdbcTemplate jdbc;
    private final TenantSession tenantSession;
    private final GroupRepository groups;
    private final GroupSettingsRepository settings;
    private final MembershipRepository memberships;
    private final AuditService audit;
    private final JsonMapper json;
    private final Clock clock;

    GroupService(JdbcTemplate jdbc, TenantSession tenantSession, GroupRepository groups, GroupSettingsRepository settings,
                 MembershipRepository memberships, AuditService audit, JsonMapper json, Clock clock) {
        this.jdbc = jdbc;
        this.tenantSession = tenantSession;
        this.groups = groups;
        this.settings = settings;
        this.memberships = memberships;
        this.audit = audit;
        this.json = json;
        this.clock = clock;
    }

    /**
     * Creates a group with the caller as its President (spec 17.2). The subscription trial
     * starts here too once billing exists (Phase 7).
     */
    @Transactional
    GroupView create(Group.Profile profile) {
        long userId = CurrentUser.require().id();
        Instant now = clock.instant();
        CreatedIds ids = jdbc.queryForObject("SELECT group_id, group_public_id FROM create_group(?)",
                (rs, n) -> new CreatedIds(rs.getLong(1), rs.getObject(2, UUID.class)), profile.name().trim());
        if (ids == null) {
            throw new IllegalStateException("create_group returned nothing");
        }
        // The group exists now: act inside it, as its founding President.
        tenantSession.enterGroup(new TenantContext.GroupScope(ids.groupId(), ids.groupPublicId(), null, GroupRole.PRESIDENT.name()));

        Group group = groups.findById(ids.groupId()).orElseThrow();
        group.updateProfile(normalised(profile));
        settings.save(new GroupSettingsRow(ids.groupId(), json.writeValueAsString(GroupSettingsV1.defaults()),
                GroupSettingsV1.SCHEMA_VERSION, now));
        Membership president = memberships.save(Membership.active(ids.groupId(), userId, MemberNumbers.format(1),
                GroupRole.PRESIDENT, now));
        memberships.flush();
        tenantSession.enterGroup(new TenantContext.GroupScope(ids.groupId(), ids.groupPublicId(), president.getId(),
                GroupRole.PRESIDENT.name()));

        audit.record(AuditEvent.of("GROUP_CREATED").entity("group", ids.groupPublicId()).after(group.profile()));
        return view(group, president);
    }

    @Transactional(readOnly = true)
    GroupView current() {
        TenantContext.GroupScope scope = TenantContext.requireGroup();
        Group group = groups.findById(scope.groupId()).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        Membership me = memberships.findById(scope.membershipId()).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        return view(group, me);
    }

    @Transactional
    GroupView updateProfile(Group.Profile changes) {
        TenantContext.GroupScope scope = TenantContext.requireGroup();
        Group group = groups.findById(scope.groupId()).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        Group.Profile before = group.profile();
        group.updateProfile(normalised(changes));
        audit.record(AuditEvent.of("GROUP_PROFILE_UPDATED").entity("group", group.getPublicId())
                .before(before).after(group.profile()));
        Membership me = memberships.findById(scope.membershipId()).orElseThrow();
        return view(group, me);
    }

    /** Groups the caller actively belongs to, across all groups (spec 17.1 GET /me/groups). */
    @Transactional(readOnly = true)
    List<MyGroup> myGroups() {
        long userId = CurrentUser.require().id();
        List<Membership> mine = memberships.findByUserIdAndStatus(userId, Membership.Status.ACTIVE);
        Map<Long, Group> byId = groups.findByIdIn(mine.stream().map(Membership::getGroupId).toList()).stream()
                .collect(Collectors.toMap(Group::getId, Function.identity()));
        return mine.stream()
                .filter(m -> byId.containsKey(m.getGroupId()))
                .map(m -> new MyGroup(byId.get(m.getGroupId()).getPublicId(), byId.get(m.getGroupId()).getName(),
                        m.getPublicId(), m.getMemberNumber(), m.getRole()))
                .sorted((a, b) -> a.name().compareToIgnoreCase(b.name()))
                .toList();
    }

    private GroupView view(Group group, Membership me) {
        Group.Profile p = group.profile();
        return new GroupView(group.getPublicId(), p.name(), p.registrationNumber(), p.phone(), p.email(), p.province(),
                p.district(), p.sector(), p.cell(), p.village(), group.getStatus(), group.getCreatedAt(), me.getRole(),
                me.getPublicId(), memberships.countByGroupIdAndStatus(group.getId(), Membership.Status.ACTIVE));
    }

    /** The group's contact phone, when given, is normalised like any other phone number. */
    private static Group.Profile normalised(Group.Profile profile) {
        String phone = profile.phone() == null || profile.phone().isBlank() ? profile.phone()
                : PhoneNumber.parse(profile.phone()).e164();
        String name = profile.name() == null ? null : profile.name().trim();
        return new Group.Profile(name, profile.registrationNumber(), phone, profile.email(), profile.province(),
                profile.district(), profile.sector(), profile.cell(), profile.village());
    }
}
