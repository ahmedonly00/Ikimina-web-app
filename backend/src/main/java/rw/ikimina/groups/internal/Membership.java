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

/** A person's membership of one group, with their role there (spec 2.2). Never deleted. */
@Entity
@Table(name = "group_memberships")
public class Membership {

    enum Status { INVITED, ACTIVE, SUSPENDED, LEFT, REMOVED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, updatable = false)
    private UUID publicId;

    @Column(name = "group_id", nullable = false, updatable = false)
    private Long groupId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "member_number", nullable = false, updatable = false)
    private String memberNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GroupRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(name = "joined_at")
    private Instant joinedAt;

    @Column(name = "left_at")
    private Instant leftAt;

    @Column(name = "left_reason")
    private String leftReason;

    @Version
    private long version;

    protected Membership() {
    }

    static Membership active(long groupId, long userId, String memberNumber, GroupRole role, Instant now) {
        Membership membership = new Membership();
        membership.publicId = UUID.randomUUID();
        membership.groupId = groupId;
        membership.userId = userId;
        membership.memberNumber = memberNumber;
        membership.role = role;
        membership.status = Status.ACTIVE;
        membership.joinedAt = now;
        return membership;
    }

    boolean isActive() {
        return status == Status.ACTIVE;
    }

    boolean hasLeft() {
        return status == Status.LEFT || status == Status.REMOVED;
    }

    void rejoin(GroupRole newRole, Instant now) {
        role = newRole;
        status = Status.ACTIVE;
        joinedAt = now;
        leftAt = null;
        leftReason = null;
    }

    void changeRole(GroupRole newRole) {
        role = newRole;
    }

    void changeStatus(Status newStatus, String reason, Instant now) {
        status = newStatus;
        if (newStatus == Status.REMOVED || newStatus == Status.LEFT) {
            leftAt = now;
            leftReason = reason;
        }
    }

    Long getId() {
        return id;
    }

    UUID getPublicId() {
        return publicId;
    }

    Long getGroupId() {
        return groupId;
    }

    Long getUserId() {
        return userId;
    }

    String getMemberNumber() {
        return memberNumber;
    }

    GroupRole getRole() {
        return role;
    }

    Status getStatus() {
        return status;
    }

    Instant getJoinedAt() {
        return joinedAt;
    }
}
