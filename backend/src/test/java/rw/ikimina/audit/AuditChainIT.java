package rw.ikimina.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import rw.ikimina.audit.AuditChainReport.ChainResult;
import rw.ikimina.audit.internal.AuditChainVerifier;
import rw.ikimina.support.GroupFixture;
import rw.ikimina.support.GroupFixture.TestGroup;
import rw.ikimina.support.IntegrationTest;
import rw.ikimina.support.PostgresTestDatabase;
import rw.ikimina.shared.tenancy.TenantContext;

/**
 * Phase 1 acceptance: the audit hash chain verifier works (spec 16.6). Each test uses a
 * group of its own and reads only that group's chain, because the tamper tests deliberately
 * break theirs.
 */
class AuditChainIT extends IntegrationTest {

    private static final String DB = "app_it";

    @Autowired
    private AuditChainVerifier verifier;

    @Autowired
    private AuditService audit;

    @Autowired
    private PlatformTransactionManager transactions;

    @Test
    void realActivityProducesAnIntactChain() throws SQLException {
        TestGroup group = new GroupFixture(api(), sms).createWithEveryRole("Audited Group");
        ChainResult chain = chainOf(group);
        assertThat(chain.intact()).as(chain.problem()).isTrue();
        assertThat(chain.rowsChecked()).isGreaterThanOrEqualTo(9);   // created + 4 invited + 4 joined + 2 roles
        assertThat(actions(group)).contains("GROUP_CREATED", "MEMBER_INVITED", "MEMBER_JOINED", "MEMBER_ROLE_CHANGED");
    }

    @Test
    void concurrentWritersDoNotForkTheChain() throws Exception {
        TestGroup group = new GroupFixture(api(), sms).create("Concurrent Audit");
        long groupId = internalGroupId(group);
        int writers = 24;
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Callable<Void>> tasks = new ArrayList<>();
            for (int i = 0; i < writers; i++) {
                int n = i;
                tasks.add(() -> {
                    inGroup(groupId, () -> audit.record(AuditEvent.of("CONCURRENCY_PROBE").entity("probe", n)));
                    return null;
                });
            }
            for (Future<Void> result : pool.invokeAll(tasks)) {
                result.get();
            }
        } finally {
            pool.shutdown();
        }
        ChainResult chain = chainOf(group);
        assertThat(chain.intact()).as(chain.problem()).isTrue();
        assertThat(actions(group).stream().filter("CONCURRENCY_PROBE"::equals).count()).isEqualTo(writers);
    }

    @Test
    void anAuditRowRollsBackWithTheChangeItDescribes() throws SQLException {
        TestGroup group = new GroupFixture(api(), sms).create("Rolled Back");
        long groupId = internalGroupId(group);
        TransactionTemplate tx = new TransactionTemplate(transactions);
        TenantContext.callAs(null, new TenantContext.GroupScope(groupId, null, null, "SYSTEM"), () -> {
            tx.executeWithoutResult(status -> {
                audit.record(AuditEvent.of("NEVER_HAPPENED"));
                status.setRollbackOnly();
            });
            return null;
        });
        assertThat(actions(group)).doesNotContain("NEVER_HAPPENED");
        assertThat(chainOf(group).intact()).isTrue();
    }

    @Test
    void auditOutsideATransactionIsRefused() {
        assertThatThrownBy(() -> audit.record(AuditEvent.of("NO_TRANSACTION")))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void theApplicationRoleCannotAlterAuditRows() throws SQLException {
        TestGroup group = new GroupFixture(api(), sms).create("Read Only Audit");
        long groupId = internalGroupId(group);
        try (Connection app = PostgresTestDatabase.connectAsApp(DB)) {
            app.setAutoCommit(false);
            try (Statement statement = app.createStatement()) {
                statement.execute("SELECT set_config('app.current_group_id', '" + groupId + "', true)");
                assertThatThrownBy(() -> statement.executeUpdate("UPDATE audit_logs SET reason = 'edited' WHERE group_id = " + groupId))
                        .isInstanceOf(SQLException.class).hasMessageContaining("permission denied");
            }
            app.rollback();
        }
    }

    @Test
    void editingARowIsDetected() throws SQLException {
        TestGroup group = new GroupFixture(api(), sms).createWithEveryRole("Edited Row");
        long target = auditIds(group).get(2);
        asInsider("UPDATE audit_logs SET reason = 'quietly edited' WHERE id = " + target);
        ChainResult chain = chainOf(group);
        assertThat(chain.intact()).isFalse();
        assertThat(chain.firstBrokenAuditId()).isEqualTo(target);
    }

    @Test
    void deletingARowIsDetected() throws SQLException {
        TestGroup group = new GroupFixture(api(), sms).createWithEveryRole("Deleted Row");
        List<Long> ids = auditIds(group);
        asInsider("DELETE FROM audit_logs WHERE id = " + ids.get(3));
        ChainResult chain = chainOf(group);
        assertThat(chain.intact()).isFalse();
        assertThat(chain.firstBrokenAuditId()).isEqualTo(ids.get(4));
    }

    @Test
    void cuttingOffTheTailIsDetected() throws SQLException {
        TestGroup group = new GroupFixture(api(), sms).createWithEveryRole("Truncated Tail");
        List<Long> ids = auditIds(group);
        asInsider("DELETE FROM audit_logs WHERE id = " + ids.getLast());
        ChainResult chain = chainOf(group);
        assertThat(chain.intact()).isFalse();
        assertThat(chain.problem()).contains("head");
    }

    private ChainResult chainOf(TestGroup group) throws SQLException {
        long groupId = internalGroupId(group);
        return verifier.verifyAll().chains().stream()
                .filter(chain -> chain.chainKey() == groupId)
                .findFirst()
                .orElseThrow();
    }

    private void inGroup(long groupId, Runnable work) {
        TransactionTemplate tx = new TransactionTemplate(transactions);
        TenantContext.callAs(null, new TenantContext.GroupScope(groupId, null, null, "SYSTEM"), () -> {
            tx.executeWithoutResult(status -> work.run());
            return null;
        });
    }

    /** Someone with full database access, bypassing the immutability trigger to cover their tracks. */
    private static void asInsider(String sql) throws SQLException {
        try (Connection superuser = PostgresTestDatabase.connectAsSuperuser(DB); Statement statement = superuser.createStatement()) {
            statement.execute("SET search_path = ikimina");
            statement.execute("ALTER TABLE audit_logs DISABLE TRIGGER trg_audit_immutable");
            try {
                assertThat(statement.executeUpdate(sql)).isEqualTo(1);
            } finally {
                statement.execute("ALTER TABLE audit_logs ENABLE TRIGGER trg_audit_immutable");
            }
        }
    }

    private static List<Long> auditIds(TestGroup group) throws SQLException {
        List<Long> ids = new ArrayList<>();
        for (Object[] row : rows(group, "SELECT id FROM audit_logs WHERE group_id = ? ORDER BY id")) {
            ids.add((Long) row[0]);
        }
        return ids;
    }

    private static List<String> actions(TestGroup group) throws SQLException {
        List<String> actions = new ArrayList<>();
        for (Object[] row : rows(group, "SELECT action FROM audit_logs WHERE group_id = ? ORDER BY id")) {
            actions.add((String) row[0]);
        }
        return actions;
    }

    private static List<Object[]> rows(TestGroup group, String sql) throws SQLException {
        long groupId = internalGroupId(group);
        try (Connection owner = PostgresTestDatabase.connectAsOwner(DB); PreparedStatement statement = owner.prepareStatement(sql)) {
            statement.setLong(1, groupId);
            List<Object[]> result = new ArrayList<>();
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    result.add(new Object[] {rs.getObject(1)});
                }
            }
            return result;
        }
    }

    private static long internalGroupId(TestGroup group) throws SQLException {
        try (Connection owner = PostgresTestDatabase.connectAsOwner(DB);
             PreparedStatement statement = owner.prepareStatement("SELECT id FROM groups WHERE public_id = ?")) {
            statement.setObject(1, UUID.fromString(group.groupId()));
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }
}
