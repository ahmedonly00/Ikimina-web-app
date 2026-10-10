package rw.ikimina.loans.internal;

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

/** A proposed change to a loan product's money terms, waiting for a second officer. */
@Entity
@Table(name = "loan_product_change_requests")
public class LoanProductChangeRequest {

    enum Status { PENDING, APPLIED, REJECTED, SUPERSEDED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, updatable = false)
    private UUID publicId;

    @Column(name = "group_id", nullable = false, updatable = false)
    private Long groupId;

    @Column(name = "product_id", nullable = false, updatable = false)
    private Long productId;

    @Column(name = "base_version", nullable = false, updatable = false)
    private long baseVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "proposed_terms", nullable = false, updatable = false)
    private String proposedTerms;

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

    protected LoanProductChangeRequest() {
    }

    LoanProductChangeRequest(long groupId, long productId, long baseVersion, String proposedTerms, long proposedBy, Instant now) {
        this.publicId = UUID.randomUUID();
        this.groupId = groupId;
        this.productId = productId;
        this.baseVersion = baseVersion;
        this.proposedTerms = proposedTerms;
        this.status = Status.PENDING;
        this.proposedByMembership = proposedBy;
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

    Long getProductId() {
        return productId;
    }

    long getBaseVersion() {
        return baseVersion;
    }

    String getProposedTerms() {
        return proposedTerms;
    }

    Long getProposedByMembership() {
        return proposedByMembership;
    }

    Instant getCreatedAt() {
        return createdAt;
    }
}
