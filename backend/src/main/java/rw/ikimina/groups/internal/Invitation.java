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
import rw.ikimina.groups.GroupRole;

/** An invitation to join a group, sent by SMS. Only the token's hash is stored. */
@Entity
@Table(name = "group_invitations")
public class Invitation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, updatable = false)
    private UUID publicId;

    @Column(name = "group_id", nullable = false, updatable = false)
    private Long groupId;

    @Column(nullable = false, updatable = false)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private GroupRole role;

    @Column(name = "token_hash", nullable = false, updatable = false)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "accepted_by")
    private Long acceptedBy;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoked_by")
    private Long revokedBy;

    @Column(name = "created_by", nullable = false, updatable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Invitation() {
    }

    Invitation(long groupId, String phone, GroupRole role, String tokenHash, Instant expiresAt, long createdBy, Instant now) {
        this.publicId = UUID.randomUUID();
        this.groupId = groupId;
        this.phone = phone;
        this.role = role;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.createdBy = createdBy;
        this.createdAt = now;
    }

    boolean isUsable(Instant now) {
        return acceptedAt == null && revokedAt == null && expiresAt.isAfter(now);
    }

    void accept(long userId, Instant now) {
        acceptedAt = now;
        acceptedBy = userId;
    }

    void revoke(long userId, Instant now) {
        if (revokedAt == null && acceptedAt == null) {
            revokedAt = now;
            revokedBy = userId;
        }
    }

    UUID getPublicId() {
        return publicId;
    }

    String getPhone() {
        return phone;
    }

    GroupRole getRole() {
        return role;
    }

    Instant getExpiresAt() {
        return expiresAt;
    }

    Instant getCreatedAt() {
        return createdAt;
    }
}
