package rw.ikimina.savings.internal;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import rw.ikimina.ledger.Ledger;
import rw.ikimina.savings.MemberSavings;
import rw.ikimina.shared.money.Money;
import rw.ikimina.shared.tenancy.TenantContext;

@Component
@Transactional(readOnly = true)
class MemberSavingsQueries implements MemberSavings {

    private final Ledger ledger;
    private final SavingsBucketRepository buckets;
    private final SavingsWithdrawalRepository withdrawals;

    MemberSavingsQueries(Ledger ledger, SavingsBucketRepository buckets, SavingsWithdrawalRepository withdrawals) {
        this.ledger = ledger;
        this.buckets = buckets;
        this.withdrawals = withdrawals;
    }

    @Override
    public Money approvedWithdrawals() {
        return withdrawals.findByGroupIdAndStatus(TenantContext.requireGroup().groupId(), SavingsWithdrawal.Status.APPROVED).stream()
                .map(SavingsWithdrawal::getAmount)
                .reduce(Money.ZERO, Money::plus);
    }

    @Override
    public Money loanBasis(long membershipId) {
        long groupId = TenantContext.requireGroup().groupId();
        Set<Long> socialFunds = buckets.findByGroupIdOrderByName(groupId).stream()
                .filter(b -> b.getBucketType() == SavingsBucket.Type.SOCIAL_FUND)
                .map(SavingsBucket::getId)
                .collect(Collectors.toSet());
        Map<Long, Money> byBucket = ledger.memberSavingsByBucket(membershipId);
        return byBucket.entrySet().stream()
                .filter(e -> !socialFunds.contains(e.getKey()))
                .map(Map.Entry::getValue)
                .reduce(Money.ZERO, Money::plus);
    }
}
