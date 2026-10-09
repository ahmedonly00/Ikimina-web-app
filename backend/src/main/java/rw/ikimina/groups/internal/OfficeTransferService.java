package rw.ikimina.groups.internal;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.ikimina.audit.AuditEvent;
import rw.ikimina.audit.AuditService;
import rw.ikimina.groups.GroupRole;
import rw.ikimina.groups.Permission;
import rw.ikimina.groups.PermissionMatrix;
import rw.ikimina.shared.error.ApiException;
import rw.ikimina.shared.error.ErrorCode;
import rw.ikimina.shared.tenancy.TenantContext;

/**
 * Transfer of office (spec 2.2, 17.2), in two audited steps: the current holder offers the
 * office to another member (with a recent password), and that member accepts. Only then do
 * the roles change, together, so the group is never without the office or with two holders.
 */
@Service
class OfficeTransferService {

    record TransferView(UUID transferId, GroupRole role, UUID fromMemberId, UUID toMemberId,
                        OfficeTransfer.Status status, Instant createdAt) {
    }

    private final OfficeTransferRepository transfers;
    private final MembershipRepository memberships;
    private final AuditService audit;
    private final Clock clock;

    OfficeTransferService(OfficeTransferRepository transfers, MembershipRepository memberships, AuditService audit, Clock clock) {
        this.transfers = transfers;
        this.memberships = memberships;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    TransferView offer(GroupRole role, UUID toMemberId) {
        TenantContext.GroupScope scope = TenantContext.requireGroup();
        if (role == null || !role.isOffice()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED);
        }
        Membership holder = memberships.findByGroupIdAndIdForUpdate(scope.groupId(), scope.membershipId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        if (holder.getRole() != role || !holder.isActive()) {
            // Only the person holding an office can hand it over.
            throw new ApiException(ErrorCode.FORBIDDEN);
        }
        Membership recipient = memberships.findByGroupIdAndPublicIdForUpdate(scope.groupId(), toMemberId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        requireEligible(holder, recipient);

        Instant now = clock.instant();
        transfers.findByGroupIdAndRoleAndStatus(scope.groupId(), role, OfficeTransfer.Status.PENDING)
                .ifPresent(previous -> previous.decide(OfficeTransfer.Status.CANCELLED, now));
        transfers.flush();
        OfficeTransfer transfer = transfers.save(new OfficeTransfer(scope.groupId(), role, holder.getId(), recipient.getId(), now));
        audit.record(AuditEvent.of("OFFICE_TRANSFER_OFFERED").entity("office_transfer", transfer.getPublicId())
                .after(Map.of("role", role, "from", holder.getPublicId(), "to", recipient.getPublicId())));
        return view(transfer, holder, recipient);
    }

    @Transactional
    TransferView accept(UUID transferId) {
        TenantContext.GroupScope scope = TenantContext.requireGroup();
        OfficeTransfer transfer = pending(scope.groupId(), transferId);
        if (!transfer.getToMembershipId().equals(scope.membershipId())) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }
        Membership holder = memberships.findByGroupIdAndIdForUpdate(scope.groupId(), transfer.getFromMembershipId()).orElseThrow();
        Membership recipient = memberships.findByGroupIdAndIdForUpdate(scope.groupId(), transfer.getToMembershipId()).orElseThrow();
        if (holder.getRole() != transfer.getRole() || !holder.isActive()) {
            // The offerer no longer holds the office (it changed hands meanwhile); a new offer replaces this one.
            throw new ApiException(ErrorCode.CONCURRENT_MODIFICATION);
        }
        requireEligible(holder, recipient);

        // Vacate first, then fill: the one-holder-per-office index never sees two holders.
        holder.changeRole(GroupRole.MEMBER);
        memberships.flush();
        recipient.changeRole(transfer.getRole());
        transfer.decide(OfficeTransfer.Status.ACCEPTED, clock.instant());
        memberships.flush();
        audit.record(AuditEvent.of("OFFICE_TRANSFERRED").entity("office_transfer", transfer.getPublicId())
                .before(Map.of("holder", holder.getPublicId()))
                .after(Map.of("holder", recipient.getPublicId(), "role", transfer.getRole())));
        return view(transfer, holder, recipient);
    }

    @Transactional
    TransferView decline(UUID transferId) {
        return close(transferId, OfficeTransfer.Status.DECLINED, "OFFICE_TRANSFER_DECLINED", true);
    }

    @Transactional
    TransferView cancel(UUID transferId) {
        return close(transferId, OfficeTransfer.Status.CANCELLED, "OFFICE_TRANSFER_CANCELLED", false);
    }

    /** Pending transfers: all of them for member managers, otherwise only those the caller is part of. */
    @Transactional(readOnly = true)
    List<TransferView> pending() {
        TenantContext.GroupScope scope = TenantContext.requireGroup();
        boolean manager = PermissionMatrix.allows(GroupRole.valueOf(scope.role()), Permission.MEMBER_MANAGE);
        return transfers.findByGroupIdAndStatusOrderByCreatedAtDesc(scope.groupId(), OfficeTransfer.Status.PENDING).stream()
                .filter(t -> manager || t.getFromMembershipId().equals(scope.membershipId())
                        || t.getToMembershipId().equals(scope.membershipId()))
                .map(t -> view(t, memberships.findById(t.getFromMembershipId()).orElseThrow(),
                        memberships.findById(t.getToMembershipId()).orElseThrow()))
                .toList();
    }

    private TransferView close(UUID transferId, OfficeTransfer.Status outcome, String action, boolean byRecipient) {
        TenantContext.GroupScope scope = TenantContext.requireGroup();
        OfficeTransfer transfer = pending(scope.groupId(), transferId);
        Long party = byRecipient ? transfer.getToMembershipId() : transfer.getFromMembershipId();
        if (!party.equals(scope.membershipId())) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }
        transfer.decide(outcome, clock.instant());
        audit.record(AuditEvent.of(action).entity("office_transfer", transfer.getPublicId()));
        return view(transfer, memberships.findById(transfer.getFromMembershipId()).orElseThrow(),
                memberships.findById(transfer.getToMembershipId()).orElseThrow());
    }

    private OfficeTransfer pending(long groupId, UUID transferId) {
        OfficeTransfer transfer = transfers.findByGroupIdAndPublicIdForUpdate(groupId, transferId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        if (!transfer.isPending()) {
            throw new ApiException(ErrorCode.CONCURRENT_MODIFICATION);
        }
        return transfer;
    }

    /** The recipient must be someone else, active, and not already holding an office. */
    private static void requireEligible(Membership holder, Membership recipient) {
        if (recipient.getId().equals(holder.getId())) {
            throw new ApiException(ErrorCode.SELF_MODIFICATION_FORBIDDEN);
        }
        if (!recipient.isActive()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED);
        }
        if (recipient.getRole().isOffice()) {
            throw new ApiException(ErrorCode.OFFICE_OCCUPIED);
        }
    }

    private static TransferView view(OfficeTransfer transfer, Membership from, Membership to) {
        return new TransferView(transfer.getPublicId(), transfer.getRole(), from.getPublicId(), to.getPublicId(),
                transfer.getStatus(), transfer.getCreatedAt());
    }
}
