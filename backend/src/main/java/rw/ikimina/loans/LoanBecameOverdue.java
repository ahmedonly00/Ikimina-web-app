package rw.ikimina.loans;

import java.util.UUID;

/**
 * Published, inside the overdue job's transaction for the group, when a loan becomes OVERDUE
 * (spec 9.7: "emits events"). The notifications (Phase 6) and fines (Phase 4) engines will listen.
 *
 * @param borrowerMembershipId internal membership id
 */
public record LoanBecameOverdue(long groupId, UUID loanId, long borrowerMembershipId) {
}
