package rw.ikimina.loans.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import rw.ikimina.groups.GroupRole;
import rw.ikimina.shared.money.Money;

/** Spec 9.3 approval rules and the Phase 3 owner decisions, officer combination by officer combination. */
class ApprovalPolicyTest {

    private static final GroupRole P = GroupRole.PRESIDENT;
    private static final GroupRole T = GroupRole.TREASURER;
    private static final GroupRole S = GroupRole.SECRETARY;
    private static final GroupRole A = GroupRole.AUDITOR;
    private static final GroupRole M = GroupRole.MEMBER;

    @Test
    void twoApprovalsFromTheThresholdUpAndAlwaysTwoWithoutAThreshold() {
        Money threshold = Money.ofWholeRwf(500_000);
        assertThat(ApprovalPolicy.requiredApprovals(Money.ofWholeRwf(499_999), threshold)).isEqualTo(1);
        assertThat(ApprovalPolicy.requiredApprovals(Money.ofWholeRwf(500_000), threshold)).isEqualTo(2);
        assertThat(ApprovalPolicy.requiredApprovals(Money.ofWholeRwf(1_000), null)).isEqualTo(2);
    }

    @Test
    void anOrdinaryBorrowerNeedsThePresidentAndTheTreasurerForTwoApprovals() {
        assertThat(may(M, 2, false, List.of(), P)).isTrue();
        assertThat(may(M, 2, false, List.of(), T)).isTrue();
        assertThat(may(M, 2, false, List.of(), S)).as("the Secretary only substitutes for a borrowing officer").isFalse();
        assertThat(may(M, 2, false, List.of(), A)).isFalse();
        assertThat(may(M, 2, false, List.of(), M)).isFalse();
        assertThat(may(M, 2, false, List.of(P), P)).as("a second President approval fills nothing").isFalse();
        assertThat(may(M, 2, false, List.of(P), T)).isTrue();
        assertThat(ApprovalPolicy.openSlots(M, 2, false, List.of(P, T))).isEmpty();
    }

    @Test
    void oneApprovalFromEitherThePresidentOrTheTreasurerWhenOfficersAreFew() {
        assertThat(may(M, 1, false, List.of(), P)).isTrue();
        assertThat(may(M, 1, false, List.of(), T)).isTrue();
        assertThat(may(M, 1, false, List.of(), S)).isFalse();
        assertThat(may(M, 1, false, List.of(T), P)).as("one approval is enough").isFalse();
    }

    @Test
    void withThreeOfficersTheTreasurerLeavesTheSingleApprovalToOthersSoTheyCanPayOut() {
        assertThat(ApprovalPolicy.separateDisburser(1, 3)).isTrue();
        assertThat(ApprovalPolicy.separateDisburser(1, 2)).isFalse();
        assertThat(ApprovalPolicy.separateDisburser(2, 5)).as("a dual approval already has a second signer").isFalse();

        assertThat(may(M, 1, true, List.of(), T)).isFalse();
        assertThat(may(M, 1, true, List.of(), P)).isTrue();
        assertThat(ApprovalPolicy.waitingFor(M, 1, true, List.of())).isEqualTo(Set.of(P));
        // A borrowing President: the Secretary stands in, and the Treasurer still stays out.
        assertThat(ApprovalPolicy.waitingFor(P, 1, true, List.of())).isEqualTo(Set.of(S));
        // A borrowing Treasurer: President or Secretary, as before.
        assertThat(ApprovalPolicy.waitingFor(T, 1, true, List.of())).isEqualTo(Set.of(P, S));
    }

    @Test
    void theSecretaryStandsInForABorrowingPresident() {
        assertThat(may(P, 2, false, List.of(), S)).isTrue();
        assertThat(may(P, 2, false, List.of(), T)).isTrue();
        assertThat(may(P, 2, false, List.of(S), T)).isTrue();
        assertThat(ApprovalPolicy.waitingFor(P, 2, false, List.of())).isEqualTo(Set.of(S, T));
        assertThat(may(P, 1, false, List.of(), S)).isTrue();
        assertThat(may(P, 1, false, List.of(), T)).isTrue();
    }

    @Test
    void theSecretaryStandsInForABorrowingTreasurer() {
        assertThat(may(T, 2, false, List.of(), P)).isTrue();
        assertThat(may(T, 2, false, List.of(), S)).isTrue();
        assertThat(ApprovalPolicy.waitingFor(T, 2, false, List.of(P))).isEqualTo(Set.of(S));
    }

    @Test
    void anApprovalFromARoleNoSlotNeedsLeavesTheLoanOpen() {
        // e.g. the Treasurer approved before the group reached three officers; now the President is still needed.
        assertThat(ApprovalPolicy.openSlots(M, 1, true, List.of(T))).hasSize(1);
        assertThat(ApprovalPolicy.openSlots(M, 1, true, List.of(T, P))).isEmpty();
    }

    @Test
    void theBorrowerNeverApprovesTheirOwnLoanWhateverTheirRole() {
        for (GroupRole role : GroupRole.values()) {
            for (int required = 1; required <= 2; required++) {
                for (boolean separate : new boolean[] {false, true}) {
                    assertThat(ApprovalPolicy.slotFor(role, required, separate, List.of(), role, true)).as("%s, %d", role, required)
                            .isEmpty();
                }
            }
        }
    }

    @Test
    void aLoanNobodyCouldApproveIsRecognised() {
        assertThat(ApprovalPolicy.approvable(M, 2, false, List.of(P, T))).isTrue();
        assertThat(ApprovalPolicy.approvable(M, 2, false, List.of(P))).as("no Treasurer").isFalse();
        assertThat(ApprovalPolicy.approvable(P, 2, false, List.of(T))).as("borrowing President, no Secretary").isFalse();
        assertThat(ApprovalPolicy.approvable(P, 2, false, List.of(T, S))).isTrue();
        assertThat(ApprovalPolicy.approvable(P, 1, true, List.of(T))).as("only the Secretary may approve here").isFalse();
        assertThat(ApprovalPolicy.approvable(M, 1, false, List.of(T))).isTrue();
    }

    private static boolean may(GroupRole borrower, int required, boolean separate, List<GroupRole> approvedAs, GroupRole approver) {
        return ApprovalPolicy.slotFor(borrower, required, separate, approvedAs, approver, false).isPresent();
    }
}
