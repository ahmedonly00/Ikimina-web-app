package rw.ikimina.savings.internal;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.ikimina.audit.AuditEvent;
import rw.ikimina.audit.AuditService;
import rw.ikimina.groups.GroupMembers;
import rw.ikimina.shared.error.ApiException;
import rw.ikimina.shared.error.ErrorCode;
import rw.ikimina.shared.money.Money;
import rw.ikimina.shared.security.StepUp;
import rw.ikimina.shared.tenancy.TenantContext;
import tools.jackson.databind.json.JsonMapper;

/**
 * Savings buckets (spec 8.1, 17.3). Managed by SETTINGS_EDIT holders; once a bucket exists, a
 * change to its money terms needs a recent password and a second officer's confirmation
 * (owner decision, Phase 2), because those terms decide what every member owes.
 */
@Service
class BucketService {

    record NewBucket(String name, String description, SavingsBucket.Type type, SavingsBucket.CycleType cycleType,
                     LocalDate startDate, BucketTerms terms) {
    }

    record PendingChange(UUID changeId, BucketTerms proposed, UUID proposedBy, Instant proposedAt) {
    }

    record BucketView(UUID bucketId, String name, String description, SavingsBucket.Type type, SavingsBucket.CycleType cycleType,
                      LocalDate startDate, BucketTerms terms, SavingsBucket.Status status, long version,
                      PendingChange pendingChange) {
    }

    record UpdateResult(boolean applied, BucketView view) {
    }

    private final SavingsBucketRepository buckets;
    private final BucketChangeRequestRepository changes;
    private final ObligationGenerator generator;
    private final GroupMembers members;
    private final AuditService audit;
    private final StepUp stepUp;
    private final JsonMapper json;
    private final Clock clock;

    BucketService(SavingsBucketRepository buckets, BucketChangeRequestRepository changes, ObligationGenerator generator,
                  GroupMembers members, AuditService audit, StepUp stepUp, JsonMapper json, Clock clock) {
        this.buckets = buckets;
        this.changes = changes;
        this.generator = generator;
        this.members = members;
        this.audit = audit;
        this.stepUp = stepUp;
        this.json = json;
        this.clock = clock;
    }

    @Transactional
    BucketView create(NewBucket request) {
        long groupId = TenantContext.requireGroup().groupId();
        String name = request.name().trim();
        if (buckets.existsByGroupIdAndNameIgnoreCase(groupId, name)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED);
        }
        validate(request.type(), request.cycleType(), request.startDate(), request.terms());
        SavingsBucket bucket = buckets.saveAndFlush(new SavingsBucket(groupId, name, blankToNull(request.description()),
                request.type(), request.cycleType(), request.startDate(), request.terms(), penaltyJson(request.terms()),
                clock.instant()));
        audit.record(AuditEvent.of("BUCKET_CREATED").entity("savings_bucket", bucket.getPublicId())
                .after(Map.of("name", name, "type", request.type(), "terms", request.terms())));
        generator.generate(groupId, bucket);
        return view(groupId, bucket);
    }

    @Transactional(readOnly = true)
    List<BucketView> list() {
        long groupId = TenantContext.requireGroup().groupId();
        return buckets.findByGroupIdOrderByName(groupId).stream().map(b -> view(groupId, b)).toList();
    }

    @Transactional(readOnly = true)
    BucketView get(UUID bucketId) {
        long groupId = TenantContext.requireGroup().groupId();
        return view(groupId, buckets.findByGroupIdAndPublicId(groupId, bucketId).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND)));
    }

    @Transactional
    UpdateResult update(UUID bucketId, long expectedVersion, String name, String description, SavingsBucket.Status status,
                        BucketTerms terms) {
        TenantContext.GroupScope scope = TenantContext.requireGroup();
        SavingsBucket bucket = buckets.findByGroupIdAndPublicIdForUpdate(scope.groupId(), bucketId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        if (bucket.getVersion() != expectedVersion) {
            throw new ApiException(ErrorCode.CONCURRENT_MODIFICATION);
        }
        if (name != null && !name.trim().equalsIgnoreCase(bucket.getName())
                && buckets.existsByGroupIdAndNameIgnoreCase(scope.groupId(), name.trim())) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED);
        }
        Map<String, Object> before = Map.of("name", bucket.getName(), "status", bucket.getStatus());
        bucket.rename(name == null ? null : name.trim(), description);
        if (status != null) {
            bucket.changeStatus(status);
        }
        buckets.flush();
        audit.record(AuditEvent.of("BUCKET_UPDATED").entity("savings_bucket", bucket.getPublicId())
                .before(before).after(Map.of("name", bucket.getName(), "status", bucket.getStatus())));

        if (terms == null || !terms(bucket).differsFrom(terms)) {
            return new UpdateResult(true, view(scope.groupId(), bucket));
        }
        validate(bucket.getBucketType(), bucket.getCycleType(), bucket.getStartDate(), terms);
        stepUp.require();
        Instant now = clock.instant();
        for (BucketChangeRequest open : changes.findByGroupIdAndBucketIdAndStatus(scope.groupId(), bucket.getId(),
                BucketChangeRequest.Status.PENDING)) {
            open.decide(BucketChangeRequest.Status.SUPERSEDED, scope.membershipId(), "replaced by a newer proposal", now);
        }
        changes.flush();
        BucketChangeRequest change = changes.save(new BucketChangeRequest(scope.groupId(), bucket.getId(), bucket.getVersion(),
                json.writeValueAsString(terms), scope.membershipId(), now));
        audit.record(AuditEvent.of("BUCKET_CHANGE_PROPOSED").entity("bucket_change", change.getPublicId())
                .before(terms(bucket)).after(terms));
        return new UpdateResult(false, view(scope.groupId(), bucket));
    }

    /** @return the updated bucket, or empty if the bucket changed after the proposal (the proposal is then closed). */
    @Transactional
    Optional<BucketView> confirm(UUID bucketId, UUID changeId) {
        TenantContext.GroupScope scope = TenantContext.requireGroup();
        SavingsBucket bucket = buckets.findByGroupIdAndPublicIdForUpdate(scope.groupId(), bucketId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        BucketChangeRequest change = pending(scope.groupId(), bucket, changeId);
        if (change.getProposedByMembership().equals(scope.membershipId())) {
            throw new ApiException(ErrorCode.SELF_APPROVAL_FORBIDDEN);
        }
        if (bucket.getVersion() != change.getBaseVersion()) {
            change.decide(BucketChangeRequest.Status.SUPERSEDED, scope.membershipId(), "bucket changed after the proposal",
                    clock.instant());
            return Optional.empty();
        }
        BucketTerms before = terms(bucket);
        BucketTerms proposed = json.readValue(change.getProposedTerms(), BucketTerms.class);
        bucket.applyTerms(proposed, penaltyJson(proposed));
        change.decide(BucketChangeRequest.Status.APPLIED, scope.membershipId(), null, clock.instant());
        buckets.flush();
        audit.record(AuditEvent.of("BUCKET_TERMS_CHANGED").entity("savings_bucket", bucket.getPublicId())
                .before(before).after(proposed).reason("confirmed change " + change.getPublicId()));
        generator.generate(scope.groupId(), bucket);
        return Optional.of(view(scope.groupId(), bucket));
    }

    @Transactional
    BucketView reject(UUID bucketId, UUID changeId, String reason) {
        TenantContext.GroupScope scope = TenantContext.requireGroup();
        SavingsBucket bucket = buckets.findByGroupIdAndPublicId(scope.groupId(), bucketId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        BucketChangeRequest change = pending(scope.groupId(), bucket, changeId);
        change.decide(BucketChangeRequest.Status.REJECTED, scope.membershipId(), reason, clock.instant());
        audit.record(AuditEvent.of("BUCKET_CHANGE_REJECTED").entity("bucket_change", change.getPublicId()).reason(reason));
        return view(scope.groupId(), bucket);
    }

    private BucketChangeRequest pending(long groupId, SavingsBucket bucket, UUID changeId) {
        BucketChangeRequest change = changes.findByGroupIdAndPublicIdForUpdate(groupId, changeId)
                .filter(c -> c.getBucketId().equals(bucket.getId()))
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        if (!change.isPending()) {
            throw new ApiException(ErrorCode.BUCKET_CHANGE_STALE);
        }
        return change;
    }

    /** Spec 8: rules a bucket's terms must satisfy, whoever sets them. */
    private static void validate(SavingsBucket.Type type, SavingsBucket.CycleType cycleType, LocalDate startDate, BucketTerms terms) {
        Money minimum = terms.minimumContribution();
        boolean valid = !minimum.isNegative() && minimum.isWholeRwf()
                && (!terms.mandatory() || (minimum.isPositive() && terms.contributionFrequency() != SavingsBucket.Frequency.ADHOC))
                // Social funds are not withdrawable except through a recorded resolution (spec 8.2, V2).
                && !(type == SavingsBucket.Type.SOCIAL_FUND && terms.withdrawable())
                && (cycleType == SavingsBucket.CycleType.FIXED_TERM
                    ? terms.endDate() != null && !terms.endDate().isBefore(startDate)
                    : terms.endDate() == null);
        if (valid && terms.latePenaltyRule() != null) {
            BucketTerms.PenaltyRule rule = terms.latePenaltyRule();
            valid = rule.value().matches("(0|[1-9][0-9]{0,8})(\\.[0-9]{1,4})?") && rule.graceDays() >= 0 && rule.graceDays() <= 365
                    && (rule.type() != BucketTerms.PenaltyRule.Type.PERCENT || new BigDecimal(rule.value()).compareTo(BigDecimal.valueOf(100)) <= 0);
        }
        if (!valid) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED);
        }
    }

    private BucketTerms terms(SavingsBucket bucket) {
        BucketTerms.PenaltyRule penalty = bucket.getLatePenaltyRule() == null ? null
                : json.readValue(bucket.getLatePenaltyRule(), BucketTerms.PenaltyRule.class);
        return new BucketTerms(bucket.isMandatory(), bucket.getMinimumContribution(), bucket.getContributionFrequency(),
                bucket.isWithdrawable(), bucket.getEndDate(), penalty);
    }

    private String penaltyJson(BucketTerms terms) {
        return terms.latePenaltyRule() == null ? null : json.writeValueAsString(terms.latePenaltyRule());
    }

    private BucketView view(long groupId, SavingsBucket bucket) {
        PendingChange pending = changes.findByGroupIdAndBucketIdAndStatus(groupId, bucket.getId(), BucketChangeRequest.Status.PENDING)
                .stream().findFirst()
                .map(c -> new PendingChange(c.getPublicId(), json.readValue(c.getProposedTerms(), BucketTerms.class),
                        members.findById(c.getProposedByMembership()).map(GroupMembers.Member::memberId).orElse(null),
                        c.getCreatedAt()))
                .orElse(null);
        return new BucketView(bucket.getPublicId(), bucket.getName(), bucket.getDescription(), bucket.getBucketType(),
                bucket.getCycleType(), bucket.getStartDate(), terms(bucket), bucket.getStatus(), bucket.getVersion(), pending);
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }
}
