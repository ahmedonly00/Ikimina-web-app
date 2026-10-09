package rw.ikimina.audit.internal;

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
import rw.ikimina.audit.AuditChainReport;
import rw.ikimina.audit.AuditChainReport.ChainResult;
import rw.ikimina.shared.scheduling.SchedulerRunLog;
import rw.ikimina.shared.tenancy.TenantContext;

/**
 * Recomputes every audit chain and reports any break (spec 16.6: "a nightly verifier
 * recomputes and alerts on a broken chain"). Uses the same {@code audit_canonical}
 * function as the writer, so a mismatch means a row was altered, removed or inserted
 * out of band - never a formatting difference.
 *
 * <p>Runs chain by chain, each in its own transaction scoped to that group, so row-level
 * security still applies and one broken chain does not stop the others (spec 9.7).
 */
@Component
public class AuditChainVerifier {

    static final String JOB_NAME = "audit-chain-verifier";
    private static final String GENESIS = "0".repeat(64);
    private static final Logger log = LoggerFactory.getLogger(AuditChainVerifier.class);

    private static final String VERIFY = """
            SELECT id,
                   row_hash = encode(sha256(convert_to(prev_hash || audit_canonical(a), 'UTF8')), 'hex') AS hash_ok,
                   prev_hash = COALESCE(lag(row_hash) OVER (ORDER BY chain_seq), ?) AS link_ok,
                   chain_seq = row_number() OVER (ORDER BY chain_seq) AS seq_ok,
                   row_hash
            FROM audit_logs a
            WHERE group_id IS NOT DISTINCT FROM ?
            ORDER BY chain_seq""";

    private record Row(long id, boolean hashOk, boolean linkOk, boolean seqOk, String rowHash) {
    }

    private record Head(long chainKey, String lastHash, long lastSeq) {
    }

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final SchedulerRunLog runs;

    public AuditChainVerifier(JdbcTemplate jdbc, PlatformTransactionManager transactionManager, SchedulerRunLog runs) {
        this.jdbc = jdbc;
        this.transaction = new TransactionTemplate(transactionManager);
        this.transaction.setReadOnly(true);
        this.runs = runs;
    }

    @Scheduled(cron = "0 30 2 * * *", zone = "Africa/Kigali")
    @SchedulerLock(name = JOB_NAME, lockAtMostFor = "PT2H")
    public void runNightly() {
        long runId = runs.start(JOB_NAME);
        try {
            AuditChainReport report = verifyAll();
            for (ChainResult broken : report.broken()) {
                // Alert hook: this line is what monitoring pages on (spec 21.4).
                log.error("AUDIT_CHAIN_BROKEN chain={} firstBrokenAuditId={} problem={}",
                        broken.chainKey(), broken.firstBrokenAuditId(), broken.problem());
            }
            runs.finish(runId, report.allIntact() ? SchedulerRunLog.Status.SUCCEEDED : SchedulerRunLog.Status.FAILED,
                    report.chains().size(), report.allIntact() ? null : report.broken().size() + " broken chain(s)");
        } catch (RuntimeException e) {
            runs.finish(runId, SchedulerRunLog.Status.FAILED, 0, e.getClass().getSimpleName());
            throw e;
        }
    }

    public AuditChainReport verifyAll() {
        List<Head> heads = TenantContext.callAs(null, null, () -> transaction.execute(status ->
                jdbc.query("SELECT chain_key, last_hash, last_seq FROM audit_chain_heads ORDER BY chain_key",
                        (rs, n) -> new Head(rs.getLong(1), rs.getString(2), rs.getLong(3)))));
        List<ChainResult> results = new ArrayList<>();
        for (Head head : heads == null ? List.<Head>of() : heads) {
            results.add(verifyChain(head));
        }
        return new AuditChainReport(results);
    }

    private ChainResult verifyChain(Head head) {
        boolean platform = head.chainKey() == 0;
        TenantContext.GroupScope scope = platform ? null
                : new TenantContext.GroupScope(head.chainKey(), null, null, "SYSTEM");
        try {
            return TenantContext.callAs(null, scope, () -> transaction.execute(status -> {
                List<Row> rows = jdbc.query(VERIFY,
                        (rs, n) -> new Row(rs.getLong("id"), rs.getBoolean("hash_ok"), rs.getBoolean("link_ok"),
                                rs.getBoolean("seq_ok"), rs.getString("row_hash")),
                        GENESIS, platform ? null : head.chainKey());
                return check(head, rows);
            }));
        } catch (RuntimeException e) {
            log.error("Audit chain {} could not be verified", head.chainKey(), e);
            return new ChainResult(head.chainKey(), 0, null, "verification failed: " + e.getClass().getSimpleName());
        }
    }

    private static ChainResult check(Head head, List<Row> rows) {
        for (Row row : rows) {
            if (!row.hashOk()) {
                return new ChainResult(head.chainKey(), rows.size(), row.id(), "row content does not match its hash");
            }
            if (!row.seqOk()) {
                return new ChainResult(head.chainKey(), rows.size(), row.id(), "a row is missing before this one");
            }
            if (!row.linkOk()) {
                return new ChainResult(head.chainKey(), rows.size(), row.id(), "row does not link to the previous row");
            }
        }
        String tail = rows.isEmpty() ? GENESIS : rows.getLast().rowHash();
        if (!tail.equals(head.lastHash()) || rows.size() != head.lastSeq()) {
            return new ChainResult(head.chainKey(), rows.size(), null, "chain head does not match the last row");
        }
        return new ChainResult(head.chainKey(), rows.size(), null, null);
    }
}
