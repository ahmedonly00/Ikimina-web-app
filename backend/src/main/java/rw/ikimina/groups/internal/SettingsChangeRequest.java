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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A proposed change to financial bylaws, waiting for a second officer (spec 5.5, H9). */
@Entity
@Table(name = "settings_change_requests")
public class SettingsChangeRequest {

    enum Status { PENDING, APPLIED, REJECTED, SUPERSEDED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, updatable = false)
    private UUID publicId;

    @Column(name = "group_id", nullable = false, updatable = false)
    private Long groupId;

    @Column(name = "base_version", nullable = false, updatable = false)
    private long baseVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "proposed_settings", nullable = false, updatable = false)
    private String proposedSettings;

    @Column(name = "schema_version", nullable = false, updatable = false)
    private int schemaVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(name = "proposed_by_membership", nullable = false, updatable = false)
    private Long proposedByMembership;

    @Column(name = "decided_by_membership")
    private Long decidedByMembership;

    @Column(name = "decision_reason")
    private String decisionReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Version
    private long version;

    protected SettingsChangeRequest() {
    }

    SettingsChangeRequest(long groupId, long baseVersion, String proposedSettings, int schemaVersion,
                          long proposedByMembership, Instant now) {
        this.publicId = UUID.randomUUID();
        this.groupId = groupId;
        this.baseVersion = baseVersion;
        this.proposedSettings = proposedSettings;
        this.schemaVersion = schemaVersion;
        this.status = Status.PENDING;
        this.proposedByMembership = proposedByMembership;
        this.createdAt = now;
    }

    boolean isPending() {
        return status == Status.PENDING;
    }

    void decide(Status outcome, Long byMembership, String reason, Instant now) {
        status = outcome;
        decidedByMembership = byMembership;
        decisionReason = reason;
        decidedAt = now;
    }

    UUID getPublicId() {
        return publicId;
    }

    long getBaseVersion() {
        return baseVersion;
    }

    String getProposedSettings() {
        return proposedSettings;
    }

    int getSchemaVersion() {
        return schemaVersion;
    }

    Status getStatus() {
        return status;
    }

    Long getProposedByMembership() {
        return proposedByMembership;
    }

    Instant getCreatedAt() {
        return createdAt;
    }
}
