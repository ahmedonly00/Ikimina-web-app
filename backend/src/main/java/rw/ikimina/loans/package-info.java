/**
 * Loan products, LoanStateMachine, maker-checker approvals, disbursement, schedules, repayments (spec 9).
 *
 * <p>Public API lives in this package. Implementation (entities, repositories,
 * services that other modules must not call) lives in {@code .internal} and is
 * reachable only from inside this module - enforced by {@code ArchitectureTest}.
 */
package rw.ikimina.loans;
