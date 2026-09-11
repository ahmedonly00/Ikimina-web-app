package com.example.ikimina.privacy;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * A record that a member agreed to a specific version of a specific purpose.
 *
 * Law 058/2021 requires consent to be specific and informed, and requires the
 * controller to be able to demonstrate it. "The user ticked a box once" is not
 * demonstrable, so each grant records:
 *
 *  <ul>
 *    <li>the purpose, separately - bundling "terms and marketing" into one
 *        checkbox is not specific consent;</li>
 *    <li>the version of the text shown, so a later change to the policy does
 *        not silently re-interpret an old agreement;</li>
 *    <li>when, and from where.</li>
 *  </ul>
 *
 * Withdrawal is a new row with granted=false rather than a deletion, so the
 * history of what was agreed when survives.
 */
@Entity
@Table(
    name = "consent_records",
    indexes = {
        @Index(name = "ix_consent_user_purpose", columnList = "user_id, purpose"),
        @Index(name = "ix_consent_recorded", columnList = "recorded_at")
    }
)
@Getter
@Setter
@NoArgsConstructor
public class ConsentRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 40, updatable = false)
    private ConsentPurpose purpose;

    /** Version of the notice the member actually saw. */
    @Column(name = "policy_version", nullable = false, length = 20, updatable = false)
    private String policyVersion;

    @Column(name = "granted", nullable = false, updatable = false)
    private boolean granted;

    @CreationTimestamp
    @Column(name = "recorded_at", nullable = false, updatable = false)
    private LocalDateTime recordedAt;

    /** Evidence of the request that carried the agreement. */
    @Column(name = "ip_address", length = 45, updatable = false)
    private String ipAddress;

    @Column(name = "user_agent", length = 500, updatable = false)
    private String userAgent;
}