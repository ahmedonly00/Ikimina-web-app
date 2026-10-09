package rw.ikimina.savings;

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
import rw.ikimina.groups.GroupRole;
import rw.ikimina.ledger.internal.LedgerReconciler;
import rw.ikimina.support.Api.Response;
import rw.ikimina.support.Api.User;
import rw.ikimina.support.GroupFixture;
import rw.ikimina.support.GroupFixture.TestGroup;
import rw.ikimina.support.IntegrationTest;
import rw.ikimina.support.LedgerTestSupport;
import rw.ikimina.support.PostgresTestDatabase;

/**
 * Phase 2 acceptance B2 (spec 7.4 #2): 50 contributions to one member and bucket, sent at the
 * same moment through the real API, give exactly the expected balance - no lost update, no
 * deadlock - and concurrent retries of one request record it once.
 */
class ConcurrentContributionsIT extends IntegrationTest {

    private static final int PARALLEL = 50;

    @Autowired
    private LedgerReconciler reconciler;

    private record Fixture(TestGroup group, User treasurer, String memberId, String bucketId) {
    }

    private Fixture fixture(String name) {
        TestGroup group = new GroupFixture(api(), sms).createWithEveryRole(name);
        String bucketId = api().post(group.path("/buckets"), group.president(), Map.of("name", "Ubwizigame", "type", "SAVINGS",
                        "cycleType", "ROLLING", "startDate", "2026-01-01", "terms", Map.of("mandatory", false,
                                "minimumContribution", "0", "contributionFrequency", "ADHOC", "withdrawable", false)))
                .expect(201).text("bucketId");
        return new Fixture(group, group.as(GroupRole.TREASURER), group.memberId(GroupRole.MEMBER), bucketId);
    }

    private Map<String, Object> body(Fixture f) {
        return Map.of("memberId", f.memberId(), "bucketId", f.bucketId(), "amount", "1000", "method", "CASH");
    }

    @Test
    void fiftyParallelContributionsGiveTheExactBalance() throws Exception {
        Fixture f = fixture("Fifty At Once");
        List<Response> responses = runTogether(PARALLEL, i -> api().contribute(f.group().groupId(), f.treasurer(), body(f), "par-" + UUID.randomUUID()));

        assertThat(responses).allSatisfy(r -> assertThat(r.status()).as(String.valueOf(r.body())).isEqualTo(201));
        Response balances = api().get(f.group().path("/members/" + f.memberId() + "/balances"), f.treasurer()).expect(200);
        assertThat(balances.text("total")).isEqualTo("50000.00");
        assertThat(transactionCount(f)).isEqualTo(PARALLEL);
        long groupId = LedgerTestSupport.actor(f.group().groupId(), f.memberId()).groupId();
        assertThat(reconciler.reconcile(groupId).clean()).isTrue();
    }

    @Test
    void concurrentRetriesOfOneRequestRecordItOnce() throws Exception {
        Fixture f = fixture("Ten Retries");
        String key = "retry-" + UUID.randomUUID();
        List<Response> responses = runTogether(10, i -> api().contribute(f.group().groupId(), f.treasurer(), body(f), key));

        assertThat(responses).allSatisfy(r -> assertThat(r.status()).isEqualTo(201));
        assertThat(responses.stream().map(r -> r.text("transactionId")).distinct()).hasSize(1);
        assertThat(api().get(f.group().path("/members/" + f.memberId() + "/balances"), f.treasurer()).text("total"))
                .isEqualTo("1000.00");
        assertThat(transactionCount(f)).isEqualTo(1);
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
            pool.shutdown();
        }
    }

    private static long transactionCount(Fixture f) throws SQLException {
        try (Connection owner = PostgresTestDatabase.connectAsOwner("app_it");
             PreparedStatement statement = owner.prepareStatement("""
                     SELECT count(*) FROM savings_transactions t JOIN group_memberships m ON m.id = t.membership_id
                     WHERE m.public_id = ?""")) {
            statement.setObject(1, UUID.fromString(f.memberId()));
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }
}
