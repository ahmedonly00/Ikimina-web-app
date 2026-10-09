package rw.ikimina.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import rw.ikimina.groups.GroupRole;
import rw.ikimina.ledger.internal.LedgerReconciler;
import rw.ikimina.shared.error.ApiException;
import rw.ikimina.shared.money.Money;
import rw.ikimina.support.GroupFixture;
import rw.ikimina.support.GroupFixture.TestGroup;
import rw.ikimina.support.IntegrationTest;
import rw.ikimina.support.LedgerTestSupport;
import rw.ikimina.support.LedgerTestSupport.Actor;
import rw.ikimina.support.PostgresTestDatabase;

/** Spec 7.4: the mandatory ledger tests (Phase 2 acceptance B1, B3, B4). */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LedgerIT extends IntegrationTest {

    @Autowired
    private Ledger ledger;

    @Autowired
    private LedgerReconciler reconciler;

    @Autowired
    private PlatformTransactionManager transactions;

    private LedgerTestSupport support;

    @BeforeAll
    void support() {
        support = new LedgerTestSupport(transactions);
    }

    /** A fresh group per test, with its Treasurer as the actor and the members' internal ids. */
    private record Setup(Actor treasurer, List<Long> memberships, long bucketId) {
    }

    private Setup setup(String name) throws SQLException {
        TestGroup group = new GroupFixture(api(), sms).createWithEveryRole(name);
        String bucket = api().post(group.path("/buckets"), group.president(), Map.of("name", "Ubwizigame", "type", "SAVINGS",
                        "cycleType", "ROLLING", "startDate", "2026-01-01", "terms", Map.of("mandatory", false,
                                "minimumContribution", "0", "contributionFrequency", "ADHOC", "withdrawable", false)))
                .expect(201).text("bucketId");
        List<Long> memberships = new ArrayList<>();
        for (GroupRole role : GroupRole.values()) {
            memberships.add(LedgerTestSupport.actor(group.groupId(), group.memberId(role)).membershipId());
        }
        return new Setup(LedgerTestSupport.actor(group.groupId(), group.memberId(GroupRole.TREASURER)), memberships,
                LedgerTestSupport.bucketId(bucket));
    }

    private static JournalRequest contribution(String key, long membership, long bucket, long amount) {
        return new JournalRequest(JournalType.CONTRIBUTION, key, "hash-" + key, LocalDate.of(2026, 3, 1), "test", JournalSource.MANUAL,
                null, membership, List.of(
                JournalRequest.Line.debit(AccountRef.of(AccountType.GROUP_CASH), Money.ofWholeRwf(amount)),
                JournalRequest.Line.credit(AccountRef.memberSavings(membership, bucket), Money.ofWholeRwf(amount))));
    }

    // --- property-based: random sequences keep the books consistent (spec 7.4 #1) -------------

    @ParameterizedTest(name = "seed {0}")
    @ValueSource(longs = {1, 7, 42, 1_000, 31_337})
    void randomPostingsAndReversalsKeepEveryInvariant(long seed) throws SQLException {
        Setup setup = setup("Property " + seed);
        Random random = new Random(seed);
        List<AccountRef> accounts = new ArrayList<>(List.of(AccountRef.of(AccountType.GROUP_CASH), AccountRef.of(AccountType.EXPENSE),
                AccountRef.of(AccountType.INTEREST_INCOME), AccountRef.of(AccountType.FINE_INCOME)));
        setup.memberships().forEach(m -> accounts.add(AccountRef.memberSavings(m, setup.bucketId())));

        Map<AccountRef, Money> expected = new HashMap<>();
        Map<Long, List<JournalRequest.Line>> posted = new HashMap<>();
        List<Long> reversible = new ArrayList<>();

        for (int i = 0; i < 150; i++) {
            if (!reversible.isEmpty() && random.nextInt(6) == 0) {
                long target = reversible.remove(random.nextInt(reversible.size()));
                support.as(setup.treasurer(), () -> ledger.reverse(target, "property test"));
                posted.get(target).forEach(line -> apply(expected, line.account(), line.direction().opposite(), line.amount()));
                continue;
            }
            List<JournalRequest.Line> lines = randomBalancedLines(random, accounts);
            JournalRequest request = new JournalRequest(JournalType.ADJUSTMENT, "p" + seed + "-" + i, "h" + i,
                    LocalDate.of(2026, 1, 1).plusDays(random.nextInt(200)), "property", JournalSource.MANUAL, null, null, lines);
            PostedJournal journal = support.as(setup.treasurer(), () -> ledger.post(request));
            posted.put(journal.id(), lines);
            reversible.add(journal.id());
            lines.forEach(line -> apply(expected, line.account(), line.direction(), line.amount()));
        }

        for (Map.Entry<AccountRef, Money> entry : expected.entrySet()) {
            assertThat(support.as(setup.treasurer(), () -> ledger.balance(entry.getKey()))).as(entry.getKey().toString())
                    .isEqualTo(entry.getValue());
        }
        assertThat(reconciler.reconcile(setup.treasurer().groupId()).clean()).isTrue();
        assertAccountingEquation(setup.treasurer().groupId());
    }

    private static List<JournalRequest.Line> randomBalancedLines(Random random, List<AccountRef> accounts) {
        int debits = 1 + random.nextInt(2);
        int credits = 1 + random.nextInt(2);
        List<AccountRef> shuffled = new ArrayList<>(accounts);
        Collections.shuffle(shuffled, random);
        List<JournalRequest.Line> lines = new ArrayList<>();
        long total = 0;
        for (int d = 0; d < debits; d++) {
            long amount = 1 + random.nextInt(100_000);
            total += amount;
            lines.add(JournalRequest.Line.debit(shuffled.get(d), Money.ofWholeRwf(amount)));
        }
        long remaining = total;
        for (int c = 0; c < credits; c++) {
            long amount = c == credits - 1 ? remaining : 1 + random.nextLong(Math.max(1, remaining - (credits - c - 1)));
            remaining -= amount;
            if (amount > 0) {
                lines.add(JournalRequest.Line.credit(shuffled.get(debits + c), Money.ofWholeRwf(amount)));
            }
        }
        return lines;
    }

    private static void apply(Map<AccountRef, Money> balances, AccountRef account, Direction direction, Money amount) {
        Money signed = direction == account.type().normalSide() ? amount : amount.negate();
        balances.merge(account, signed, Money::plus);
    }

    /** Every journal balanced means debit-normal balances sum to credit-normal balances. */
    private static void assertAccountingEquation(long groupId) throws SQLException {
        try (Connection owner = PostgresTestDatabase.connectAsOwner("app_it");
             PreparedStatement statement = owner.prepareStatement("""
                     SELECT COALESCE(SUM(b.balance) FILTER (WHERE a.normal_side = 'DEBIT'), 0),
                            COALESCE(SUM(b.balance) FILTER (WHERE a.normal_side = 'CREDIT'), 0)
                     FROM ledger_accounts a JOIN ledger_balances b ON b.account_id = a.id WHERE a.group_id = ?""")) {
            statement.setLong(1, groupId);
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                assertThat(rs.getBigDecimal(1)).isEqualByComparingTo(rs.getBigDecimal(2));
            }
        }
    }

    // --- idempotency (spec 7.4 #3, H7) ---------------------------------------------------------

    @Test
    void theSameKeyTwiceGivesOneJournal() throws SQLException {
        Setup setup = setup("Idempotent");
        long member = setup.memberships().getLast();
        PostedJournal first = support.as(setup.treasurer(), () -> ledger.post(contribution("same-key", member, setup.bucketId(), 5_000)));
        PostedJournal again = support.as(setup.treasurer(), () -> ledger.post(contribution("same-key", member, setup.bucketId(), 5_000)));

        assertThat(again.id()).isEqualTo(first.id());
        assertThat(again.replayed()).isTrue();
        assertThat(support.as(setup.treasurer(), () -> ledger.balance(AccountRef.memberSavings(member, setup.bucketId()))))
                .isEqualTo(Money.ofWholeRwf(5_000));
    }

    @Test
    void theSameKeyWithADifferentRequestIsRefused() throws SQLException {
        Setup setup = setup("Key Reuse");
        long member = setup.memberships().getLast();
        support.as(setup.treasurer(), () -> ledger.post(contribution("reused", member, setup.bucketId(), 5_000)));
        JournalRequest different = new JournalRequest(JournalType.CONTRIBUTION, "reused", "another-hash", LocalDate.of(2026, 3, 1),
                "test", JournalSource.MANUAL, null, member, contribution("x", member, setup.bucketId(), 9_000).lines());
        assertThatThrownBy(() -> support.as(setup.treasurer(), () -> ledger.post(different)))
                .isInstanceOf(ApiException.class).hasMessage("IDEMPOTENCY_CONFLICT");
    }

    // --- validation and rounding ---------------------------------------------------------------

    @Test
    void anUnbalancedRequestWritesNothing() throws SQLException {
        Setup setup = setup("Unbalanced");
        JournalRequest unbalanced = new JournalRequest(JournalType.ADJUSTMENT, "unbalanced", "h", LocalDate.of(2026, 3, 1), "x",
                JournalSource.MANUAL, null, null, List.of(
                JournalRequest.Line.debit(AccountRef.of(AccountType.GROUP_CASH), Money.ofWholeRwf(100)),
                JournalRequest.Line.credit(AccountRef.of(AccountType.FINE_INCOME), Money.ofWholeRwf(99))));
        assertThatThrownBy(() -> support.as(setup.treasurer(), () -> ledger.post(unbalanced))).isInstanceOf(IllegalArgumentException.class);
        assertThat(journalCount(setup.treasurer().groupId())).isZero();
    }

    @Test
    void amountsAreRoundedToWholeFrancsAtThePostingBoundary() throws SQLException {
        Setup setup = setup("Rounding");
        Money amount = Money.of(new BigDecimal("1000.50"));
        support.as(setup.treasurer(), () -> ledger.post(new JournalRequest(JournalType.ADJUSTMENT, "round", "h", LocalDate.of(2026, 3, 1),
                "x", JournalSource.MANUAL, null, null, List.of(
                JournalRequest.Line.debit(AccountRef.of(AccountType.GROUP_CASH), amount),
                JournalRequest.Line.credit(AccountRef.of(AccountType.EQUITY), amount)))));
        assertThat(support.as(setup.treasurer(), () -> ledger.balance(AccountRef.of(AccountType.GROUP_CASH))))
                .isEqualTo(Money.ofWholeRwf(1_001));
    }

    // --- immutability (spec 7.4 #4, H5) ----------------------------------------------------------

    @Test
    void journalsAndLinesCannotBeChangedByTheApplicationOrByAnyone() throws SQLException {
        Setup setup = setup("Immutable");
        long member = setup.memberships().getLast();
        support.as(setup.treasurer(), () -> ledger.post(contribution("immutable", member, setup.bucketId(), 1_000)));
        long groupId = setup.treasurer().groupId();

        try (Connection app = PostgresTestDatabase.connectAsApp("app_it"); Statement statement = app.createStatement()) {
            app.setAutoCommit(false);
            statement.execute("SELECT set_config('app.current_group_id', '" + groupId + "', true)");
            for (String sql : List.of("UPDATE ledger_lines SET amount = 1 WHERE group_id = " + groupId,
                    "DELETE FROM ledger_journals WHERE group_id = " + groupId)) {
                assertThatThrownBy(() -> statement.executeUpdate(sql)).hasMessageContaining("permission denied");
                app.rollback();
                statement.execute("SELECT set_config('app.current_group_id', '" + groupId + "', true)");
            }
        }
        try (Connection owner = PostgresTestDatabase.connectAsOwner("app_it"); Statement statement = owner.createStatement()) {
            assertThatThrownBy(() -> statement.executeUpdate("UPDATE ledger_lines SET amount = 1 WHERE group_id = " + groupId))
                    .hasMessageContaining("immutable");
            assertThatThrownBy(() -> statement.executeUpdate("DELETE FROM ledger_journals WHERE group_id = " + groupId))
                    .hasMessageContaining("immutable");
            assertThatThrownBy(() -> statement.execute("TRUNCATE ledger_lines")).hasMessageContaining("immutable");
        }
    }

    @Test
    void theDatabaseRejectsAnUnbalancedJournalEvenIfTheServiceIsBypassed() throws SQLException {
        Setup setup = setup("Bypass");
        long groupId = setup.treasurer().groupId();
        try (Connection app = PostgresTestDatabase.connectAsApp("app_it"); Statement statement = app.createStatement()) {
            app.setAutoCommit(false);
            statement.execute("SELECT set_config('app.current_group_id', '" + groupId + "', true)");
            statement.execute("""
                    INSERT INTO ledger_accounts (group_id, account_type, normal_side) VALUES (%d, 'GROUP_CASH', 'DEBIT')
                    ON CONFLICT DO NOTHING""".formatted(groupId));
            statement.execute("""
                    INSERT INTO ledger_journals (group_id, journal_type, idempotency_key, request_hash, business_date, source, created_by)
                    VALUES (%d, 'ADJUSTMENT', 'sneaky', 'h', DATE '2026-03-01', 'MANUAL', %d)""".formatted(groupId, setup.treasurer().userId()));
            statement.execute("""
                    INSERT INTO ledger_lines (journal_id, group_id, account_id, direction, amount)
                    SELECT j.id, j.group_id, a.id, 'DEBIT', 500 FROM ledger_journals j, ledger_accounts a
                    WHERE j.group_id = %d AND j.idempotency_key = 'sneaky' AND a.group_id = j.group_id AND a.account_type = 'GROUP_CASH'
                    """.formatted(groupId));
            assertThatThrownBy(app::commit).hasMessageContaining("unbalanced");
        }
    }

    // --- reconciliation (spec 7.4 #5) ------------------------------------------------------------

    @Test
    void reconciliationFlagsATamperedBalance() throws SQLException {
        Setup setup = setup("Tampered");
        long member = setup.memberships().getLast();
        support.as(setup.treasurer(), () -> ledger.post(contribution("tamper", member, setup.bucketId(), 2_500)));
        long groupId = setup.treasurer().groupId();
        assertThat(reconciler.reconcile(groupId).clean()).isTrue();

        try (Connection superuser = PostgresTestDatabase.connectAsSuperuser("app_it"); Statement statement = superuser.createStatement()) {
            statement.executeUpdate("UPDATE ikimina.ledger_balances SET balance = balance + 1000000 WHERE group_id = " + groupId
                    + " AND account_id = (SELECT id FROM ikimina.ledger_accounts WHERE group_id = " + groupId
                    + " AND account_type = 'MEMBER_SAVINGS' LIMIT 1)");
        }
        ReconciliationReport.GroupResult result = reconciler.reconcile(groupId);
        assertThat(result.clean()).isFalse();
        assertThat(result.mismatchedAccountIds()).hasSize(1);
    }

    // --- reversals (Phase 2 acceptance B3) --------------------------------------------------------

    @Test
    void aReversalRestoresBalancesAndHappensOnlyOnce() throws SQLException {
        Setup setup = setup("Reversal");
        long member = setup.memberships().getLast();
        AccountRef savings = AccountRef.memberSavings(member, setup.bucketId());
        PostedJournal journal = support.as(setup.treasurer(), () -> ledger.post(contribution("to-reverse", member, setup.bucketId(), 8_000)));
        PostedJournal reversal = support.as(setup.treasurer(), () -> ledger.reverse(journal.id(), "entered by mistake"));

        assertThat(reversal.type()).isEqualTo(JournalType.REVERSAL);
        assertThat(support.as(setup.treasurer(), () -> ledger.balance(savings))).isEqualTo(Money.ZERO);
        assertThat(support.as(setup.treasurer(), () -> ledger.balance(AccountRef.of(AccountType.GROUP_CASH)))).isEqualTo(Money.ZERO);
        assertThatThrownBy(() -> support.as(setup.treasurer(), () -> ledger.reverse(journal.id(), "again")))
                .hasMessage("JOURNAL_ALREADY_REVERSED");
        assertThatThrownBy(() -> support.as(setup.treasurer(), () -> ledger.reverse(reversal.id(), "undo the undo")))
                .hasMessage("JOURNAL_NOT_REVERSIBLE");
        assertThat(reconciler.reconcile(setup.treasurer().groupId()).clean()).isTrue();
    }

    @Test
    void historyIsKeptAndPointInTimeBalancesAreRight() throws SQLException {
        Setup setup = setup("History");
        long member = setup.memberships().getLast();
        AccountRef savings = AccountRef.memberSavings(member, setup.bucketId());
        for (int day = 1; day <= 3; day++) {
            int d = day;
            support.as(setup.treasurer(), () -> ledger.post(new JournalRequest(JournalType.CONTRIBUTION, "h-" + d, "h", LocalDate.of(2026, 3, d),
                    "day " + d, JournalSource.MANUAL, null, member, List.of(
                    JournalRequest.Line.debit(AccountRef.of(AccountType.GROUP_CASH), Money.ofWholeRwf(1_000L * d)),
                    JournalRequest.Line.credit(savings, Money.ofWholeRwf(1_000L * d))))));
        }
        assertThat(support.as(setup.treasurer(), () -> ledger.balanceBefore(savings, LocalDate.of(2026, 3, 3)))).isEqualTo(Money.ofWholeRwf(3_000));
        List<StatementEntry> entries = support.as(setup.treasurer(), () -> ledger.entries(savings, LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 3)));
        assertThat(entries).extracting(StatementEntry::balanceAfter).containsExactly(Money.ofWholeRwf(3_000), Money.ofWholeRwf(6_000));
    }

    private static long journalCount(long groupId) throws SQLException {
        try (Connection owner = PostgresTestDatabase.connectAsOwner("app_it"); Statement statement = owner.createStatement();
             ResultSet rs = statement.executeQuery("SELECT count(*) FROM ledger_journals WHERE group_id = " + groupId)) {
            rs.next();
            return rs.getLong(1);
        }
    }
}
