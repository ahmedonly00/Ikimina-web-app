package rw.ikimina.savings.internal;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.ikimina.groups.GroupMembers;
import rw.ikimina.groups.GroupRole;
import rw.ikimina.groups.Permission;
import rw.ikimina.groups.PermissionMatrix;
import rw.ikimina.ledger.AccountRef;
import rw.ikimina.ledger.Ledger;
import rw.ikimina.ledger.StatementEntry;
import rw.ikimina.shared.error.ApiException;
import rw.ikimina.shared.error.ErrorCode;
import rw.ikimina.shared.money.Money;
import rw.ikimina.shared.tenancy.TenantContext;
import rw.ikimina.shared.time.BusinessTime;

/**
 * What members and officers read about savings (spec 17.3): balances, transactions, statements
 * and obligations. Balances and statements come from the ledger itself (spec 8.2: "member balance
 * per bucket = ledger balance").
 *
 * <p>Object rule (spec 5.4 #4): a member sees their own figures; seeing someone else's needs
 * REPORT_VIEW_GROUP or CONTRIBUTION_RECORD.
 */
@Service
@Transactional(readOnly = true)
class SavingsQueries {

    record BucketBalance(UUID bucketId, String name, SavingsBucket.Type type, Money balance) {
    }

    record Balances(UUID memberId, List<BucketBalance> buckets, Money total) {
    }

    record TransactionView(UUID transactionId, UUID journalId, UUID bucketId, String bucketName, SavingsTransaction.Type type, Money amount,
                           SavingsTransaction.Method method, String externalRef, LocalDate businessDate, boolean reversed) {
    }

    record BucketStatement(UUID bucketId, String name, Money opening, List<StatementEntry> entries, Money closing) {
    }

    record Statement(UUID memberId, String memberNumber, String fullName, LocalDate from, LocalDate to, List<BucketStatement> buckets) {
    }

    record ObligationView(UUID obligationId, UUID bucketId, UUID memberId, LocalDate periodStart, LocalDate dueDate,
                          Money amountDue, Money amountPaid, Obligation.Status status) {
    }

    record PageView<T>(List<T> items, int page, int size, long totalItems, int totalPages) {
        static <T> PageView<T> of(Page<?> page, List<T> items) {
            return new PageView<>(items, page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
        }
    }

    private final Ledger ledger;
    private final GroupMembers members;
    private final SavingsBucketRepository buckets;
    private final SavingsTransactionRepository transactions;
    private final ObligationRepository obligations;
    private final Clock clock;

    SavingsQueries(Ledger ledger, GroupMembers members, SavingsBucketRepository buckets, SavingsTransactionRepository transactions,
                   ObligationRepository obligations, Clock clock) {
        this.ledger = ledger;
        this.members = members;
        this.buckets = buckets;
        this.transactions = transactions;
        this.obligations = obligations;
        this.clock = clock;
    }

    Balances balances(UUID memberId) {
        GroupMembers.Member member = visibleMember(memberId);
        Map<Long, Money> byBucket = ledger.memberSavingsByBucket(member.membershipId());
        List<BucketBalance> rows = buckets.findByGroupIdOrderByName(groupId()).stream()
                .filter(b -> b.isActive() || byBucket.containsKey(b.getId()))
                .map(b -> new BucketBalance(b.getPublicId(), b.getName(), b.getBucketType(), byBucket.getOrDefault(b.getId(), Money.ZERO)))
                .toList();
        Money total = rows.stream().map(BucketBalance::balance).reduce(Money.ZERO, Money::plus);
        return new Balances(member.memberId(), rows, total);
    }

    PageView<TransactionView> transactions(UUID memberId, int page, int size) {
        GroupMembers.Member member = visibleMember(memberId);
        Map<Long, SavingsBucket> byId = bucketsById();
        Page<SavingsTransaction> found = transactions.findByGroupIdAndMembershipId(groupId(), member.membershipId(),
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "businessDate", "id")));
        Map<Long, UUID> journals = ledger.publicIds(found.getContent().stream().map(SavingsTransaction::getJournalId).toList());
        return PageView.of(found, found.getContent().stream().map(t -> {
            SavingsBucket bucket = byId.get(t.getBucketId());
            return new TransactionView(t.getPublicId(), journals.get(t.getJournalId()), bucket.getPublicId(), bucket.getName(), t.getTxnType(), t.getAmount(),
                    t.getPaymentMethod(), t.getExternalRef(), t.getBusinessDate(), t.isReversed());
        }).toList());
    }

    /** Spec 15.1 member statement, JSON for now (PDF/Excel in Phase 8): opening, every entry, closing - per bucket. */
    Statement statement(UUID memberId, LocalDate from, LocalDate to, UUID bucketId) {
        GroupMembers.Member member = visibleMember(memberId);
        LocalDate end = to == null ? BusinessTime.today(clock) : to;
        LocalDate start = from == null ? end.minusMonths(3) : from;
        if (start.isAfter(end)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED);
        }
        List<BucketStatement> rows = buckets.findByGroupIdOrderByName(groupId()).stream()
                .filter(b -> bucketId == null || b.getPublicId().equals(bucketId))
                .map(b -> {
                    AccountRef account = AccountRef.memberSavings(member.membershipId(), b.getId());
                    List<StatementEntry> entries = ledger.entries(account, start, end);
                    Money opening = ledger.balanceBefore(account, start);
                    Money closing = entries.isEmpty() ? opening : entries.getLast().balanceAfter();
                    return new BucketStatement(b.getPublicId(), b.getName(), opening, entries, closing);
                })
                .toList();
        return new Statement(member.memberId(), member.memberNumber(), member.fullName(), start, end, rows);
    }

    /** Officers see everyone's obligations; anyone else sees only their own, whatever they ask for. */
    PageView<ObligationView> obligations(UUID bucketId, UUID memberId, Obligation.Status status, int page, int size) {
        TenantContext.GroupScope scope = TenantContext.requireGroup();
        Long membershipFilter;
        if (canSeeOthers(scope)) {
            membershipFilter = memberId == null ? null
                    : members.find(memberId).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND)).membershipId();
        } else {
            membershipFilter = scope.membershipId();
        }
        Map<Long, SavingsBucket> byId = bucketsById();
        Long bucketFilter = bucketId == null ? null : byId.values().stream().filter(b -> b.getPublicId().equals(bucketId))
                .findFirst().orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND)).getId();
        Page<Obligation> found = obligations.search(scope.groupId(), bucketFilter, membershipFilter, status,
                PageRequest.of(page, size, Sort.by("dueDate", "id")));
        Map<Long, GroupMembers.Member> people = members.findByIds(found.getContent().stream().map(Obligation::getMembershipId).toList());
        return PageView.of(found, found.getContent().stream().map(o -> new ObligationView(o.getPublicId(),
                byId.get(o.getBucketId()).getPublicId(), people.get(o.getMembershipId()).memberId(), o.getPeriodStart(), o.getDueDate(),
                o.getAmountDue(), o.getAmountPaid(), o.getStatus())).toList());
    }

    private GroupMembers.Member visibleMember(UUID memberId) {
        TenantContext.GroupScope scope = TenantContext.requireGroup();
        GroupMembers.Member member = members.find(memberId).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        if (member.membershipId() != scope.membershipId() && !canSeeOthers(scope)) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }
        return member;
    }

    private static boolean canSeeOthers(TenantContext.GroupScope scope) {
        GroupRole role = GroupRole.valueOf(scope.role());
        return PermissionMatrix.allows(role, Permission.REPORT_VIEW_GROUP) || PermissionMatrix.allows(role, Permission.CONTRIBUTION_RECORD);
    }

    private Map<Long, SavingsBucket> bucketsById() {
        return buckets.findByGroupIdOrderByName(groupId()).stream().collect(Collectors.toMap(SavingsBucket::getId, Function.identity()));
    }

    private static long groupId() {
        return TenantContext.requireGroup().groupId();
    }
}
