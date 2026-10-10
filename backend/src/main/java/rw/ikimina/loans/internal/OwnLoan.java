package rw.ikimina.loans.internal;

import rw.ikimina.groups.GroupMembers;
import rw.ikimina.shared.security.CurrentUser;

/**
 * Money recorded by the borrower on their own loan - possible when the Treasurer borrows, since only
 * the Treasurer records money. It is allowed but flagged in the audit log and on the loan page, for
 * the Auditor and the President to check (owner decision, Phase 3 review).
 */
final class OwnLoan {

    static final String REASON = "recorded by the borrower on their own loan";

    private OwnLoan() {
    }

    /** Whether the signed-in user is the loan's borrower. */
    static boolean recordedByBorrower(GroupMembers members, Loan loan) {
        return isBorrower(members, loan, CurrentUser.require().id());
    }

    static boolean isBorrower(GroupMembers members, Loan loan, long userId) {
        return members.findById(loan.getBorrowerMembershipId()).map(m -> m.userId() == userId).orElse(false);
    }
}
