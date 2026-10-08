package com.example.ikimina.model;

import java.time.LocalDateTime;

import com.example.ikimina.enums.MembershipRequestStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Someone asking to join a group, and the administrator's decision.
 *
 * Kept as its own record rather than a flag on the membership join table so
 * that a refusal is still visible afterwards: who asked, when, who decided and
 * why. Approving is what creates the membership - the request never grants
 * access on its own.
 */
@Entity
@Table(name = "membership_requests")
@Getter
@Setter
@NoArgsConstructor
public class MembershipRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "savings_group_id", nullable = false)
    private SavingsGroup savingsGroup;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MembershipRequestStatus status = MembershipRequestStatus.PENDING;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    /** The administrator who decided; null while pending. */
    @Column(name = "decided_by")
    private Long decidedBy;

    @Column(name = "decision_note", length = 500)
    private String decisionNote;
}
