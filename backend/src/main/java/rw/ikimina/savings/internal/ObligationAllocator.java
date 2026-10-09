package rw.ikimina.savings.internal;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import rw.ikimina.shared.money.Money;
import rw.ikimina.shared.time.BusinessTime;

/**
 * Matches a member's contributions to what they owe (spec 8.2): payments settle unsettled
 * obligations oldest first; a payment larger than what is owed stays as credit and settles the
 * next obligation when it is generated (owner decision: overpayment carries forward).
 *
 * <p>Always recomputed from the live contributions and allocations of one member and bucket,
 * under row locks, so recording, reversing and generating can run in any order and still agree.
 */
@Component
@Transactional(propagation = Propagation.MANDATORY)
class ObligationAllocator {

    private final SavingsTransactionRepository transactions;
    private final ContributionAllocationRepository allocations;
    private final ObligationRepository obligations;
    private final Clock clock;

    ObligationAllocator(SavingsTransactionRepository transactions, ContributionAllocationRepository allocations,
                        ObligationRepository obligations, Clock clock) {
        this.transactions = transactions;
        this.allocations = allocations;
        this.obligations = obligations;
        this.clock = clock;
    }

    /**
     * Spends the member's unspent contribution credit in this bucket on their unsettled
     * obligations. {@code preferredObligationId}, if given, is settled first.
     */
    void settle(long groupId, long membershipId, long bucketId, Long preferredObligationId) {
        List<SavingsTransaction> live = transactions.findLiveContributionsForUpdate(groupId, membershipId, bucketId);
        if (live.isEmpty()) {
            return;
        }
        Map<Long, Money> credit = new HashMap<>();
        for (SavingsTransaction txn : live) {
            credit.put(txn.getId(), txn.getAmount());
        }
        for (ContributionAllocation allocation : allocations.findByTransactionIdInAndReversedAtIsNull(credit.keySet())) {
            credit.computeIfPresent(allocation.getTransactionId(), (id, left) -> left.minus(allocation.getAmount()));
        }

        List<Obligation> owed = new ArrayList<>(obligations.findUnsettledForUpdate(groupId, membershipId, bucketId));
        if (preferredObligationId != null) {
            owed.sort((a, b) -> a.getId().equals(preferredObligationId) ? -1 : b.getId().equals(preferredObligationId) ? 1 : 0);
        }
        LocalDate today = BusinessTime.today(clock);
        Instant now = clock.instant();
        int t = 0;
        for (Obligation obligation : owed) {
            Money need = obligation.outstanding();
            while (need.isPositive() && t < live.size()) {
                SavingsTransaction source = live.get(t);
                Money available = credit.get(source.getId());
                if (!available.isPositive()) {
                    t++;
                    continue;
                }
                Money take = available.compareTo(need) <= 0 ? available : need;
                allocations.save(new ContributionAllocation(groupId, source.getId(), obligation.getId(), take, now));
                obligation.applyPayment(take, today);
                credit.put(source.getId(), available.minus(take));
                need = need.minus(take);
            }
            if (t >= live.size()) {
                break;
            }
        }
    }

    /** Undoes everything a contribution paid, so its obligations are owed again. */
    void undo(long groupId, SavingsTransaction reversed) {
        List<ContributionAllocation> paid = allocations.findByTransactionIdAndReversedAtIsNull(reversed.getId());
        if (paid.isEmpty()) {
            return;
        }
        LocalDate today = BusinessTime.today(clock);
        Instant now = clock.instant();
        Map<Long, Obligation> touched = new HashMap<>();
        obligations.findByGroupIdAndIdInForUpdate(groupId, paid.stream().map(ContributionAllocation::getObligationId).toList())
                .forEach(o -> touched.put(o.getId(), o));
        for (ContributionAllocation allocation : paid) {
            allocation.markReversed(now);
            touched.get(allocation.getObligationId()).applyPayment(allocation.getAmount().negate(), today);
        }
    }
}
