package rw.ikimina.loans.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import rw.ikimina.groups.GroupRole;
import rw.ikimina.shared.money.Money;

/** Spec 9.3 approval rules, officer combination by officer combination. */
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
        assertThat(may(M, 2, List.of(), P)).isTrue();
        assertThat(may(M, 2, List.of(), T)).isTrue();
        assertThat(may(M, 2, List.of(), S)).as("the Secretary only substitutes for a borrowing officer").isFalse();
        assertThat(may(M, 2, List.of(), A)).isFalse();
        assertThat(may(M, 2, List.of(), M)).isFalse();
        assertThat(may(M, 2, List.of(P), P)).as("a second President approval fills nothing").isFalse();
        assertThat(may(M, 2, List.of(P), T)).isTrue();
        assertThat(ApprovalPolicy.openSlots(M, 2, List.of(P, T))).isEmpty();
    }

    @Test
    void oneApprovalFromEitherThePresidentOrTheTreasurer() {
        assertThat(may(M, 1, List.of(), P)).isTrue();
        assertThat(may(M, 1, List.of(), T)).isTrue();
        assertThat(may(M, 1, List.of(), S)).isFalse();
        assertThat(may(M, 1, List.of(T), P)).as("one approval is enough").isFalse();
    }

    @Test
    void theSecretaryStandsInForABorrowingPresident() {
        assertThat(may(P, 2, List.of(), S)).isTrue();
        assertThat(may(P, 2, List.of(), T)).isTrue();
        assertThat(may(P, 2, List.of(S), T)).isTrue();
        assertThat(ApprovalPolicy.waitingFor(P, 2, List.of())).isEqualTo(Set.of(S, T));
        assertThat(may(P, 1, List.of(), S)).isTrue();
        assertThat(may(P, 1, List.of(), T)).isTrue();
    }

    @Test
    void theSecretaryStandsInForABorrowingTreasurer() {
        assertThat(may(T, 2, List.of(), P)).isTrue();
        assertThat(may(T, 2, List.of(), S)).isTrue();
        assertThat(ApprovalPolicy.waitingFor(T, 2, List.of(P))).isEqualTo(Set.of(S));
    }

    @Test
    void theBorrowerNeverApprovesTheirOwnLoanWhateverTheirRole() {
        for (GroupRole role : GroupRole.values()) {
            for (int required = 1; required <= 2; required++) {
                assertThat(ApprovalPolicy.slotFor(role, required, List.of(), role, true)).as("%s, %d", role, required).isEmpty();
            }
        }
    }

    private static boolean may(GroupRole borrower, int required, List<GroupRole> approvedAs, GroupRole approver) {
        return ApprovalPolicy.slotFor(borrower, required, approvedAs, approver, false).isPresent();
    }
}
