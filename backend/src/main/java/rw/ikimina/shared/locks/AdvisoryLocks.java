package rw.ikimina.shared.locks;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * PostgreSQL transaction-scoped advisory locks, by namespace, for rules that span rows no single row
 * lock covers. Every namespace is declared here, so two modules never pick the same number by accident.
 * Take an advisory lock before any row lock in the same transaction, so it cannot join a lock cycle.
 */
public final class AdvisoryLocks {

    /** One loan request at a time per member: the open-loan rule (spec 9.2). Key: membership id. */
    public static final int LOAN_REQUEST = 7_303;

    /**
     * One payout at a time per group - loan disbursements and savings withdrawals both spend the group's
     * cash, so two payouts cannot both pass the funds check. Key: group id.
     */
    public static final int GROUP_PAYOUT = 7_304;

    /** One withdrawal step at a time per member: a member's pending requests must fit their balance. Key: membership id. */
    public static final int MEMBER_WITHDRAWAL = 7_305;

    private AdvisoryLocks() {
    }

    /** Waits for, then holds until the transaction ends, the lock {@code (namespace, key)}. */
    public static void lock(JdbcTemplate jdbc, int namespace, long key) {
        jdbc.queryForList("SELECT pg_advisory_xact_lock(?, ?)", namespace, (int) key);
    }
}
