package rw.ikimina.loans.internal;

import java.net.InetAddress;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import rw.ikimina.groups.GroupRole;

/**
 * One officer's decision on a loan (spec 6.4 loan_approvals, 9.3). Append-only: the database
 * refuses updates and deletes. Keeps the officer's role at the time, their IP and user agent.
 */
@Entity
@Immutable
@Table(name = "loan_approvals")
public class LoanApproval {

    enum Decision { APPROVE, REJECT }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_id", nullable = false, updatable = false)
    private Long groupId;

    @Column(name = "loan_id", nullable = false, updatable = false)
    private Long loanId;

    @Column(name = "approver_membership_id", nullable = false, updatable = false)
    private Long approverMembershipId;

    @Enumerated(EnumType.STRING)
    @Column(name = "approver_role", nullable = false, updatable = false)
    private GroupRole approverRole;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private Decision decision;

    @Column(updatable = false)
    private String comment;

    @JdbcTypeCode(SqlTypes.INET)
    @Column(name = "ip_address", updatable = false)
    private InetAddress ipAddress;

    @Column(name = "user_agent", updatable = false)
    private String userAgent;

    @Column(name = "decided_at", nullable = false, updatable = false)
    private Instant decidedAt;

    protected LoanApproval() {
    }

    LoanApproval(long groupId, long loanId, long approverMembershipId, GroupRole approverRole, Decision decision, String comment,
                 InetAddress ipAddress, String userAgent, Instant now) {
        this.groupId = groupId;
        this.loanId = loanId;
        this.approverMembershipId = approverMembershipId;
        this.approverRole = approverRole;
        this.decision = decision;
        this.comment = comment;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.decidedAt = now;
    }

    Long getApproverMembershipId() {
        return approverMembershipId;
    }

    GroupRole getApproverRole() {
        return approverRole;
    }

    Decision getDecision() {
        return decision;
    }

    String getComment() {
        return comment;
    }

    Instant getDecidedAt() {
        return decidedAt;
    }
}
