package rw.ikimina.savings;

import rw.ikimina.shared.money.Money;

/**
 * What the savings module needs to know about loans, declared here and implemented by the loans
 * module - which already depends on savings - so the two never depend on each other in a cycle.
 * Acts on the group in the current tenant scope.
 */
public interface LoanCommitments {

    /** Whether the member has a loan that is requested, approved or still being repaid (owner decision, Phase 3c). */
    boolean hasUnfinishedLoan(long membershipId);

    /** Loans approved but not yet handed over: cash the group has already promised (spec 9.2 "available funds"). */
    Money approvedNotDisbursed();
}
