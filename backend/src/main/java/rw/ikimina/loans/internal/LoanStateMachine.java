package rw.ikimina.loans.internal;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

import rw.ikimina.shared.error.ApiException;
import rw.ikimina.shared.error.ErrorCode;

/**
 * The loan lifecycle (spec 9.1), and the only place a loan's status is decided. Every service asks
 * {@link #next} before changing a loan, so no endpoint, role or token can skip a state.
 *
 * <pre>
 * SUBMITTED --approve--> PARTIALLY_COUNTERSIGNED --approve--> APPROVED --disburse--> DISBURSED <--> OVERDUE
 *     |  \--approve (all approvals in)------------------------^                         |             |
 *     reject / cancel        reject / cancel             cancel                       settle     settle / write-off
 * </pre>
 */
final class LoanStateMachine {

    enum Status {
        SUBMITTED, PARTIALLY_COUNTERSIGNED, APPROVED, DISBURSED, OVERDUE, SETTLED, REJECTED, CANCELLED, WRITTEN_OFF;

        boolean isOpen() {
            return this != SETTLED && this != REJECTED && this != CANCELLED && this != WRITTEN_OFF;
        }

        /** Disbursed and not yet settled: money is owed on it. */
        boolean isOutstanding() {
            return this == DISBURSED || this == OVERDUE;
        }
    }

    enum Action {
        /** An approval that still leaves approvals to collect. */
        COUNTERSIGN,
        /** The approval that completes the required approvals. */
        APPROVE,
        REJECT,
        CANCEL,
        DISBURSE,
        RECORD_REPAYMENT,
        MARK_OVERDUE,
        CLEAR_OVERDUE,
        SETTLE,
        /** A repayment reversal reopens a settled loan. */
        REOPEN,
        WRITE_OFF
    }

    private static final Map<Status, Map<Action, Status>> TRANSITIONS = new EnumMap<>(Status.class);

    static {
        allow(Status.SUBMITTED, Action.COUNTERSIGN, Status.PARTIALLY_COUNTERSIGNED);
        allow(Status.SUBMITTED, Action.APPROVE, Status.APPROVED);
        allow(Status.SUBMITTED, Action.REJECT, Status.REJECTED);
        allow(Status.SUBMITTED, Action.CANCEL, Status.CANCELLED);

        allow(Status.PARTIALLY_COUNTERSIGNED, Action.APPROVE, Status.APPROVED);
        allow(Status.PARTIALLY_COUNTERSIGNED, Action.REJECT, Status.REJECTED);
        allow(Status.PARTIALLY_COUNTERSIGNED, Action.CANCEL, Status.CANCELLED);

        allow(Status.APPROVED, Action.CANCEL, Status.CANCELLED);
        allow(Status.APPROVED, Action.DISBURSE, Status.DISBURSED);

        allow(Status.DISBURSED, Action.RECORD_REPAYMENT, Status.DISBURSED);
        allow(Status.DISBURSED, Action.MARK_OVERDUE, Status.OVERDUE);
        allow(Status.DISBURSED, Action.SETTLE, Status.SETTLED);

        allow(Status.OVERDUE, Action.RECORD_REPAYMENT, Status.OVERDUE);
        allow(Status.OVERDUE, Action.CLEAR_OVERDUE, Status.DISBURSED);
        allow(Status.OVERDUE, Action.SETTLE, Status.SETTLED);
        allow(Status.OVERDUE, Action.WRITE_OFF, Status.WRITTEN_OFF);

        allow(Status.SETTLED, Action.REOPEN, Status.DISBURSED);
    }

    private LoanStateMachine() {
    }

    private static void allow(Status from, Action action, Status to) {
        TRANSITIONS.computeIfAbsent(from, s -> new EnumMap<>(Action.class)).put(action, to);
    }

    static Optional<Status> target(Status from, Action action) {
        return Optional.ofNullable(TRANSITIONS.getOrDefault(from, Map.of()).get(action));
    }

    /** @throws ApiException LOAN_INVALID_TRANSITION when the action is not allowed in this status */
    static Status next(Status from, Action action) {
        return target(from, action).orElseThrow(() -> new ApiException(ErrorCode.LOAN_INVALID_TRANSITION));
    }
}
