package rw.ikimina.savings.internal;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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
import rw.ikimina.shared.money.Money;

/** A named savings pool of a group - Ubwizigame, Ingoboka, shares... (spec 8.1). */
@Entity
@Table(name = "savings_buckets")
public class SavingsBucket {

    enum Type { SAVINGS, SOCIAL_FUND, SHARES }

    enum Frequency { WEEKLY, BIWEEKLY, MONTHLY, PER_MEETING, ADHOC }

    enum CycleType { FIXED_TERM, ROLLING }

    enum Status { ACTIVE, CLOSED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, updatable = false)
    private UUID publicId;

    @Column(name = "group_id", nullable = false, updatable = false)
    private Long groupId;

    @Column(nullable = false)
    private String name;

    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "bucket_type", nullable = false, updatable = false)
    private Type bucketType;

    @Column(name = "is_mandatory", nullable = false)
    private boolean mandatory;

    @Column(name = "minimum_contribution", nullable = false)
    private BigDecimal minimumContribution;

    @Enumerated(EnumType.STRING)
    @Column(name = "contribution_frequency", nullable = false)
    private Frequency contributionFrequency;

    @Enumerated(EnumType.STRING)
    @Column(name = "cycle_type", nullable = false, updatable = false)
    private CycleType cycleType;

    @Column(name = "start_date", nullable = false, updatable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(nullable = false)
    private boolean withdrawable;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "late_penalty_rule")
    private String latePenaltyRule;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Version
    private long version;

    protected SavingsBucket() {
    }

    SavingsBucket(long groupId, String name, String description, Type type, CycleType cycleType, LocalDate startDate,
                  BucketTerms terms, String penaltyJson, Instant now) {
        this.publicId = UUID.randomUUID();
        this.groupId = groupId;
        this.name = name;
        this.description = description;
        this.bucketType = type;
        this.cycleType = cycleType;
        this.startDate = startDate;
        this.status = Status.ACTIVE;
        this.createdAt = now;
        applyTerms(terms, penaltyJson);
    }

    void applyTerms(BucketTerms terms, String penaltyJson) {
        mandatory = terms.mandatory();
        minimumContribution = terms.minimumContribution().toStorageAmount();
        contributionFrequency = terms.contributionFrequency();
        withdrawable = terms.withdrawable();
        endDate = terms.endDate();
        latePenaltyRule = penaltyJson;
    }

    void rename(String newName, String newDescription) {
        if (newName != null) {
            name = newName;
        }
        if (newDescription != null) {
            description = newDescription.isBlank() ? null : newDescription;
        }
    }

    void changeStatus(Status newStatus) {
        status = newStatus;
    }

    boolean isActive() {
        return status == Status.ACTIVE;
    }

    /** Obligations are generated only for mandatory buckets with a fixed calendar (spec 8.1). */
    boolean generatesObligations() {
        return isActive() && mandatory && minimumContribution.signum() > 0
                && (contributionFrequency == Frequency.WEEKLY || contributionFrequency == Frequency.BIWEEKLY
                || contributionFrequency == Frequency.MONTHLY);
    }

    Long getId() {
        return id;
    }

    UUID getPublicId() {
        return publicId;
    }

    String getName() {
        return name;
    }

    String getDescription() {
        return description;
    }

    Long getGroupId() {
        return groupId;
    }

    Type getBucketType() {
        return bucketType;
    }

    boolean isMandatory() {
        return mandatory;
    }

    Money getMinimumContribution() {
        return Money.of(minimumContribution);
    }

    Frequency getContributionFrequency() {
        return contributionFrequency;
    }

    CycleType getCycleType() {
        return cycleType;
    }

    LocalDate getStartDate() {
        return startDate;
    }

    LocalDate getEndDate() {
        return endDate;
    }

    boolean isWithdrawable() {
        return withdrawable;
    }

    String getLatePenaltyRule() {
        return latePenaltyRule;
    }

    Status getStatus() {
        return status;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    long getVersion() {
        return version;
    }
}
