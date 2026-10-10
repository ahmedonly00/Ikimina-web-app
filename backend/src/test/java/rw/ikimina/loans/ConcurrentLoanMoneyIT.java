package rw.ikimina.loans;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import rw.ikimina.groups.GroupRole;
import rw.ikimina.ledger.internal.LedgerReconciler;
import rw.ikimina.support.Api.Response;
import rw.ikimina.support.Api.User;
import rw.ikimina.support.GroupFixture;
import rw.ikimina.support.GroupFixture.TestGroup;
import rw.ikimina.support.GroupRoutes;
import rw.ikimina.support.IntegrationTest;
import rw.ikimina.support.LedgerTestSupport;
import rw.ikimina.support.PostgresTestDatabase;

/**
 * Phase 3 acceptance: a loan cannot be disbursed twice, even by requests sent at the same moment
 * (spec 9.4), and simultaneous repayments of one loan are each counted exactly once.
 */
class ConcurrentLoanMoneyIT extends IntegrationTest {

    private static final int PARALLEL = 20;

    @Autowired
    private LedgerReconciler reconciler;

    private record Fixture(TestGroup group, User treasurer, String loanId) {
    }

    /** A member's 30,000 loan over 3 months, approved by President and Treasurer, in a group holding 100,000. */
    private Fixture approvedLoan(String name) {
        TestGroup group = new GroupFixture(api(), sms).createWithEveryRole(name);
        User treasurer = group.as(GroupRole.TREASURER);
        String bucketId = api().post(group.path("/buckets"), group.president(), Map.of("name", "Ubwizigame", "type", "SAVINGS",
                        "cycleType", "ROLLING", "startDate", "2026-01-01", "terms", Map.of("mandatory", false,
                                "minimumContribution", "0", "contributionFrequency", "ADHOC", "withdrawable", false)))
                .expect(201).text("bucketId");
        api().contribute(group.groupId(), treasurer, Map.of("memberId", group.memberId(GroupRole.MEMBER), "bucketId", bucketId,
                "amount", "100000", "method", "CASH"), "save-" + UUID.randomUUID()).expect(201);
        String productId = api().post(group.path("/loan-products"), group.president(),
                Map.of("name", "Standard", "terms", GroupRoutes.LOAN_TERMS)).expect(201).text("productId");
        String loanId = api().post(group.path("/loans"), group.as(GroupRole.MEMBER),
                Map.of("productId", productId, "amount", "30000", "termMonths", 3)).expect(201).text("loanId");
        api().post(group.path("/loans/" + loanId + "/approve"), group.president(), Map.of()).expect(200);
        api().post(group.path("/loans/" + loanId + "/approve"), treasurer, Map.of()).expect(200);
        return new Fixture(group, treasurer, loanId);
    }

    private Response disburse(Fixture f, String key) {
        return api().call(HttpMethod.POST, f.group().path("/loans/" + f.loanId() + "/disburse"), f.treasurer().accessToken(),
                Map.of("method", "CASH"), f.treasurer().ip(), Map.of("Idempotency-Key", key));
    }

    @Test
    void twentySimultaneousPayoutsOfOneLoanPayItOnce() throws Exception {
        Fixture f = approvedLoan("Payout Race");

        List<Response> responses = runTogether(PARALLEL, i -> disburse(f, "payout-" + UUID.randomUUID()));

        assertThat(responses.stream().filter(r -> r.status() == 200)).hasSize(1);
        assertThat(responses.stream().filter(r -> r.status() != 200))
                .allSatisfy(r -> assertThat(r.code()).as(String.valueOf(r.body())).isEqualTo("LOAN_INVALID_TRANSITION"));
        assertThat(count(f, "loan_disbursements")).isEqualTo(1);
        assertThat(count(f, "loan_installments")).isEqualTo(3);
        assertThat(journals(f, "LOAN_DISBURSEMENT")).isEqualTo(1);
        assertThat(reconciler.reconcile(groupId(f)).clean()).isTrue();
    }

    @Test
    void simultaneousRetriesOfOnePayoutReturnTheOriginal() throws Exception {
        Fixture f = approvedLoan("Payout Retries");
        String key = "payout-" + UUID.randomUUID();

        List<Response> responses = runTogether(10, i -> disburse(f, key));

        assertThat(responses).allSatisfy(r -> assertThat(r.status()).as(String.valueOf(r.body())).isEqualTo(200));
        assertThat(count(f, "loan_disbursements")).isEqualTo(1);
        assertThat(journals(f, "LOAN_DISBURSEMENT")).isEqualTo(1);
    }

    @Test
    void simultaneousRepaymentsAreEachCountedOnce() throws Exception {
        Fixture f = approvedLoan("Repayment Race");
        disburse(f, "payout-" + UUID.randomUUID()).expect(200);

        List<Response> responses = runTogether(PARALLEL, i -> api().call(HttpMethod.POST,
                f.group().path("/loans/" + f.loanId() + "/repayments"), f.treasurer().accessToken(),
                Map.of("amount", "1000", "method", "CASH"), f.treasurer().ip(), Map.of("Idempotency-Key", "repay-" + UUID.randomUUID())));

        assertThat(responses).allSatisfy(r -> assertThat(r.status()).as(String.valueOf(r.body())).isEqualTo(201));
        String owed = api().get(f.group().path("/loans/" + f.loanId()), f.group().president()).expect(200).body()
                .get("outstanding").get("total").asString();
        assertThat(owed).isEqualTo("14500.00");   // 34,500 owed less 20 x 1,000
        assertThat(journals(f, "LOAN_REPAYMENT")).isEqualTo(PARALLEL);
        assertThat(reconciler.reconcile(groupId(f)).clean()).isTrue();
    }

    /**
     * Review finding: approving a repayment's reversal and recording a new repayment of the same loan
     * at the same moment used to take their locks in opposite orders and could deadlock. Each round
     * races the two; both must succeed, and what is owed must come out exact.
     */
    @Test
    void reversingARepaymentWhileAnotherIsRecordedNeverDeadlocks() throws Exception {
        Fixture f = approvedLoan("Reverse Race");
        disburse(f, "payout-" + UUID.randomUUID()).expect(200);
        User president = f.group().president();
        int rounds = 8;
        for (int round = 0; round < rounds; round++) {
            String journal = repay(f, "1000").expect(201).body().get("repayments").get(round * 2).get("journalId").asString();
            String requestId = api().post(f.group().path("/journals/" + journal + "/reverse"), f.treasurer(),
                    Map.of("reason", "race round " + round)).expect(202).text("requestId");

            List<Response> both = runTogether(2, i -> i == 0
                    ? api().post(f.group().path("/reversals/" + requestId + "/approve"), president, null)
                    : repay(f, "1000"));

            assertThat(both.get(0).status()).as("reversal: %s", both.get(0).body()).isEqualTo(200);
            assertThat(both.get(1).status()).as("repayment: %s", both.get(1).body()).isEqualTo(201);
        }
        String owed = api().get(f.group().path("/loans/" + f.loanId()), president).expect(200).body()
                .get("outstanding").get("total").asString();
        assertThat(owed).isEqualTo("26500.00");   // 34,500 less one standing 1,000 repayment per round
        assertThat(reconciler.reconcile(groupId(f)).clean()).isTrue();
    }

    private Response repay(Fixture f, String amount) {
        return api().call(HttpMethod.POST, f.group().path("/loans/" + f.loanId() + "/repayments"), f.treasurer().accessToken(),
                Map.of("amount", amount, "method", "CASH"), f.treasurer().ip(), Map.of("Idempotency-Key", "repay-" + UUID.randomUUID()));
    }

    private long groupId(Fixture f) throws SQLException {
        return LedgerTestSupport.actor(f.group().groupId(), f.group().memberId(GroupRole.MEMBER)).groupId();
    }

    private long count(Fixture f, String table) throws SQLException {
        return query(f, "SELECT count(*) FROM " + table + " t JOIN loans l ON l.id = t.loan_id WHERE l.public_id = ?", f.loanId());
    }

    private long journals(Fixture f, String type) throws SQLException {
        return query(f, "SELECT count(*) FROM ledger_journals j JOIN groups g ON g.id = j.group_id WHERE g.public_id = ?::uuid"
                + " AND j.journal_type = '" + type + "'", f.group().groupId());
    }

    private static long query(Fixture f, String sql, String id) throws SQLException {
        try (Connection owner = PostgresTestDatabase.connectAsOwner("app_it");
             PreparedStatement statement = owner.prepareStatement(sql)) {
            statement.setObject(1, sql.contains("?::uuid") ? id : UUID.fromString(id));
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private interface Call {
        Response run(int index);
    }

    /** Starts every call at the same instant, to give races their best chance. */
    private static List<Response> runTogether(int count, Call call) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(count);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Callable<Response>> tasks = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                int index = i;
                tasks.add(() -> {
                    start.await();
                    return call.run(index);
                });
            }
            List<Future<Response>> futures = new ArrayList<>();
            tasks.forEach(task -> futures.add(pool.submit(task)));
            start.countDown();
            List<Response> responses = new ArrayList<>();
            for (Future<Response> future : futures) {
                responses.add(future.get());
            }
            return responses;
        } finally {
            pool.shutdownNow();
        }
    }
}
