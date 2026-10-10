package rw.ikimina.savings.internal;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.ikimina.groups.GroupMembers;
import rw.ikimina.groups.GroupRole;
import rw.ikimina.groups.Permission;
import rw.ikimina.groups.PermissionMatrix;
import rw.ikimina.ledger.Ledger;
import rw.ikimina.shared.error.ApiException;
import rw.ikimina.shared.error.ErrorCode;
import rw.ikimina.shared.money.Money;
import rw.ikimina.shared.tenancy.TenantContext;

/**
 * Reading withdrawals. A member sees their own; officers who decide or pay them, and anyone with
 * group reports, see every one. Someone else's withdrawal answers 404, as if it did not exist.
 */
@Service
@Transactional(readOnly = true)
class WithdrawalQueries {

    record Person(UUID memberId, String memberNumber, String fullName) {
    }

    /**
     * @param recordedByMember the member recorded the payout of their own withdrawal - allowed, but flagged
     * @param journalId        the payout journal, to ask for its reversal
     */
    record WithdrawalView(UUID withdrawalId, Person member, UUID bucketId, String bucketName, Money amount, String reason,
                          SavingsWithdrawal.Status status, LocalDate requestedOn, LocalDate earliestPayoutOn, Instant requestedAt,
                          GroupRole decidedRole, Instant decidedAt, String decisionReason, Instant paidAt, UUID journalId,
                          boolean recordedByMember, boolean reversed) {
    }

    record PageView<T>(List<T> items, int page, int size, long totalItems, int totalPages) {
    }

    private final SavingsWithdrawalRepository withdrawals;
    private final SavingsBucketRepository buckets;
    private final SavingsTransactionRepository transactions;
    private final GroupMembers members;
    private final Ledger ledger;

    WithdrawalQueries(SavingsWithdrawalRepository withdrawals, SavingsBucketRepository buckets, SavingsTransactionRepository transactions,
                      GroupMembers members, Ledger ledger) {
        this.withdrawals = withdrawals;
        this.buckets = buckets;
        this.transactions = transactions;
        this.members = members;
        this.ledger = ledger;
    }

    WithdrawalView get(UUID withdrawalId) {
        TenantContext.GroupScope scope = TenantContext.requireGroup();
        SavingsWithdrawal withdrawal = withdrawals.findByGroupIdAndPublicId(scope.groupId(), withdrawalId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        if (!withdrawal.getMembershipId().equals(scope.membershipId()) && !seesEveryWithdrawal(scope)) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }
        return view(withdrawal);
    }

    /** Officers see every withdrawal (optionally one member's); anyone else sees only their own, whatever they ask for. */
    PageView<WithdrawalView> list(UUID memberId, SavingsWithdrawal.Status status, int page, int size) {
        TenantContext.GroupScope scope = TenantContext.requireGroup();
        Long member;
        if (seesEveryWithdrawal(scope)) {
            member = memberId == null ? null
                    : members.find(memberId).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND)).membershipId();
        } else {
            member = scope.membershipId();
        }
        Page<SavingsWithdrawal> found = withdrawals.search(scope.groupId(), member, status,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "requestedAt", "id")));
        return new PageView<>(found.getContent().stream().map(this::view).toList(), found.getNumber(), found.getSize(),
                found.getTotalElements(), found.getTotalPages());
    }

    WithdrawalView view(SavingsWithdrawal w) {
        GroupMembers.Member member = members.findById(w.getMembershipId()).orElse(null);
        SavingsBucket bucket = buckets.findById(w.getBucketId()).orElseThrow();
        UUID journalId = w.getTransactionId() == null ? null
                : transactions.findById(w.getTransactionId())
                        .flatMap(t -> ledger.publicIds(List.of(t.getJournalId())).values().stream().findFirst())
                        .orElse(null);
        boolean byMember = member != null && w.getPaidBy() != null && w.getPaidBy() == member.userId();
        return new WithdrawalView(w.getPublicId(),
                member == null ? null : new Person(member.memberId(), member.memberNumber(), member.fullName()),
                bucket.getPublicId(), bucket.getName(), w.getAmount(), w.getReason(), w.getStatus(), w.getRequestedOn(),
                w.getEarliestPayoutOn(), w.getRequestedAt(), w.getDecidedRole(), w.getDecidedAt(), w.getDecisionReason(), w.getPaidAt(),
                journalId, byMember, w.getReversedAt() != null);
    }

    static boolean seesEveryWithdrawal(TenantContext.GroupScope scope) {
        GroupRole role = GroupRole.valueOf(scope.role());
        return PermissionMatrix.allows(role, Permission.REPORT_VIEW_GROUP) || PermissionMatrix.allows(role, Permission.LOAN_APPROVE)
                || PermissionMatrix.allows(role, Permission.CONTRIBUTION_RECORD);
    }
}
