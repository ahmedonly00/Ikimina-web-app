package rw.ikimina.savings.internal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.ikimina.audit.AuditEvent;
import rw.ikimina.audit.AuditService;
import rw.ikimina.groups.GroupBylaws;
import rw.ikimina.groups.GroupMembers;
import rw.ikimina.groups.GroupRole;
import rw.ikimina.ledger.AccountRef;
import rw.ikimina.ledger.AccountType;
import rw.ikimina.ledger.JournalRequest;
import rw.ikimina.ledger.JournalSource;
import rw.ikimina.ledger.JournalType;
import rw.ikimina.ledger.Ledger;
import rw.ikimina.ledger.PostedJournal;
import rw.ikimina.savings.LoanCommitments;
import rw.ikimina.shared.error.ApiException;
import rw.ikimina.shared.error.ErrorCode;
import rw.ikimina.shared.error.ReasonedApiException;
import rw.ikimina.shared.error.ReasonedApiException.Reason;
import rw.ikimina.shared.locks.AdvisoryLocks;
import rw.ikimina.shared.money.Money;
import rw.ikimina.shared.security.CurrentUser;
import rw.ikimina.shared.tenancy.TenantContext;
import rw.ikimina.shared.time.BusinessTime;

/**
 * Savings withdrawals (spec 8.3; owner decisions, Phase 3c). A member asks for their own money; the
 * President or the Treasurer - never the member - approves or rejects; the Treasurer records the payout,
 * which posts the journal (debit the member's savings, credit group cash - spec 7.2) and a WITHDRAWAL
 * savings transaction, in one transaction.
 *
 * <p>The rules are checked when the member asks and again at approval and payout, since balances,
 * loans and bylaws can change in between: withdrawals allowed by the bylaws; the fund allows them (never a
 * social fund); no unfinished loan; whole francs, at most the fund balance less other pending requests;
 * at payout, the notice period is over and the group has the cash.
 */
@Service
class WithdrawalService {

    private static final java.util.regex.Pattern IDEMPOTENCY_KEY = ContributionService.IDEMPOTENCY_KEY;
    private static final EnumSet<SavingsWithdrawal.Status> PENDING = EnumSet.of(SavingsWithdrawal.Status.REQUESTED,
            SavingsWithdrawal.Status.APPROVED);

    private final SavingsWithdrawalRepository withdrawals;
    private final SavingsBucketRepository buckets;
    private final SavingsTransactionRepository transactions;
    private final WithdrawalQueries queries;
    private final GroupMembers members;
    private final GroupBylaws bylaws;
    private final LoanCommitments loans;
    private final Ledger ledger;
    private final AuditService audit;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    WithdrawalService(SavingsWithdrawalRepository withdrawals, SavingsBucketRepository buckets, SavingsTransactionRepository transactions,
                      WithdrawalQueries queries, GroupMembers members, GroupBylaws bylaws, LoanCommitments loans, Ledger ledger,
                      AuditService audit, JdbcTemplate jdbc, Clock clock) {
        this.withdrawals = withdrawals;
        this.buckets = buckets;
        this.transactions = transactions;
        this.queries = queries;
        this.members = members;
        this.bylaws = bylaws;
        this.loans = loans;
        this.ledger = ledger;
        this.audit = audit;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** A member asks to withdraw from one of their funds. */
    @Transactional
    WithdrawalQueries.WithdrawalView request(UUID bucketId, Money amount, String reason) {
        TenantContext.GroupScope scope = TenantContext.requireGroup();
        SavingsBucket bucket = buckets.findByGroupIdAndPublicId(scope.groupId(), bucketId).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        GroupMembers.Member member = members.findById(scope.membershipId()).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        AdvisoryLocks.lock(jdbc, AdvisoryLocks.MEMBER_WITHDRAWAL, member.membershipId());

        List<Reason> reasons = rules(member, bucket, amount, null);
        if (!reasons.isEmpty()) {
            throw new ReasonedApiException(ErrorCode.WITHDRAWAL_NOT_ALLOWED, reasons);
        }
        LocalDate today = BusinessTime.today(clock);
        LocalDate earliest = today.plusDays(bylaws.current().withdrawalNoticeDays());
        SavingsWithdrawal withdrawal = withdrawals.saveAndFlush(new SavingsWithdrawal(scope.groupId(), member.membershipId(), bucket.getId(),
                amount, blankToNull(reason), today, earliest, clock.instant()));
        audit.record(AuditEvent.of("WITHDRAWAL_REQUESTED").entity("savings_withdrawal", withdrawal.getPublicId())
                .after(Map.of("bucket", bucket.getPublicId(), "amount", amount, "earliestPayoutOn", earliest)));
        return queries.view(withdrawal);
    }

    /** The President or Treasurer agrees; never the member themselves (H9). */
    @Transactional
    WithdrawalQueries.WithdrawalView approve(UUID withdrawalId) {
        TenantContext.GroupScope scope = TenantContext.requireGroup();
        SavingsWithdrawal withdrawal = lockVisible(scope, withdrawalId);
        if (withdrawal.getMembershipId().equals(scope.membershipId())) {
            throw new ApiException(ErrorCode.SELF_APPROVAL_FORBIDDEN);
        }
        if (withdrawal.getStatus() != SavingsWithdrawal.Status.REQUESTED) {
            throw new ApiException(ErrorCode.WITHDRAWAL_INVALID_STATE);
        }
        GroupMembers.Member member = members.findById(withdrawal.getMembershipId()).orElseThrow();
        AdvisoryLocks.lock(jdbc, AdvisoryLocks.MEMBER_WITHDRAWAL, member.membershipId());
        List<Reason> reasons = rules(member, bucket(scope, withdrawal), withdrawal.getAmount(), withdrawal.getId());
        if (!reasons.isEmpty()) {
            throw new ReasonedApiException(ErrorCode.WITHDRAWAL_NOT_ALLOWED, reasons);
        }
        withdrawal.approve(scope.membershipId(), GroupRole.valueOf(scope.role()), clock.instant());
        withdrawals.flush();
        audit.record(AuditEvent.of("WITHDRAWAL_APPROVED").entity("savings_withdrawal", withdrawal.getPublicId())
                .after(Map.of("status", withdrawal.getStatus(), "approverRole", scope.role())));
        return queries.view(withdrawal);
    }

    @Transactional
    WithdrawalQueries.WithdrawalView reject(UUID withdrawalId, String reason) {
        TenantContext.GroupScope scope = TenantContext.requireGroup();
        SavingsWithdrawal withdrawal = lockVisible(scope, withdrawalId);
        if (withdrawal.getMembershipId().equals(scope.membershipId())) {
            throw new ApiException(ErrorCode.SELF_APPROVAL_FORBIDDEN);
        }
        withdrawal.reject(scope.membershipId(), GroupRole.valueOf(scope.role()), reason.trim(), clock.instant());
        withdrawals.flush();
        audit.record(AuditEvent.of("WITHDRAWAL_REJECTED").entity("savings_withdrawal", withdrawal.getPublicId()).reason(reason.trim()));
        return queries.view(withdrawal);
    }

    /** The member changes their mind, before the money is paid out. */
    @Transactional
    WithdrawalQueries.WithdrawalView cancel(UUID withdrawalId) {
        TenantContext.GroupScope scope = TenantContext.requireGroup();
        SavingsWithdrawal withdrawal = lockVisible(scope, withdrawalId);
        if (!withdrawal.getMembershipId().equals(scope.membershipId())) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }
        withdrawal.cancel();
        withdrawals.flush();
        audit.record(AuditEvent.of("WITHDRAWAL_CANCELLED").entity("savings_withdrawal", withdrawal.getPublicId()));
        return queries.view(withdrawal);
    }

    /** The Treasurer records that the money was handed over. Idempotent on the Idempotency-Key (H7). */
    @Transactional
    WithdrawalQueries.WithdrawalView pay(UUID withdrawalId, String idempotencyKey, SavingsTransaction.Method method, String externalRef,
                                         LocalDate businessDate) {
        TenantContext.GroupScope scope = TenantContext.requireGroup();
        // Withdrawals and loan payouts spend the same cash: one payout at a time per group, before any row lock.
        AdvisoryLocks.lock(jdbc, AdvisoryLocks.GROUP_PAYOUT, scope.groupId());
        SavingsWithdrawal withdrawal = lockVisible(scope, withdrawalId);
        if (idempotencyKey == null || !IDEMPOTENCY_KEY.matcher(idempotencyKey).matches()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED);
        }
        String ref = externalRef == null || externalRef.isBlank() ? null : externalRef.trim();
        // The date as the client sent it (null = today), so a retry after midnight is still the same request.
        String requestHash = hash(withdrawal.getPublicId() + "|" + method + "|" + ref + "|" + businessDate);
        Optional<SavingsWithdrawal> earlier = withdrawals.findByGroupIdAndPayIdempotencyKey(scope.groupId(), idempotencyKey);
        if (earlier.isPresent()) {
            if (!earlier.get().getId().equals(withdrawal.getId()) || !requestHash.equals(earlier.get().getPayRequestHash())) {
                throw new ApiException(ErrorCode.IDEMPOTENCY_CONFLICT);
            }
            return queries.view(withdrawal);
        }

        if (withdrawal.getStatus() != SavingsWithdrawal.Status.APPROVED) {
            throw new ApiException(ErrorCode.WITHDRAWAL_INVALID_STATE);
        }
        LocalDate today = BusinessTime.today(clock);
        if (today.isBefore(withdrawal.getEarliestPayoutOn())) {
            throw new ApiException(ErrorCode.WITHDRAWAL_NOT_YET_DUE, withdrawal.getEarliestPayoutOn().toString());
        }
        LocalDate date = businessDate == null ? today : businessDate;
        if (method == SavingsTransaction.Method.MOMO_API || method == SavingsTransaction.Method.MOMO_MANUAL && ref == null
                || date.isAfter(today) || date.isBefore(withdrawal.getRequestedOn())) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED);
        }
        if (ref != null && transactions.existsByGroupIdAndExternalRefAndReversedAtIsNull(scope.groupId(), ref)) {
            throw new ApiException(ErrorCode.DUPLICATE_EXTERNAL_REF);
        }
        GroupMembers.Member member = members.findById(withdrawal.getMembershipId()).orElseThrow();
        AdvisoryLocks.lock(jdbc, AdvisoryLocks.MEMBER_WITHDRAWAL, member.membershipId());
        SavingsBucket bucket = bucket(scope, withdrawal);
        List<Reason> reasons = rules(member, bucket, withdrawal.getAmount(), withdrawal.getId());
        if (!reasons.isEmpty()) {
            throw new ReasonedApiException(ErrorCode.WITHDRAWAL_NOT_ALLOWED, reasons);
        }
        if (availableCash(scope.groupId(), withdrawal.getId()).compareTo(withdrawal.getAmount()) < 0) {
            throw new ApiException(ErrorCode.INSUFFICIENT_GROUP_FUNDS);
        }

        PostedJournal journal = ledger.post(new JournalRequest(JournalType.WITHDRAWAL, "withdrawal:" + idempotencyKey, requestHash, date,
                "Withdrawal from " + bucket.getName(), JournalSource.MANUAL, ref, member.membershipId(), List.of(
                JournalRequest.Line.debit(AccountRef.memberSavings(member.membershipId(), bucket.getId()), withdrawal.getAmount()),
                JournalRequest.Line.credit(AccountRef.of(AccountType.GROUP_CASH), withdrawal.getAmount()))));
        long userId = CurrentUser.require().id();
        SavingsTransaction txn = transactions.saveAndFlush(SavingsTransaction.withdrawal(scope.groupId(), bucket.getId(),
                member.membershipId(), withdrawal.getAmount(), journal.id(), method, ref, date, userId, clock.instant()));
        withdrawal.pay(txn.getId(), userId, idempotencyKey, requestHash, clock.instant());
        withdrawals.flush();

        boolean own = member.userId() == userId;
        audit.record(AuditEvent.of("WITHDRAWAL_PAID").entity("savings_withdrawal", withdrawal.getPublicId())
                .after(Map.of("amount", withdrawal.getAmount(), "method", method, "journal", journal.publicId(),
                        "externalRef", ref == null ? "" : ref, "recordedByMember", own))
                .reason(own ? "recorded by the member on their own withdrawal" : null));
        return queries.view(withdrawal);
    }

    /** Every rule the withdrawal breaks right now; {@code self} is left out of the pending requests. */
    private List<Reason> rules(GroupMembers.Member member, SavingsBucket bucket, Money amount, Long self) {
        List<Reason> reasons = new ArrayList<>();
        if (!bylaws.current().withdrawalsAllowed()) {
            reasons.add(new Reason("WITHDRAWALS_OFF"));
        }
        if (!bucket.isWithdrawable() || bucket.getBucketType() == SavingsBucket.Type.SOCIAL_FUND) {
            reasons.add(new Reason("FUND_NOT_WITHDRAWABLE"));
        }
        if (!member.active()) {
            reasons.add(new Reason("MEMBER_NOT_ACTIVE"));
        }
        if (loans.hasUnfinishedLoan(member.membershipId())) {
            reasons.add(new Reason("OPEN_LOAN_WITHDRAWAL"));
        }
        if (!amount.isPositive() || !amount.isWholeRwf()) {
            reasons.add(new Reason("AMOUNT_NOT_WHOLE"));
        }
        Money pending = withdrawals.findByGroupIdAndMembershipIdAndBucketIdAndStatusIn(bucket.getGroupId(), member.membershipId(),
                        bucket.getId(), PENDING).stream()
                .filter(w -> !w.getId().equals(self))
                .map(SavingsWithdrawal::getAmount)
                .reduce(Money.ZERO, Money::plus);
        Money available = ledger.balance(AccountRef.memberSavings(member.membershipId(), bucket.getId())).minus(pending);
        if (amount.compareTo(available) > 0) {
            reasons.add(new Reason("ABOVE_BALANCE", available.isNegative() ? "0.00" : available.toWireString()));
        }
        return reasons;
    }

    /** Group cash less loans approved but not handed over, and other withdrawals approved but not paid. */
    private Money availableCash(long groupId, long self) {
        Money approvedWithdrawals = withdrawals.findByGroupIdAndStatus(groupId, SavingsWithdrawal.Status.APPROVED).stream()
                .filter(w -> !w.getId().equals(self))
                .map(SavingsWithdrawal::getAmount)
                .reduce(Money.ZERO, Money::plus);
        return ledger.balance(AccountRef.of(AccountType.GROUP_CASH)).minus(loans.approvedNotDisbursed()).minus(approvedWithdrawals);
    }

    private SavingsWithdrawal lockVisible(TenantContext.GroupScope scope, UUID withdrawalId) {
        SavingsWithdrawal withdrawal = withdrawals.findByGroupIdAndPublicIdForUpdate(scope.groupId(), withdrawalId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        if (!withdrawal.getMembershipId().equals(scope.membershipId()) && !WithdrawalQueries.seesEveryWithdrawal(scope)) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }
        return withdrawal;
    }

    private SavingsBucket bucket(TenantContext.GroupScope scope, SavingsWithdrawal withdrawal) {
        return buckets.findById(withdrawal.getBucketId()).filter(b -> b.getGroupId() == scope.groupId()).orElseThrow();
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }

    private static String hash(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
