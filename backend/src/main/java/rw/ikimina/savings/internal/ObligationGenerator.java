package rw.ikimina.savings.internal;

import java.sql.Date;
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
import rw.ikimina.groups.GroupMembers;
import rw.ikimina.shared.scheduling.SchedulerRunLog;
import rw.ikimina.shared.tenancy.TenantContext;
import rw.ikimina.shared.time.BusinessTime;

/**
 * Generates contribution obligations (spec 8.1): nightly for every group, and immediately for a
 * new bucket. Idempotent - the unique (bucket, member, period) key means re-running inserts
 * nothing new - and resumable: each group runs in its own transaction and tenant scope, and one
 * failing group does not stop the others (spec 9.7).
 */
@Component
class ObligationGenerator {

    static final String JOB_NAME = "obligation-generator";
    private static final Logger log = LoggerFactory.getLogger(ObligationGenerator.class);

    private static final String INSERT = """
            INSERT INTO contribution_obligations (group_id, bucket_id, membership_id, period_start, due_date, amount_due)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT (bucket_id, membership_id, period_start) DO NOTHING""";

    private final JdbcTemplate jdbc;
    private final SavingsBucketRepository buckets;
    private final ObligationRepository obligations;
    private final ObligationAllocator allocator;
    private final GroupMembers members;
    private final SchedulerRunLog runs;
    private final TransactionTemplate transaction;
    private final Clock clock;

    ObligationGenerator(JdbcTemplate jdbc, SavingsBucketRepository buckets, ObligationRepository obligations,
                        ObligationAllocator allocator, GroupMembers members, SchedulerRunLog runs,
                        PlatformTransactionManager transactionManager, Clock clock) {
        this.jdbc = jdbc;
        this.buckets = buckets;
        this.obligations = obligations;
        this.allocator = allocator;
        this.members = members;
        this.runs = runs;
        this.transaction = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    @Scheduled(cron = "0 15 1 * * *", zone = "Africa/Kigali")
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
                        transaction.execute(status -> {
                            generateForCurrentGroup();
                            return null;
                        }));
                processed++;
            } catch (RuntimeException e) {
                failed++;
                log.error("Obligation generation failed for group {}", groupId, e);
            }
        }
        runs.finish(runId, failed == 0 ? SchedulerRunLog.Status.SUCCEEDED : SchedulerRunLog.Status.FAILED, processed,
                failed == 0 ? null : failed + " group(s) failed");
    }

    /** All buckets of the group in the current tenant scope. */
    @Transactional(propagation = Propagation.MANDATORY)
    void generateForCurrentGroup() {
        long groupId = TenantContext.requireGroup().groupId();
        for (SavingsBucket bucket : buckets.findByGroupIdAndStatus(groupId, SavingsBucket.Status.ACTIVE)) {
            generate(groupId, bucket);
        }
        obligations.markOverdue(groupId, BusinessTime.today(clock));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    void generate(long groupId, SavingsBucket bucket) {
        if (!bucket.generatesObligations()) {
            return;
        }
        LocalDate today = BusinessTime.today(clock);
        LocalDate bucketCreated = BusinessTime.dateOf(bucket.getCreatedAt());
        for (GroupMembers.Member member : members.active()) {
            LocalDate joined = member.joinedAt() == null ? bucketCreated : BusinessTime.dateOf(member.joinedAt());
            LocalDate notBefore = joined.isAfter(bucketCreated) ? joined : bucketCreated;
            int created = 0;
            for (ObligationSchedule.Period period : ObligationSchedule.periodsDue(bucket.getStartDate(),
                    bucket.getContributionFrequency(), bucket.getEndDate(), today, notBefore)) {
                created += jdbc.update(INSERT, groupId, bucket.getId(), member.membershipId(), Date.valueOf(period.start()),
                        Date.valueOf(period.due()), bucket.getMinimumContribution().toStorageAmount());
            }
            if (created > 0) {
                // Credit carried from earlier overpayments settles the new obligations (owner decision).
                allocator.settle(groupId, member.membershipId(), bucket.getId(), null);
            }
        }
    }
}
