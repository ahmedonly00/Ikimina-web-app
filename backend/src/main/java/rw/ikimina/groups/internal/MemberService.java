package rw.ikimina.groups.internal;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.ikimina.audit.AuditEvent;
import rw.ikimina.audit.AuditService;
import rw.ikimina.groups.GroupRole;
import rw.ikimina.groups.Permission;
import rw.ikimina.groups.PermissionMatrix;
import rw.ikimina.identity.UserDirectory;
import rw.ikimina.identity.UserDirectory.UserSummary;
import rw.ikimina.shared.error.ApiException;
import rw.ikimina.shared.error.ErrorCode;
import rw.ikimina.shared.security.StepUp;
import rw.ikimina.shared.tenancy.TenantContext;

/**
 * Members of a group: listing, viewing, role and status changes (spec 17.2).
 *
 * <p>Guard rails against privilege escalation (spec 16.10):
 * nobody changes their own role or status; an office (President, Treasurer, Secretary) can
 * only be assigned while vacant - otherwise it changes hands through a transfer of office;
 * an office holder cannot be demoted, suspended or removed until the office is handed over,
 * so a group always keeps its President; removal requires a recent password and a reason.
 */
@Service
class MemberService {

    record MemberView(UUID memberId, String memberNumber, String fullName, String phone, GroupRole role,
                      Membership.Status status, Instant joinedAt) {
    }

    record PageView<T>(List<T> items, int page, int size, long totalItems, int totalPages) {
        static <T> PageView<T> of(Page<?> page, List<T> items) {
            return new PageView<>(items, page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
        }
    }

    private final MembershipRepository memberships;
    private final UserDirectory users;
    private final AuditService audit;
    private final StepUp stepUp;
    private final Clock clock;

    MemberService(MembershipRepository memberships, UserDirectory users, AuditService audit, StepUp stepUp, Clock clock) {
        this.memberships = memberships;
        this.users = users;
        this.audit = audit;
        this.stepUp = stepUp;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    PageView<MemberView> list(Pageable pageable) {
        long groupId = TenantContext.requireGroup().groupId();
        Page<Membership> page = memberships.findByGroupId(groupId, pageable);
        Map<Long, UserSummary> people = users.findByIds(page.getContent().stream().map(Membership::getUserId).toList());
        return PageView.of(page, page.getContent().stream().map(m -> view(m, people.get(m.getUserId()))).toList());
    }

    /** A member may always see their own record; anyone else's needs a group-level permission (spec 5.4 #4). */
    @Transactional(readOnly = true)
    MemberView get(UUID memberId) {
        TenantContext.GroupScope scope = TenantContext.requireGroup();
        Membership member = memberships.findByGroupIdAndPublicId(scope.groupId(), memberId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        boolean self = member.getId().equals(scope.membershipId());
        GroupRole myRole = GroupRole.valueOf(scope.role());
        if (!self && !PermissionMatrix.allows(myRole, Permission.MEMBER_MANAGE)
                && !PermissionMatrix.allows(myRole, Permission.REPORT_VIEW_GROUP)) {
            // A member exists in this group, but the caller may not see it: 403, since they are a member themselves.
            throw new ApiException(ErrorCode.FORBIDDEN);
        }
        return view(member, users.findById(member.getUserId()).orElse(null));
    }

    @Transactional
    MemberView update(UUID memberId, GroupRole newRole, Membership.Status newStatus, String reason) {
        TenantContext.GroupScope scope = TenantContext.requireGroup();
        Membership member = memberships.findByGroupIdAndPublicIdForUpdate(scope.groupId(), memberId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        if (member.getId().equals(scope.membershipId())) {
            throw new ApiException(ErrorCode.SELF_MODIFICATION_FORBIDDEN);
        }
        if (newRole != null && newRole != member.getRole()) {
            changeRole(scope.groupId(), member, newRole);
        }
        if (newStatus != null && newStatus != member.getStatus()) {
            changeStatus(member, newStatus, reason);
        }
        return view(member, users.findById(member.getUserId()).orElse(null));
    }

    private void changeRole(long groupId, Membership member, GroupRole newRole) {
        if (!member.isActive()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED);
        }
        if (member.getRole().isOffice()) {
            throw new ApiException(ErrorCode.OFFICE_TRANSFER_REQUIRED);
        }
        if (newRole.isOffice() && memberships.findByGroupIdAndRoleAndStatus(groupId, newRole, Membership.Status.ACTIVE).isPresent()) {
            throw new ApiException(ErrorCode.OFFICE_OCCUPIED);
        }
        GroupRole before = member.getRole();
        member.changeRole(newRole);
        memberships.flush();   // the one-holder-per-office index settles any race here
        audit.record(AuditEvent.of("MEMBER_ROLE_CHANGED").entity("membership", member.getPublicId())
                .before(Map.of("role", before)).after(Map.of("role", newRole)));
    }

    private void changeStatus(Membership member, Membership.Status newStatus, String reason) {
        if (newStatus != Membership.Status.ACTIVE && newStatus != Membership.Status.SUSPENDED
                && newStatus != Membership.Status.REMOVED) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED);
        }
        if (member.getRole().isOffice() && newStatus != Membership.Status.ACTIVE) {
            throw new ApiException(ErrorCode.OFFICE_TRANSFER_REQUIRED);
        }
        if (member.hasLeft()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED);   // rejoining is by invitation
        }
        if (newStatus == Membership.Status.REMOVED) {
            stepUp.require();
            if (reason == null || reason.isBlank()) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED);
            }
        }
        Membership.Status before = member.getStatus();
        member.changeStatus(newStatus, reason, clock.instant());
        audit.record(AuditEvent.of(newStatus == Membership.Status.REMOVED ? "MEMBER_REMOVED" : "MEMBER_STATUS_CHANGED")
                .entity("membership", member.getPublicId())
                .before(Map.of("status", before)).after(Map.of("status", newStatus)).reason(reason));
    }

    static MemberView view(Membership member, UserSummary person) {
        return new MemberView(member.getPublicId(), member.getMemberNumber(),
                person == null ? null : person.fullName(), person == null ? null : person.phone().e164(),
                member.getRole(), member.getStatus(), member.getJoinedAt());
    }
}
