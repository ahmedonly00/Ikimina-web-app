package rw.ikimina.groups.internal;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import rw.ikimina.groups.GroupRole;

/** A two-step handover of an office: the holder offers it, the recipient accepts (spec 2.2). */
@Entity
@Table(name = "office_transfers")
public class OfficeTransfer {

    enum Status { PENDING, ACCEPTED, DECLINED, CANCELLED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, updatable = false)
    private UUID publicId;

    @Column(name = "group_id", nullable = false, updatable = false)
    private Long groupId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private GroupRole role;

    @Column(name = "from_membership_id", nullable = false, updatable = false)
    private Long fromMembershipId;

    @Column(name = "to_membership_id", nullable = false, updatable = false)
    private Long toMembershipId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Version
    private long version;

    protected OfficeTransfer() {
    }

    OfficeTransfer(long groupId, GroupRole role, long fromMembershipId, long toMembershipId, Instant now) {
        this.publicId = UUID.randomUUID();
        this.groupId = groupId;
        this.role = role;
        this.fromMembershipId = fromMembershipId;
        this.toMembershipId = toMembershipId;
        this.status = Status.PENDING;
        this.createdAt = now;
    }

    boolean isPending() {
        return status == Status.PENDING;
    }

    void decide(Status outcome, Instant now) {
        status = outcome;
        decidedAt = now;
    }

    UUID getPublicId() {
        return publicId;
    }

    GroupRole getRole() {
        return role;
    }

    Long getFromMembershipId() {
        return fromMembershipId;
    }

    Long getToMembershipId() {
        return toMembershipId;
    }

    Status getStatus() {
        return status;
    }

    Instant getCreatedAt() {
        return createdAt;
    }
}
