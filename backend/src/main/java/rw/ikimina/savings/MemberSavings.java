package rw.ikimina.savings;

import rw.ikimina.shared.money.Money;

/**
 * The savings module's public view of a member's savings, for other modules. Acts on the group in
 * the current tenant scope.
 */
public interface MemberSavings {

    /**
     * The savings a loan limit is measured against (spec 9.2 "max multiple of member savings"):
     * every fund except the social fund, which is a shared safety net rather than the member's own
     * savings (owner decision, Phase 3).
     *
     * @param membershipId internal membership id
     */
    Money loanBasis(long membershipId);

    /** Withdrawals approved but not yet paid out: cash the group has already promised to members. */
    Money approvedWithdrawals();
}
