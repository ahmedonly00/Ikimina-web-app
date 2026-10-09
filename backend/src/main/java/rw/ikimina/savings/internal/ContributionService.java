package rw.ikimina.savings.internal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.ikimina.audit.AuditEvent;
import rw.ikimina.audit.AuditService;
import rw.ikimina.groups.GroupMembers;
import rw.ikimina.ledger.AccountRef;
import rw.ikimina.ledger.AccountType;
import rw.ikimina.ledger.JournalRequest;
import rw.ikimina.ledger.JournalSource;
import rw.ikimina.ledger.JournalType;
import rw.ikimina.ledger.Ledger;
import rw.ikimina.ledger.PostedJournal;
import rw.ikimina.shared.error.ApiException;
import rw.ikimina.shared.error.ErrorCode;
import rw.ikimina.shared.money.Money;
import rw.ikimina.shared.security.CurrentUser;
import rw.ikimina.shared.tenancy.TenantContext;
import rw.ikimina.shared.time.BusinessTime;

/**
 * Recording a contribution (spec 8, 17.3): one ledger journal (debit group cash, credit the
 * member's savings in that bucket - spec 7.2), the savings record, the allocation to what the
 * member owes, and the audit row, all in one transaction.
 *
 * <p>Idempotent (H7): the client's Idempotency-Key makes a retried request return the original
 * contribution; the same key with a different request is refused.
 */
@Service
class ContributionService {

    /** Idempotency keys: what the API accepts from clients. */
    static final Pattern IDEMPOTENCY_KEY = Pattern.compile("[A-Za-z0-9._:-]{8,80}");

    record ContributionCommand(UUID memberId, UUID bucketId, Money amount, SavingsTransaction.Method method,
                               String externalRef, UUID obligationId, LocalDate businessDate) {
    }

    record ContributionView(UUID transactionId, UUID journalId, UUID memberId, UUID bucketId, Money amount,
                            SavingsTransaction.Method method, String externalRef, LocalDate businessDate,
                            Instant recordedAt, boolean reversed, Money memberBalance) {
    }

    private final Ledger ledger;
    private final GroupMembers members;
    private final SavingsBucketRepository buckets;
    private final SavingsTransactionRepository transactions;
    private final ObligationRepository obligations;
    private final ObligationAllocator allocator;
    private final AuditService audit;
    private final Clock clock;

    ContributionService(Ledger ledger, GroupMembers members, SavingsBucketRepository buckets,
                        SavingsTransactionRepository transactions, ObligationRepository obligations,
                        ObligationAllocator allocator, AuditService audit, Clock clock) {
        this.ledger = ledger;
        this.members = members;
        this.buckets = buckets;
        this.transactions = transactions;
        this.obligations = obligations;
        this.allocator = allocator;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    ContributionView record(String idempotencyKey, ContributionCommand command) {
        long groupId = TenantContext.requireGroup().groupId();
        LocalDate today = BusinessTime.today(clock);

        GroupMembers.Member member = members.find(command.memberId()).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        SavingsBucket bucket = buckets.findByGroupIdAndPublicId(groupId, command.bucketId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        Long obligationId = command.obligationId() == null ? null : obligations.findByGroupIdAndPublicId(groupId, command.obligationId())
                .filter(o -> o.getMembershipId() == member.membershipId() && o.getBucketId().equals(bucket.getId()))
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND)).getId();
        // Checked after resolving the member and bucket, so ids from another group answer 404 whatever else is wrong.
        if (idempotencyKey == null || !IDEMPOTENCY_KEY.matcher(idempotencyKey).matches()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED);
        }
        LocalDate businessDate = command.businessDate() == null ? today : command.businessDate();
        String externalRef = command.externalRef() == null || command.externalRef().isBlank() ? null : command.externalRef().trim();
        validate(command, member, bucket, businessDate, today, externalRef);

        String requestHash = hash(member.memberId() + "|" + bucket.getPublicId() + "|" + command.amount().toWireString() + "|"
                + command.method() + "|" + externalRef + "|" + command.obligationId() + "|" + businessDate);
        PostedJournal journal = ledger.post(new JournalRequest(JournalType.CONTRIBUTION, "contribution:" + idempotencyKey,
                requestHash, businessDate, "Contribution to " + bucket.getName(), JournalSource.MANUAL, externalRef,
                member.membershipId(), List.of(
                JournalRequest.Line.debit(AccountRef.of(AccountType.GROUP_CASH), command.amount()),
                JournalRequest.Line.credit(AccountRef.memberSavings(member.membershipId(), bucket.getId()), command.amount()))));

        if (journal.replayed()) {
            SavingsTransaction original = transactions.findByGroupIdAndJournalId(groupId, journal.id())
                    .orElseThrow(() -> new IllegalStateException("journal " + journal.id() + " has no savings record"));
            return view(original, member, bucket, journal);
        }
        // After the replay check: a retry of the same request carries the same reference and must not be refused (H7).
        if (externalRef != null && transactions.existsByGroupIdAndExternalRefAndReversedAtIsNull(groupId, externalRef)) {
            throw new ApiException(ErrorCode.DUPLICATE_EXTERNAL_REF);
        }

        SavingsTransaction txn = transactions.saveAndFlush(SavingsTransaction.contribution(groupId, bucket.getId(),
                member.membershipId(), command.amount(), journal.id(), obligationId, command.method(), externalRef, businessDate,
                CurrentUser.require().id(), clock.instant()));
        allocator.settle(groupId, member.membershipId(), bucket.getId(), obligationId);
        audit.record(AuditEvent.of("CONTRIBUTION_RECORDED").entity("savings_transaction", txn.getPublicId())
                .after(Map.of("member", member.memberId(), "bucket", bucket.getPublicId(), "amount", command.amount(),
                        "method", command.method(), "journal", journal.publicId(),
                        "externalRef", externalRef == null ? "" : externalRef)));
        return view(txn, member, bucket, journal);
    }

    private void validate(ContributionCommand command, GroupMembers.Member member, SavingsBucket bucket,
                          LocalDate businessDate, LocalDate today, String externalRef) {
        if (!member.active()) {
            throw new ApiException(ErrorCode.MEMBER_NOT_ACTIVE);
        }
        if (!bucket.isActive()) {
            throw new ApiException(ErrorCode.BUCKET_NOT_ACTIVE);
        }
        // RWF has no minor unit in practice (spec 4.3): refuse fractions rather than silently round them.
        if (!command.amount().isPositive() || !command.amount().isWholeRwf()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED);
        }
        if (command.method() == SavingsTransaction.Method.MOMO_API) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED);   // only the MoMo integration may claim this (V2)
        }
        if (command.method() == SavingsTransaction.Method.MOMO_MANUAL && externalRef == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED);
        }
        if (businessDate.isAfter(today) || businessDate.isBefore(bucket.getStartDate())) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED);
        }
    }

    private ContributionView view(SavingsTransaction txn, GroupMembers.Member member, SavingsBucket bucket, PostedJournal journal) {
        return new ContributionView(txn.getPublicId(), journal.publicId(), member.memberId(), bucket.getPublicId(), txn.getAmount(),
                txn.getPaymentMethod(), txn.getExternalRef(), txn.getBusinessDate(), txn.getRecordedAt(), txn.isReversed(),
                ledger.balance(AccountRef.memberSavings(member.membershipId(), bucket.getId())));
    }

    private static String hash(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
