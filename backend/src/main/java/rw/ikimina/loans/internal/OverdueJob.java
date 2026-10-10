package rw.ikimina.loans.internal;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import rw.ikimina.loans.LoanBecameOverdue;
import rw.ikimina.loans.internal.LoanStateMachine.Status;
import rw.ikimina.shared.scheduling.SchedulerRunLog;
import rw.ikimina.shared.tenancy.TenantContext;
import rw.ikimina.shared.time.BusinessTime;

/**
 * Overdue detection (spec 9.7): nightly at 02:00 Africa/Kigali. Marks unpaid installments OVERDUE
 * once their due date plus the loan's grace days has passed, and their loans OVERDUE, publishing
 * {@link LoanBecameOverdue}. Idempotent - a second run finds nothing new - and resumable: each group
 * runs in its own transaction and tenant scope, and one failing group does not stop the others.
 */
@Component
class OverdueJob {

    static final String JOB_NAME = "loan-overdue";
    private static final Logger log = LoggerFactory.getLogger(OverdueJob.class);

    private final JdbcTemplate jdbc;
    private final LoanRepository loans;
    private final LoanInstallmentRepository installments;
    private final LoanStatusUpdater statusUpdater;
    private final SchedulerRunLog runs;
    private final TransactionTemplate transaction;
    private final Clock clock;

    OverdueJob(JdbcTemplate jdbc, LoanRepository loans, LoanInstallmentRepository installments, LoanStatusUpdater statusUpdater,
               SchedulerRunLog runs, PlatformTransactionManager transactionManager, Clock clock) {
        this.jdbc = jdbc;
        this.loans = loans;
        this.installments = installments;
        this.statusUpdater = statusUpdater;
        this.runs = runs;
        this.transaction = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    @Scheduled(cron = "0 0 2 * * *", zone = "Africa/Kigali")
    @SchedulerLock(name = JOB_NAME, lockAtMostFor = "PT2H")
    public void runNightly() {
        long runId = runs.start(JOB_NAME);
        int processed = 0;
        int failed = 0;
        List<Long> groups = TenantContext.callAs(null, null, () -> transaction.execute(status ->
                jdbc.queryForList("SELECT active_group_ids()", Long.class)));
        for (long groupId : groups == null ? List.<Long>of() : groups) {
            try {
                TenantContext.callAs(null, new TenantContext.GroupScope(groupId, null, null, "SYSTEM"), () ->
                        transaction.execute(status -> markOverdueInCurrentGroup()));
                processed++;
            } catch (RuntimeException e) {
                failed++;
                log.error("Overdue detection failed for group {}", groupId, e);
            }
        }
        runs.finish(runId, failed == 0 ? SchedulerRunLog.Status.SUCCEEDED : SchedulerRunLog.Status.FAILED, processed,
                failed == 0 ? null : failed + " group(s) failed");
    }

    /** @return how many loans became overdue */
    @Transactional(propagation = Propagation.MANDATORY)
    int markOverdueInCurrentGroup() {
        long groupId = TenantContext.requireGroup().groupId();
        LocalDate today = BusinessTime.today(clock);
        int newlyOverdue = 0;
        for (long loanId : installments.loansWithNewlyOverdueInstallments(groupId, today)) {
            Loan loan = loans.findByGroupIdAndIdForUpdate(groupId, loanId).orElseThrow();
            List<LoanInstallment> schedule = installments.findByLoanForUpdate(groupId, loanId);
            schedule.forEach(i -> i.refreshStatus(today, loan.getGraceDays()));
            Status before = statusUpdater.refresh(loan, schedule);
            installments.flush();
            loans.flush();
            if (before != Status.OVERDUE && loan.getStatus() == Status.OVERDUE) {
                newlyOverdue++;
            }
        }
        return newlyOverdue;
    }
}
