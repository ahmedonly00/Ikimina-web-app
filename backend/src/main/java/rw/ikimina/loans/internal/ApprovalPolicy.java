package rw.ikimina.loans.internal;

import java.util.ArrayList;
import java.util.Collection;
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
 *   <li>When one approval is needed and the group has three or more active officers, whoever
 *       approves cannot record the payout - and only the Treasurer records payouts - so the
 *       Treasurer does not give that approval: the President does, or the Secretary when the
 *       President is the borrower (owner decision, Phase 3 review).</li>
 * </ul>
 *
 * <p>Each approval fills one "slot"; an officer may approve only into a slot their role fits and
 * that nobody has filled yet. A loan is approved when no slot is left open.
 */
final class ApprovalPolicy {

    static final Set<GroupRole> OFFICES = EnumSet.of(GroupRole.PRESIDENT, GroupRole.TREASURER, GroupRole.SECRETARY);

    private ApprovalPolicy() {
    }

    static int requiredApprovals(Money principal, Money dualApprovalThreshold) {
        return dualApprovalThreshold == null || principal.compareTo(dualApprovalThreshold) >= 0 ? 2 : 1;
    }

    /** Spec 9.3 / owner decision: the approver of a single-approval loan cannot record its payout when 3+ officers are active. */
    static boolean separateDisburser(int required, long activeOfficers) {
        return required == 1 && activeOfficers >= 3;
    }

    /**
     * The slots to fill, each the set of roles that may fill it.
     *
     * @param borrowerRole      the borrower's role when the loan was requested
     * @param separateDisburser whether the approver must leave the payout to someone else (see {@link #separateDisburser})
     */
    static List<Set<GroupRole>> slots(GroupRole borrowerRole, int required, boolean separateDisburser) {
        GroupRole presidentSlot = borrowerRole == GroupRole.PRESIDENT ? GroupRole.SECRETARY : GroupRole.PRESIDENT;
        GroupRole treasurerSlot = borrowerRole == GroupRole.TREASURER ? GroupRole.SECRETARY : GroupRole.TREASURER;
        if (required == 2) {
            return List.of(EnumSet.of(presidentSlot), EnumSet.of(treasurerSlot));
        }
        Set<GroupRole> single = EnumSet.of(presidentSlot, treasurerSlot);
        if (separateDisburser) {
            single.remove(GroupRole.TREASURER);
        }
        return List.of(single);
    }

    /** Slots not yet filled by the approvals so far, given each approval's role at the time it was given. */
    static List<Set<GroupRole>> openSlots(GroupRole borrowerRole, int required, boolean separateDisburser, List<GroupRole> approvedAs) {
        List<Set<GroupRole>> open = new ArrayList<>(slots(borrowerRole, required, separateDisburser));
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
    static Optional<Set<GroupRole>> slotFor(GroupRole borrowerRole, int required, boolean separateDisburser, List<GroupRole> approvedAs,
                                            GroupRole approverRole, boolean approverIsBorrower) {
        if (approverIsBorrower) {
            return Optional.empty();
        }
        return openSlots(borrowerRole, required, separateDisburser, approvedAs).stream()
                .filter(slot -> slot.contains(approverRole)).findFirst();
    }

    /** The roles that may still approve, for screens that explain who the loan is waiting for. */
    static Set<GroupRole> waitingFor(GroupRole borrowerRole, int required, boolean separateDisburser, List<GroupRole> approvedAs) {
        Set<GroupRole> roles = EnumSet.noneOf(GroupRole.class);
        openSlots(borrowerRole, required, separateDisburser, approvedAs).forEach(roles::addAll);
        return roles;
    }

    /**
     * Spec 9.3: whether the officers in post can fill every slot. Offices are held by one person each,
     * so slots needing different roles always get different people.
     *
     * @param officesHeld the offices currently held by active members other than the borrower
     */
    static boolean approvable(GroupRole borrowerRole, int required, boolean separateDisburser, Collection<GroupRole> officesHeld) {
        return slots(borrowerRole, required, separateDisburser).stream().allMatch(slot -> slot.stream().anyMatch(officesHeld::contains));
    }
}
