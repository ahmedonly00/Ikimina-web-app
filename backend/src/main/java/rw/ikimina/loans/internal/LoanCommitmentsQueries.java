package rw.ikimina.loans.internal;

import java.util.EnumSet;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import rw.ikimina.loans.internal.LoanStateMachine.Status;
import rw.ikimina.savings.LoanCommitments;
import rw.ikimina.shared.money.Money;
import rw.ikimina.shared.tenancy.TenantContext;

@Component
@Transactional(readOnly = true)
class LoanCommitmentsQueries implements LoanCommitments {

    private final LoanRepository loans;

    LoanCommitmentsQueries(LoanRepository loans) {
        this.loans = loans;
    }

    @Override
    public boolean hasUnfinishedLoan(long membershipId) {
        return loans.existsByGroupIdAndBorrowerMembershipIdAndStatusIn(groupId(), membershipId, LoanService.UNFINISHED);
    }

    @Override
    public Money approvedNotDisbursed() {
        return loans.findByGroupIdAndStatusIn(groupId(), EnumSet.of(Status.APPROVED)).stream()
                .map(Loan::getPrincipal)
                .reduce(Money.ZERO, Money::plus);
    }

    private static long groupId() {
        return TenantContext.requireGroup().groupId();
    }
}
