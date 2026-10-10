package rw.ikimina.savings.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import rw.ikimina.groups.GroupRole;
import rw.ikimina.ledger.internal.LedgerReconciler;
import rw.ikimina.shared.time.BusinessTime;
import rw.ikimina.support.Api.Response;
import rw.ikimina.support.Api.User;
import rw.ikimina.support.GroupFixture;
import rw.ikimina.support.GroupFixture.TestGroup;
import rw.ikimina.support.GroupRoutes;
import rw.ikimina.support.IntegrationTest;
import rw.ikimina.support.LedgerTestSupport;
import tools.jackson.databind.JsonNode;

/** Phase 3c: savings withdrawals end to end through the API (spec 8.3; owner decisions, Phase 3c). */
class WithdrawalFlowIT extends IntegrationTest {

    @Autowired
    private LedgerReconciler reconciler;

    private TestGroup group;
    private String fund;
    private final Map<GroupRole, User> fresh = new EnumMap<>(GroupRole.class);

    @BeforeEach
    void group() {
        group = new GroupFixture(api(), sms).createWithEveryRole("Withdrawals " + UUID.randomUUID().toString().substring(0, 8));
        fund = bucket("Ubwizigame", "SAVINGS", true);
    }

    // --- helpers ------------------------------------------------------------------------------

    private User as(GroupRole role) {
        return fresh.getOrDefault(role, group.as(role));
    }

    private void everyoneSignsInAgain() {
        for (GroupRole role : GroupRole.values()) {
            User person = group.as(role);
            fresh.put(role, person.withAccessToken(api().login(person.phone(), person.password(), person.ip()).expect(200)
                    .text("accessToken")));
        }
    }

    private String bucket(String name, String type, boolean withdrawable) {
        return api().post(group.path("/buckets"), group.president(), Map.of("name", name, "type", type, "cycleType", "ROLLING",
                        "startDate", "2026-01-01", "terms", Map.of("mandatory", false, "minimumContribution", "0",
                                "contributionFrequency", "ADHOC", "withdrawable", withdrawable)))
                .expect(201).text("bucketId");
    }

    /** The bylaws: withdrawals allowed with this many days' notice (a financial change: proposed, then confirmed). */
    private void allowWithdrawals(int noticeDays) {
        JsonNode settings = api().get(group.path("/settings"), group.president()).body();
        Map<String, Object> changed = new LinkedHashMap<>(json.convertValue(settings.get("settings"), Map.class));
        changed.put("withdrawalsAllowed", true);
        changed.put("withdrawalNoticeDays", noticeDays);
        String changeId = api().put(group.path("/settings"), as(GroupRole.TREASURER),
                        Map.of("version", settings.get("version").asLong(), "settings", changed))
                .expect(202).body().get("pendingChange").get("changeId").asString();
        api().post(group.path("/settings/changes/" + changeId + "/confirm"), group.president(), null).expect(200);
    }

    private void save(GroupRole who, String bucket, String amount) {
        api().contribute(group.groupId(), as(GroupRole.TREASURER), Map.of("memberId", group.memberId(who), "bucketId", bucket,
                "amount", amount, "method", "CASH"), "save-" + UUID.randomUUID()).expect(201);
    }

    private Response ask(GroupRole who, String bucket, String amount) {
        return api().post(group.path("/withdrawals"), as(who), Map.of("bucketId", bucket, "amount", amount));
    }

    private Response approve(GroupRole who, String id) {
        return api().post(group.path("/withdrawals/" + id + "/approve"), as(who), null);
    }

    private Response pay(String id, String key) {
        return api().call(HttpMethod.POST, group.path("/withdrawals/" + id + "/pay"), as(GroupRole.TREASURER).accessToken(),
                Map.of("method", "CASH"), as(GroupRole.TREASURER).ip(), key == null ? Map.of() : Map.of("Idempotency-Key", key));
    }

    private String balance(GroupRole who) {
        return api().get(group.path("/members/" + group.memberId(who) + "/balances"), as(GroupRole.TREASURER)).expect(200).text("total");
    }

    private List<String> reasons(Response response) {
        List<String> codes = new ArrayList<>();
        response.body().get("reasons").forEach(r -> codes.add(r.get("code").asString()));
        return codes;
    }

    private long groupId() throws Exception {
        return LedgerTestSupport.actor(group.groupId(), group.memberId(GroupRole.MEMBER)).groupId();
    }

    // --- tests --------------------------------------------------------------------------------

    @Nested
    class Flow {

        @Test
        void askApprovePayAndTheMoneyLeavesTheMembersSavingsOnce() throws Exception {
            allowWithdrawals(0);
            save(GroupRole.MEMBER, fund, "50000");

            JsonNode asked = ask(GroupRole.MEMBER, fund, "20000").expect(201).body();
            String id = asked.get("withdrawalId").asString();
            assertThat(asked.get("status").asString()).isEqualTo("REQUESTED");
            assertThat(asked.get("earliestPayoutOn").asString()).isEqualTo(BusinessTime.today(clock).toString());

            approve(GroupRole.MEMBER, id).expect(403, "FORBIDDEN");
            approve(GroupRole.SECRETARY, id).expect(403, "FORBIDDEN");
            pay(id, "pay-" + UUID.randomUUID()).expect(409, "WITHDRAWAL_INVALID_STATE");   // not approved yet
            assertThat(approve(GroupRole.PRESIDENT, id).expect(200).text("status")).isEqualTo("APPROVED");

            String key = "pay-" + UUID.randomUUID();
            JsonNode paid = pay(id, key).expect(200).body();
            assertThat(paid.get("status").asString()).isEqualTo("PAID");
            assertThat(paid.get("journalId").isNull()).isFalse();
            assertThat(balance(GroupRole.MEMBER)).isEqualTo("30000.00");

            assertThat(pay(id, key).expect(200).text("status")).as("a retry returns the original").isEqualTo("PAID");
            pay(id, "pay-" + UUID.randomUUID()).expect(409, "WITHDRAWAL_INVALID_STATE");
            assertThat(balance(GroupRole.MEMBER)).isEqualTo("30000.00");
            assertThat(reconciler.reconcile(groupId()).clean()).isTrue();

            // The payout shows on the member's own record as a withdrawal.
            JsonNode txns = api().get(group.path("/members/" + group.memberId(GroupRole.MEMBER) + "/transactions"), as(GroupRole.MEMBER))
                    .expect(200).body().get("items");
            assertThat(txns.get(0).get("type").asString()).isEqualTo("WITHDRAWAL");
        }

        @Test
        void everyRuleTheRequestBreaksIsGiven() {
            save(GroupRole.MEMBER, fund, "10000");
            // Withdrawals are off by default.
            assertThat(reasons(ask(GroupRole.MEMBER, fund, "5000").expect(422, "WITHDRAWAL_NOT_ALLOWED"))).containsExactly("WITHDRAWALS_OFF");

            allowWithdrawals(0);
            String socialFund = bucket("Ingoboka", "SOCIAL_FUND", false);
            save(GroupRole.MEMBER, socialFund, "10000");
            assertThat(reasons(ask(GroupRole.MEMBER, socialFund, "1000").expect(422, "WITHDRAWAL_NOT_ALLOWED")))
                    .containsExactly("FUND_NOT_WITHDRAWABLE");
            assertThat(reasons(ask(GroupRole.MEMBER, fund, "10001").expect(422, "WITHDRAWAL_NOT_ALLOWED")))
                    .containsExactly("ABOVE_BALANCE");

            // A pending request holds its part of the balance.
            ask(GroupRole.MEMBER, fund, "8000").expect(201);
            Response tooMuch = ask(GroupRole.MEMBER, fund, "3000").expect(422, "WITHDRAWAL_NOT_ALLOWED");
            assertThat(tooMuch.body().get("reasons").get(0).get("message").asString()).contains("2000.00");
        }

        @Test
        void notWhileTheMemberHasAnUnfinishedLoan() {
            allowWithdrawals(0);
            save(GroupRole.MEMBER, fund, "50000");
            String productId = api().post(group.path("/loan-products"), group.president(),
                    Map.of("name", "Standard", "terms", GroupRoutes.LOAN_TERMS)).expect(201).text("productId");
            String loanId = api().post(group.path("/loans"), as(GroupRole.MEMBER), Map.of("productId", productId, "amount", "10000",
                    "termMonths", 3)).expect(201).text("loanId");

            assertThat(reasons(ask(GroupRole.MEMBER, fund, "1000").expect(422, "WITHDRAWAL_NOT_ALLOWED")))
                    .containsExactly("OPEN_LOAN_WITHDRAWAL");
            api().post(group.path("/loans/" + loanId + "/cancel"), as(GroupRole.MEMBER), null).expect(200);
            ask(GroupRole.MEMBER, fund, "1000").expect(201);
        }

        @Test
        void thePayoutWaitsForTheNoticePeriod() {
            allowWithdrawals(3);
            save(GroupRole.MEMBER, fund, "50000");
            String id = ask(GroupRole.MEMBER, fund, "5000").expect(201).text("withdrawalId");
            approve(GroupRole.PRESIDENT, id).expect(200);

            Response early = pay(id, "pay-" + UUID.randomUUID()).expect(409, "WITHDRAWAL_NOT_YET_DUE");
            assertThat(early.body().get("detail").asString()).contains(BusinessTime.today(clock).plusDays(3).toString());

            clock.advance(Duration.ofDays(3));
            everyoneSignsInAgain();
            pay(id, "pay-" + UUID.randomUUID()).expect(200);
        }

        @Test
        void cashPromisedToApprovedLoansIsNotPaidOutAgain() {
            allowWithdrawals(0);
            save(GroupRole.MEMBER, fund, "50000");   // the group holds 50,000
            String productId = api().post(group.path("/loan-products"), group.president(),
                    Map.of("name", "Standard", "terms", GroupRoutes.LOAN_TERMS)).expect(201).text("productId");
            String loanId = api().post(group.path("/loans"), as(GroupRole.SECRETARY), Map.of("productId", productId, "amount", "40000",
                    "termMonths", 3)).expect(201).text("loanId");
            api().post(group.path("/loans/" + loanId + "/approve"), group.president(), Map.of()).expect(200);
            api().post(group.path("/loans/" + loanId + "/approve"), as(GroupRole.TREASURER), Map.of()).expect(200);

            String id = ask(GroupRole.MEMBER, fund, "20000").expect(201).text("withdrawalId");
            approve(GroupRole.PRESIDENT, id).expect(200);
            pay(id, "pay-" + UUID.randomUUID()).expect(422, "INSUFFICIENT_GROUP_FUNDS");
        }

        @Test
        void theTreasurersOwnWithdrawalIsApprovedByThePresidentAndFlagged() {
            allowWithdrawals(0);
            save(GroupRole.TREASURER, fund, "20000");
            String id = ask(GroupRole.TREASURER, fund, "5000").expect(201).text("withdrawalId");
            approve(GroupRole.TREASURER, id).expect(403, "SELF_APPROVAL_FORBIDDEN");
            approve(GroupRole.PRESIDENT, id).expect(200);
            assertThat(pay(id, "pay-" + UUID.randomUUID()).expect(200).body().get("recordedByMember").asBoolean()).isTrue();
        }

        @Test
        void aPaidWithdrawalCanBeReversedTwoStepAndTheMoneyIsBack() throws Exception {
            allowWithdrawals(0);
            save(GroupRole.MEMBER, fund, "50000");
            String id = ask(GroupRole.MEMBER, fund, "20000").expect(201).text("withdrawalId");
            approve(GroupRole.PRESIDENT, id).expect(200);
            String journal = pay(id, "pay-" + UUID.randomUUID()).expect(200).text("journalId");

            String requestId = api().post(group.path("/journals/" + journal + "/reverse"), as(GroupRole.TREASURER),
                    Map.of("reason", "paid to the wrong person")).expect(202).text("requestId");
            api().post(group.path("/reversals/" + requestId + "/approve"), group.president(), null).expect(200);

            assertThat(balance(GroupRole.MEMBER)).isEqualTo("50000.00");
            assertThat(api().get(group.path("/withdrawals/" + id), as(GroupRole.MEMBER)).expect(200).body().get("reversed").asBoolean())
                    .isTrue();
            assertThat(reconciler.reconcile(groupId()).clean()).isTrue();
        }

        @Test
        void membersSeeAndCancelOnlyTheirOwn() {
            allowWithdrawals(0);
            save(GroupRole.MEMBER, fund, "10000");
            save(GroupRole.PRESIDENT, fund, "10000");
            String mine = ask(GroupRole.MEMBER, fund, "1000").expect(201).text("withdrawalId");
            String presidents = ask(GroupRole.PRESIDENT, fund, "1000").expect(201).text("withdrawalId");

            JsonNode list = api().get(group.path("/withdrawals"), as(GroupRole.MEMBER)).expect(200).body().get("items");
            assertThat(list).extracting(w -> w.get("withdrawalId").asString()).containsExactly(mine);
            api().get(group.path("/withdrawals/" + presidents), as(GroupRole.MEMBER)).expect(404, "NOT_FOUND");
            api().post(group.path("/withdrawals/" + presidents + "/cancel"), as(GroupRole.MEMBER), null).expect(404, "NOT_FOUND");
            api().post(group.path("/withdrawals/" + mine + "/cancel"), as(GroupRole.TREASURER), null).expect(403, "FORBIDDEN");
            assertThat(api().post(group.path("/withdrawals/" + mine + "/cancel"), as(GroupRole.MEMBER), null).expect(200).text("status"))
                    .isEqualTo("CANCELLED");
        }

        @Test
        void twentySimultaneousPayoutsPayOnce() throws Exception {
            allowWithdrawals(0);
            save(GroupRole.MEMBER, fund, "50000");
            String id = ask(GroupRole.MEMBER, fund, "20000").expect(201).text("withdrawalId");
            approve(GroupRole.PRESIDENT, id).expect(200);

            List<Response> responses = runTogether(20, i -> pay(id, "pay-" + UUID.randomUUID()));

            assertThat(responses.stream().filter(r -> r.status() == 200)).hasSize(1);
            assertThat(responses.stream().filter(r -> r.status() != 200))
                    .allSatisfy(r -> assertThat(r.code()).as(String.valueOf(r.body())).isEqualTo("WITHDRAWAL_INVALID_STATE"));
            assertThat(balance(GroupRole.MEMBER)).isEqualTo("30000.00");
            assertThat(reconciler.reconcile(groupId()).clean()).isTrue();
        }
    }

    private interface Call {
        Response run(int index);
    }

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
