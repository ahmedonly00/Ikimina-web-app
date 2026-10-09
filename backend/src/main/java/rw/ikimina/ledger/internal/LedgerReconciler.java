package rw.ikimina.ledger.internal;

import java.util.ArrayList;
import java.util.List;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import rw.ikimina.ledger.ReconciliationReport;
import rw.ikimina.ledger.ReconciliationReport.GroupResult;
import rw.ikimina.shared.scheduling.SchedulerRunLog;
import rw.ikimina.shared.tenancy.TenantContext;

/**
 * Nightly ledger reconciliation (spec 7.1 #2, 21.4): recomputes every account's balance from its
 * lines and compares it with the stored projection, and checks every journal is balanced.
 * Runs group by group, each inside that group's tenant scope and its own transaction, so one
 * bad group neither hides nor blocks the others (spec 9.7).
 */
@Component
public class LedgerReconciler {

    static final String JOB_NAME = "ledger-reconciliation";
    private static final Logger log = LoggerFactory.getLogger(LedgerReconciler.class);

    private static final String MISMATCHES = """
            SELECT a.id
            FROM ledger_accounts a
            LEFT JOIN ledger_balances b ON b.account_id = a.id
            WHERE a.group_id = ?
              AND COALESCE(b.balance, 0) <> COALESCE((
                    SELECT SUM(CASE WHEN l.direction = a.normal_side THEN l.amount ELSE -l.amount END)
                    FROM ledger_lines l WHERE l.account_id = a.id), 0)
            ORDER BY a.id""";

    private static final String UNBALANCED = """
            SELECT j.id
            FROM ledger_journals j
            LEFT JOIN ledger_lines l ON l.journal_id = j.id
            WHERE j.group_id = ?
            GROUP BY j.id
            HAVING count(l.id) < 2
                OR COALESCE(SUM(l.amount) FILTER (WHERE l.direction = 'DEBIT'), 0)
                   <> COALESCE(SUM(l.amount) FILTER (WHERE l.direction = 'CREDIT'), 0)
            ORDER BY j.id""";

    private final JdbcTemplate jdbc;
    private final TransactionTemplate readOnly;
    private final SchedulerRunLog runs;

    LedgerReconciler(JdbcTemplate jdbc, PlatformTransactionManager transactionManager, SchedulerRunLog runs) {
        this.jdbc = jdbc;
        this.readOnly = new TransactionTemplate(transactionManager);
        this.readOnly.setReadOnly(true);
        this.runs = runs;
    }

    @Scheduled(cron = "0 0 3 * * *", zone = "Africa/Kigali")
    @SchedulerLock(name = JOB_NAME, lockAtMostFor = "PT2H")
    public void runNightly() {
        long runId = runs.start(JOB_NAME);
        try {
            ReconciliationReport report = reconcileAll();
            for (GroupResult problem : report.problems()) {
                // Alert hook (spec 21.4: "ledger reconciliation mismatch (page)").
                log.error("LEDGER_RECONCILIATION_MISMATCH group={} accounts={} unbalancedJournals={} error={}",
                        problem.groupId(), problem.mismatchedAccountIds(), problem.unbalancedJournalIds(), problem.error());
            }
            runs.finish(runId, report.clean() ? SchedulerRunLog.Status.SUCCEEDED : SchedulerRunLog.Status.FAILED,
                    report.groups().size(), report.clean() ? null : report.problems().size() + " group(s) with problems");
        } catch (RuntimeException e) {
            runs.finish(runId, SchedulerRunLog.Status.FAILED, 0, e.getClass().getSimpleName());
            throw e;
        }
    }

    public ReconciliationReport reconcileAll() {
        List<Long> groups = TenantContext.callAs(null, null, () -> readOnly.execute(status ->
                jdbc.queryForList("SELECT active_group_ids()", Long.class)));
        List<GroupResult> results = new ArrayList<>();
        for (long groupId : groups == null ? List.<Long>of() : groups) {
            results.add(reconcile(groupId));
        }
        return new ReconciliationReport(results);
    }

    public GroupResult reconcile(long groupId) {
        try {
            return TenantContext.callAs(null, new TenantContext.GroupScope(groupId, null, null, "SYSTEM"), () ->
                    readOnly.execute(status -> {
                        Integer accounts = jdbc.queryForObject("SELECT count(*) FROM ledger_accounts WHERE group_id = ?", Integer.class, groupId);
                        return new GroupResult(groupId, accounts == null ? 0 : accounts,
                                jdbc.queryForList(MISMATCHES, Long.class, groupId),
                                jdbc.queryForList(UNBALANCED, Long.class, groupId), null);
                    }));
        } catch (RuntimeException e) {
            log.error("Ledger reconciliation of group {} failed", groupId, e);
            return new GroupResult(groupId, 0, List.of(), List.of(), e.getClass().getSimpleName());
        }
    }
}
