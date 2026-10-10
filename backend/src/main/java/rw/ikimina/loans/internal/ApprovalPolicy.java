package rw.ikimina.loans.internal;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import rw.ikimina.groups.GroupRole;
import rw.ikimina.shared.money.Money;

/**
 * Who may approve a loan (spec 9.3, maker-checker - H9). Pure: callers pass the facts in.
 *
 * <ul>
 *   <li>Two approvals are needed when the amount is at or above the product's dual-approval
 *       threshold, or when the product has no threshold; otherwise one.</li>
 *   <li>Two approvals: one from the President and one from the Treasurer (different people).
 *       One approval: the President or the Treasurer.</li>
 *   <li>The borrower never approves their own loan. When the borrower is the President or the
 *       Treasurer, the Secretary approves in that office's place (owner confirmed, Phase 3).</li>
 * </ul>
 *
 * <p>Each approval fills one "slot"; an officer may approve only into a slot their current role
 * fits and that nobody has filled yet.
 */
final class ApprovalPolicy {

    private ApprovalPolicy() {
    }

    static int requiredApprovals(Money principal, Money dualApprovalThreshold) {
        return dualApprovalThreshold == null || principal.compareTo(dualApprovalThreshold) >= 0 ? 2 : 1;
    }

    /** The slots to fill, each the set of roles that may fill it. */
    static List<Set<GroupRole>> slots(GroupRole borrowerRole, int required) {
        GroupRole presidentSlot = borrowerRole == GroupRole.PRESIDENT ? GroupRole.SECRETARY : GroupRole.PRESIDENT;
        GroupRole treasurerSlot = borrowerRole == GroupRole.TREASURER ? GroupRole.SECRETARY : GroupRole.TREASURER;
        return required == 2
                ? List.of(EnumSet.of(presidentSlot), EnumSet.of(treasurerSlot))
                : List.of(EnumSet.of(presidentSlot, treasurerSlot));
    }

    /** Slots not yet filled by the approvals so far, given each approval's role at the time it was given. */
    static List<Set<GroupRole>> openSlots(GroupRole borrowerRole, int required, List<GroupRole> approvedAs) {
        List<Set<GroupRole>> open = new ArrayList<>(slots(borrowerRole, required));
        for (GroupRole role : approvedAs) {
            open.stream().filter(slot -> slot.contains(role)).findFirst().ifPresent(open::remove);
        }
        return List.copyOf(open);
    }

    /**
     * Whether {@code approverRole} may decide (approve or reject) now.
     *
     * @return the open slot the approver fills, or empty if they may not decide
     */
    static Optional<Set<GroupRole>> slotFor(GroupRole borrowerRole, int required, List<GroupRole> approvedAs, GroupRole approverRole,
                                            boolean approverIsBorrower) {
        if (approverIsBorrower) {
            return Optional.empty();
        }
        return openSlots(borrowerRole, required, approvedAs).stream().filter(slot -> slot.contains(approverRole)).findFirst();
    }

    /** The roles that may still approve, for screens that explain who the loan is waiting for. */
    static Set<GroupRole> waitingFor(GroupRole borrowerRole, int required, List<GroupRole> approvedAs) {
        Set<GroupRole> roles = EnumSet.noneOf(GroupRole.class);
        openSlots(borrowerRole, required, approvedAs).forEach(roles::addAll);
        return roles;
    }
}
