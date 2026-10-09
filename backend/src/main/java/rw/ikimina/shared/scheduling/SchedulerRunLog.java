package rw.ikimina.shared.scheduling;

import java.time.Clock;
import java.time.ZoneOffset;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Records every scheduled-job run in {@code scheduler_runs} (spec 6.9, 9.7), in its own
 * transactions so a failing job still leaves a record of having started and failed.
 */
@Component
public class SchedulerRunLog {

    public enum Status { SUCCEEDED, FAILED }

    private final JdbcTemplate jdbc;
    private final TransactionTemplate ownTransaction;
    private final Clock clock;

    public SchedulerRunLog(JdbcTemplate jdbc, PlatformTransactionManager transactionManager, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.ownTransaction = new TransactionTemplate(transactionManager);
        this.ownTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** @return the run id to pass to {@link #finish} */
    public long start(String jobName) {
        Long id = ownTransaction.execute(status -> jdbc.queryForObject(
                "INSERT INTO scheduler_runs (job_name, started_at) VALUES (?, ?) RETURNING id",
                Long.class, jobName, clock.instant().atOffset(ZoneOffset.UTC)));
        if (id == null) {
            throw new IllegalStateException("scheduler_runs insert returned no id");
        }
        return id;
    }

    public void finish(long runId, Status status, int groupsProcessed, String error) {
        ownTransaction.executeWithoutResult(tx -> jdbc.update(
                "UPDATE scheduler_runs SET finished_at = ?, status = ?, groups_processed = ?, error = ? WHERE id = ?",
                clock.instant().atOffset(ZoneOffset.UTC), status.name(), groupsProcessed, error, runId));
    }
}
