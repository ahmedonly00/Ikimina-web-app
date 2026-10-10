package rw.ikimina.loans.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import rw.ikimina.loans.internal.LoanStateMachine.Action;
import rw.ikimina.loans.internal.LoanStateMachine.Status;
import rw.ikimina.shared.error.ApiException;
import rw.ikimina.shared.error.ErrorCode;

/**
 * Phase 3 acceptance: every illegal transition is rejected. The expected table is written out here
 * from spec 9.1, independently of the class under test, and every (status x action) pair is checked.
 */
class LoanStateMachineTest {

    private static final Map<Status, Map<Action, Status>> SPEC_9_1 = Map.of(
            Status.SUBMITTED, Map.of(Action.COUNTERSIGN, Status.PARTIALLY_COUNTERSIGNED, Action.APPROVE, Status.APPROVED,
                    Action.REJECT, Status.REJECTED, Action.CANCEL, Status.CANCELLED),
            Status.PARTIALLY_COUNTERSIGNED, Map.of(Action.APPROVE, Status.APPROVED, Action.REJECT, Status.REJECTED,
                    Action.CANCEL, Status.CANCELLED),
            Status.APPROVED, Map.of(Action.CANCEL, Status.CANCELLED, Action.DISBURSE, Status.DISBURSED),
            Status.DISBURSED, Map.of(Action.RECORD_REPAYMENT, Status.DISBURSED, Action.MARK_OVERDUE, Status.OVERDUE,
                    Action.SETTLE, Status.SETTLED),
            Status.OVERDUE, Map.of(Action.RECORD_REPAYMENT, Status.OVERDUE, Action.CLEAR_OVERDUE, Status.DISBURSED,
                    Action.SETTLE, Status.SETTLED, Action.WRITE_OFF, Status.WRITTEN_OFF),
            Status.SETTLED, Map.of(Action.REOPEN, Status.DISBURSED),
            Status.REJECTED, Map.of(),
            Status.CANCELLED, Map.of(),
            Status.WRITTEN_OFF, Map.of());

    @Test
    void everyStatusAndActionPairBehavesAsSpec91Says() {
        List<String> mismatches = new ArrayList<>();
        for (Status from : Status.values()) {
            for (Action action : Action.values()) {
                Status expected = SPEC_9_1.get(from).get(action);
                if (expected == null) {
                    try {
                        Status got = LoanStateMachine.next(from, action);
                        mismatches.add(from + " --" + action + "--> " + got + " should be refused");
                    } catch (ApiException e) {
                        if (e.code() != ErrorCode.LOAN_INVALID_TRANSITION) {
                            mismatches.add(from + " --" + action + " refused with " + e.code());
                        }
                    }
                } else if (LoanStateMachine.target(from, action).orElse(null) != expected) {
                    mismatches.add(from + " --" + action + "--> expected " + expected);
                }
            }
        }
        assertThat(mismatches).isEmpty();
        assertThat(SPEC_9_1).containsOnlyKeys(Status.values());
    }

    @Test
    void noPathSkipsApprovalOrDisbursement() {
        // Disbursing is possible only from APPROVED, and APPROVED is reachable only by approving.
        for (Status from : Status.values()) {
            if (from != Status.APPROVED) {
                assertThatThrownBy(() -> LoanStateMachine.next(from, Action.DISBURSE)).isInstanceOf(ApiException.class);
            }
            for (Action action : Action.values()) {
                if (action != Action.APPROVE) {
                    assertThat(LoanStateMachine.target(from, action)).isNotEqualTo(java.util.Optional.of(Status.APPROVED));
                }
            }
        }
    }

    @Test
    void finishedLoansAreClosed() {
        assertThat(Status.SETTLED.isOpen()).isFalse();
        assertThat(Status.REJECTED.isOpen()).isFalse();
        assertThat(Status.CANCELLED.isOpen()).isFalse();
        assertThat(Status.WRITTEN_OFF.isOpen()).isFalse();
        assertThat(Status.DISBURSED.isOutstanding() && Status.OVERDUE.isOutstanding()).isTrue();
        assertThat(Status.APPROVED.isOutstanding()).isFalse();
    }
}
