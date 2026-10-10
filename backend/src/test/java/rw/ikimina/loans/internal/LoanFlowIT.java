package rw.ikimina.loans.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import rw.ikimina.groups.GroupRole;
import rw.ikimina.ledger.internal.LedgerReconciler;
import rw.ikimina.shared.tenancy.TenantContext;
import rw.ikimina.shared.time.BusinessTime;
import rw.ikimina.support.Api.Response;
import rw.ikimina.support.Api.User;
import rw.ikimina.support.GroupFixture;
import rw.ikimina.support.GroupFixture.TestGroup;
import rw.ikimina.support.GroupRoutes;
import rw.ikimina.support.IntegrationTest;
import rw.ikimina.support.LedgerTestSupport;
import rw.ikimina.support.PostgresTestDatabase;
import tools.jackson.databind.JsonNode;

/** Phase 3: loans end to end through the API (spec 9, 17.4). */
class LoanFlowIT extends IntegrationTest {

    @Autowired
    private OverdueJob overdueJob;

    @Autowired
    private LedgerReconciler reconciler;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TestGroup group;
    private final Map<GroupRole, User> fresh = new EnumMap<>(GroupRole.class);
    private String bucketId;

    @BeforeEach
    void group() {
        group = new GroupFixture(api(), sms).createWithEveryRole("Loans " + UUID.randomUUID().toString().substring(0, 8));
        bucketId = api().post(group.path("/buckets"), group.president(), Map.of("name", "Ubwizigame", "type", "SAVINGS",
                        "cycleType", "ROLLING", "startDate", "2026-01-01", "terms", Map.of("mandatory", false,
                                "minimumContribution", "0", "contributionFrequency", "ADHOC", "withdrawable", false)))
                .expect(201).text("bucketId");
    }

    // --- helpers ------------------------------------------------------------------------------

    /** Who acts in the role: after a long clock jump, tokens are renewed by signing in again. */
    private User as(GroupRole role) {
        return fresh.getOrDefault(role, group.as(role));
    }

    /** Access tokens last 15 minutes; after moving the clock by days, everyone signs in again. */
    private void everyoneSignsInAgain() {
        for (GroupRole role : GroupRole.values()) {
            User person = group.as(role);
            fresh.put(role, person.withAccessToken(api().login(person.phone(), person.password(), person.ip()).expect(200)
                    .text("accessToken")));
        }
    }

    private void save(GroupRole who, String amount) {
        save(who, bucketId, amount);
    }

    private void save(GroupRole who, String bucket, String amount) {
        api().contribute(group.groupId(), as(GroupRole.TREASURER), Map.of("memberId", group.memberId(who), "bucketId", bucket,
                "amount", amount, "method", "CASH"), "save-" + UUID.randomUUID()).expect(201);
    }

    private Map<String, Object> terms(Map<String, Object> overrides) {
        Map<String, Object> terms = new LinkedHashMap<>(GroupRoutes.LOAN_TERMS);
        terms.putAll(overrides);
        return terms;
    }

    private String product(Map<String, Object> overrides) {
        return api().post(group.path("/loan-products"), group.president(),
                Map.of("name", "Product " + UUID.randomUUID().toString().substring(0, 6), "terms", terms(overrides)))
                .expect(201).text("productId");
    }

    private Response request(GroupRole borrower, String productId, String amount, int months) {
        return api().post(group.path("/loans"), as(borrower), Map.of("productId", productId, "amount", amount, "termMonths", months));
    }

    private Response approve(GroupRole who, String loanId) {
        return api().post(group.path("/loans/" + loanId + "/approve"), as(who), Map.of("comment", "agreed"));
    }

    private Response disburse(GroupRole who, String loanId, String key) {
        return api().call(HttpMethod.POST, group.path("/loans/" + loanId + "/disburse"), as(who).accessToken(),
                Map.of("method", "CASH"), as(who).ip(), Map.of("Idempotency-Key", key));
    }

    private Response repay(String loanId, String amount, String key) {
        return api().call(HttpMethod.POST, group.path("/loans/" + loanId + "/repayments"), as(GroupRole.TREASURER).accessToken(),
                Map.of("amount", amount, "method", "CASH"), as(GroupRole.TREASURER).ip(), key == null ? Map.of() : Map.of("Idempotency-Key", key));
    }

    private JsonNode loan(String loanId) {
        return api().get(group.path("/loans/" + loanId), as(GroupRole.PRESIDENT)).expect(200).body();
    }

    private JsonNode schedule(String loanId) {
        return api().get(group.path("/loans/" + loanId + "/schedule"), as(GroupRole.PRESIDENT)).expect(200).body();
    }

    /** A member's loan approved by President and Treasurer (no threshold: two approvals) and handed over. */
    private String disbursedLoan(String productId, String amount, int months) {
        String loanId = request(GroupRole.MEMBER, productId, amount, months).expect(201).text("loanId");
        approve(GroupRole.PRESIDENT, loanId).expect(200);
        approve(GroupRole.TREASURER, loanId).expect(200);
        disburse(GroupRole.TREASURER, loanId, "pay-" + UUID.randomUUID()).expect(200);
        return loanId;
    }

    private List<String> reasons(Response response) {
        List<String> codes = new ArrayList<>();
        response.body().get("reasons").forEach(r -> codes.add(r.get("code").asString()));
        return codes;
    }

    private LocalDate today() {
        return BusinessTime.today(clock);
    }

    private String journalOfDisbursement(String loanId) throws SQLException {
        try (Connection owner = PostgresTestDatabase.connectAsOwner("app_it");
             PreparedStatement query = owner.prepareStatement("""
                     SELECT j.public_id FROM loan_disbursements d JOIN loans l ON l.id = d.loan_id
                     JOIN ledger_journals j ON j.id = d.journal_id WHERE l.public_id = ?""")) {
            query.setObject(1, UUID.fromString(loanId));
            try (ResultSet rs = query.executeQuery()) {
                assertThat(rs.next()).isTrue();
                return rs.getString(1);
            }
        }
    }

    // --- products ----------------------------------------------------------------------------

    @Nested
    class Products {

        @Test
        void changingMoneyTermsNeedsASecondOfficer() {
            String productId = product(Map.of());
            JsonNode created = api().get(group.path("/loan-products/" + productId), as(GroupRole.MEMBER)).expect(200).body();
            assertThat(created.get("terms").get("interestRatePercent").asString()).as("rates travel as strings").isEqualTo("5.0000");

            Response proposed = api().patch(group.path("/loan-products/" + productId), as(GroupRole.TREASURER),
                    Map.of("version", created.get("version").asLong(), "terms", terms(Map.of("interestRatePercent", "4.5")))).expect(202);
            String changeId = proposed.body().get("pendingChange").get("changeId").asString();
            assertThat(proposed.body().get("terms").get("interestRatePercent").asString()).as("unchanged until confirmed")
                    .isEqualTo("5.0000");

            api().post(group.path("/loan-products/" + productId + "/changes/" + changeId + "/confirm"), as(GroupRole.TREASURER), null)
                    .expect(403, "SELF_APPROVAL_FORBIDDEN");
            JsonNode confirmed = api().post(group.path("/loan-products/" + productId + "/changes/" + changeId + "/confirm"),
                    group.president(), null).expect(200).body();
            assertThat(confirmed.get("terms").get("interestRatePercent").isString()).isTrue();
            assertThat(new BigDecimal(confirmed.get("terms").get("interestRatePercent").asString())).isEqualByComparingTo("4.5");
            assertThat(confirmed.get("pendingChange").isNull()).isTrue();
        }

        @Test
        void resubmittingTheSameTermsChangesNothing() {
            String productId = product(Map.of());
            long version = api().get(group.path("/loan-products/" + productId), group.president()).body().get("version").asLong();
            api().patch(group.path("/loan-products/" + productId), group.president(),
                    Map.of("version", version, "terms", terms(Map.of()))).expect(200);
        }

        @Test
        void termsWithoutADefinedScheduleAreRefused() {
            api().post(group.path("/loan-products"), group.president(), Map.of("name", "Weekly",
                    "terms", terms(Map.of("repaymentFrequency", "WEEKLY")))).expect(400, "LOAN_TERMS_UNSUPPORTED");
            api().post(group.path("/loan-products"), group.president(), Map.of("name", "Reducing term",
                    "terms", terms(Map.of("interestMethod", "REDUCING_BALANCE", "interestPeriod", "LOAN_TERM"))))
                    .expect(400, "LOAN_TERMS_UNSUPPORTED");
            api().post(group.path("/loan-products"), group.president(), Map.of("name", "Backwards",
                    "terms", terms(Map.of("minTermMonths", 6, "maxTermMonths", 3)))).expect(400, "VALIDATION_FAILED");
        }
    }

    // --- requesting ----------------------------------------------------------------------------

    @Nested
    class Requesting {

        @Test
        void anIneligibleRequestListsEveryReason() {
            save(GroupRole.MEMBER, "5000");
            String productId = product(Map.of("minAmount", "10000", "maxAmount", "50000", "maxMultipleOfSavings", "2",
                    "minTermMonths", 2, "maxTermMonths", 6));

            Response refused = request(GroupRole.MEMBER, productId, "60000", 12).expect(422, "LOAN_NOT_ELIGIBLE");

            assertThat(reasons(refused)).containsExactlyInAnyOrder("ABOVE_MAXIMUM_AMOUNT", "ABOVE_SAVINGS_LIMIT", "TERM_TOO_LONG",
                    "INSUFFICIENT_GROUP_FUNDS");
            JsonNode limit = refused.body().get("reasons").get(1);
            assertThat(limit.get("message").asString()).contains("10000.00");
        }

        @Test
        void theSocialFundDoesNotCountTowardsTheSavingsLimit() {
            String socialFund = api().post(group.path("/buckets"), group.president(), Map.of("name", "Ingoboka", "type", "SOCIAL_FUND",
                            "cycleType", "ROLLING", "startDate", "2026-01-01", "terms", Map.of("mandatory", false,
                                    "minimumContribution", "0", "contributionFrequency", "ADHOC", "withdrawable", false)))
                    .expect(201).text("bucketId");
            save(GroupRole.MEMBER, "10000");
            save(GroupRole.MEMBER, socialFund, "50000");
            String productId = product(Map.of("maxMultipleOfSavings", "1"));

            Response refused = request(GroupRole.MEMBER, productId, "20000", 3).expect(422, "LOAN_NOT_ELIGIBLE");
            assertThat(reasons(refused)).containsExactly("ABOVE_SAVINGS_LIMIT");
            request(GroupRole.MEMBER, productId, "10000", 3).expect(201);
        }

        @Test
        void oneUnfinishedLoanAtATimeUnlessTheProductAllowsMore() {
            save(GroupRole.MEMBER, "100000");
            String strict = product(Map.of());
            request(GroupRole.MEMBER, strict, "10000", 3).expect(201);
            assertThat(reasons(request(GroupRole.MEMBER, strict, "10000", 3).expect(422, "LOAN_NOT_ELIGIBLE")))
                    .containsExactly("OPEN_LOAN_EXISTS");
            String relaxed = product(Map.of("allowConcurrentLoans", true));
            request(GroupRole.MEMBER, relaxed, "10000", 3).expect(201);
        }

        @Test
        void approvedLoansReserveTheGroupsCash() {
            save(GroupRole.MEMBER, "10000");
            String productId = product(Map.of("allowConcurrentLoans", true));
            String first = request(GroupRole.MEMBER, productId, "8000", 3).expect(201).text("loanId");
            approve(GroupRole.PRESIDENT, first).expect(200);
            approve(GroupRole.TREASURER, first).expect(200);

            Response refused = request(GroupRole.SECRETARY, productId, "5000", 3).expect(422, "LOAN_NOT_ELIGIBLE");
            assertThat(reasons(refused)).containsExactly("INSUFFICIENT_GROUP_FUNDS");
            assertThat(refused.body().get("reasons").get(0).get("message").asString()).contains("2000.00");
        }

        @Test
        void groupsCountingInterestWhenDueCannotLendYet() {
            save(GroupRole.MEMBER, "10000");
            String productId = product(Map.of());
            JsonNode settings = api().get(group.path("/settings"), group.president()).body();
            Map<String, Object> changed = new LinkedHashMap<>(json.convertValue(settings.get("settings"), Map.class));
            changed.put("interestRecognition", "WHEN_DUE");
            String changeId = api().put(group.path("/settings"), as(GroupRole.TREASURER),
                            Map.of("version", settings.get("version").asLong(), "settings", changed))
                    .expect(202).body().get("pendingChange").get("changeId").asString();
            api().post(group.path("/settings/changes/" + changeId + "/confirm"), group.president(), null).expect(200);

            request(GroupRole.MEMBER, productId, "5000", 3).expect(400, "LOAN_TERMS_UNSUPPORTED");
        }
    }

    // --- approving ----------------------------------------------------------------------------

    @Nested
    class Approving {

        @Test
        void twoApprovalsFromThePresidentAndTheTreasurer() {
            save(GroupRole.MEMBER, "50000");
            String loanId = request(GroupRole.MEMBER, product(Map.of()), "20000", 3).expect(201).text("loanId");
            assertThat(loan(loanId).get("requiredApprovals").asInt()).isEqualTo(2);

            approve(GroupRole.MEMBER, loanId).expect(403, "SELF_APPROVAL_FORBIDDEN");
            approve(GroupRole.SECRETARY, loanId).expect(403, "LOAN_APPROVER_NOT_ALLOWED");
            approve(GroupRole.AUDITOR, loanId).expect(403, "LOAN_APPROVER_NOT_ALLOWED");
            assertThat(approve(GroupRole.PRESIDENT, loanId).expect(200).text("status")).isEqualTo("PARTIALLY_COUNTERSIGNED");
            approve(GroupRole.PRESIDENT, loanId).expect(409, "DUPLICATE_APPROVAL");
            JsonNode approved = approve(GroupRole.TREASURER, loanId).expect(200).body();

            assertThat(approved.get("status").asString()).isEqualTo("APPROVED");
            assertThat(approved.get("approvals")).extracting(a -> a.get("role").asString()).containsExactly("PRESIDENT", "TREASURER");
            assertThat(approved.get("waitingFor")).isEmpty();
        }

        @Test
        void oneApprovalBelowTheThreshold() {
            save(GroupRole.MEMBER, "50000");
            String loanId = request(GroupRole.MEMBER, product(Map.of("dualApprovalThreshold", "100000")), "20000", 3)
                    .expect(201).text("loanId");
            assertThat(loan(loanId).get("requiredApprovals").asInt()).isEqualTo(1);
            assertThat(approve(GroupRole.PRESIDENT, loanId).expect(200).text("status")).isEqualTo("APPROVED");
        }

        @Test
        void theSecretaryStandsInForABorrowingPresident() {
            save(GroupRole.MEMBER, "50000");
            String loanId = request(GroupRole.PRESIDENT, product(Map.of()), "20000", 3).expect(201).text("loanId");
            JsonNode waiting = loan(loanId).get("waitingFor");
            assertThat(waiting).extracting(JsonNode::asString).containsExactlyInAnyOrder("SECRETARY", "TREASURER");

            approve(GroupRole.PRESIDENT, loanId).expect(403, "SELF_APPROVAL_FORBIDDEN");
            approve(GroupRole.TREASURER, loanId).expect(200);
            assertThat(approve(GroupRole.SECRETARY, loanId).expect(200).text("status")).isEqualTo("APPROVED");
        }

        @Test
        void aRejectionEndsTheLoan() {
            save(GroupRole.MEMBER, "50000");
            String loanId = request(GroupRole.MEMBER, product(Map.of()), "20000", 3).expect(201).text("loanId");
            api().post(group.path("/loans/" + loanId + "/reject"), group.president(), Map.of("reason", "not this month"))
                    .expect(200);
            JsonNode rejected = loan(loanId);
            assertThat(rejected.get("status").asString()).isEqualTo("REJECTED");
            assertThat(rejected.get("rejectionReason").asString()).isEqualTo("not this month");
            approve(GroupRole.TREASURER, loanId).expect(409, "LOAN_INVALID_TRANSITION");
        }

        @Test
        void dualApprovalNeedsARecentPassword() {
            save(GroupRole.MEMBER, "50000");
            String loanId = request(GroupRole.MEMBER, product(Map.of()), "20000", 3).expect(201).text("loanId");
            clock.advance(Duration.ofMinutes(6));
            approve(GroupRole.PRESIDENT, loanId).expect(403, "REAUTHENTICATION_REQUIRED");
            User steppedUp = api().reauthenticate(group.president());
            api().post(group.path("/loans/" + loanId + "/approve"), steppedUp, Map.of()).expect(200);
        }

        @Test
        void onlyTheBorrowerCancelsAndOnlyBeforeThePayout() {
            save(GroupRole.MEMBER, "50000");
            String productId = product(Map.of("allowConcurrentLoans", true));
            String loanId = request(GroupRole.MEMBER, productId, "10000", 3).expect(201).text("loanId");
            api().post(group.path("/loans/" + loanId + "/cancel"), group.president(), null).expect(403, "FORBIDDEN");
            assertThat(api().post(group.path("/loans/" + loanId + "/cancel"), as(GroupRole.MEMBER), null).expect(200).text("status"))
                    .isEqualTo("CANCELLED");

            String paidOut = disbursedLoan(productId, "10000", 3);
            api().post(group.path("/loans/" + paidOut + "/cancel"), as(GroupRole.MEMBER), null).expect(409, "LOAN_INVALID_TRANSITION");
        }

        @Test
        void aMemberSeesOnlyTheirOwnLoans() {
            save(GroupRole.MEMBER, "50000");
            String productId = product(Map.of("allowConcurrentLoans", true));
            String mine = request(GroupRole.MEMBER, productId, "10000", 3).expect(201).text("loanId");
            String theirs = request(GroupRole.SECRETARY, productId, "10000", 3).expect(201).text("loanId");

            JsonNode list = api().get(group.path("/loans"), as(GroupRole.MEMBER)).expect(200).body().get("items");
            assertThat(list).extracting(l -> l.get("loanId").asString()).containsExactly(mine);
            api().get(group.path("/loans/" + theirs), as(GroupRole.MEMBER)).expect(404, "NOT_FOUND");
            assertThat(api().get(group.path("/loans"), as(GroupRole.AUDITOR)).expect(200).body().get("items")).hasSize(2);
        }
    }

    // --- disbursing ----------------------------------------------------------------------------

    @Nested
    class Disbursing {

        @Test
        void thePayoutPostsTheJournalBuildsTheScheduleAndHappensOnce() throws SQLException {
            save(GroupRole.MEMBER, "100000");
            String productId = product(Map.of());
            String loanId = request(GroupRole.MEMBER, productId, "30000", 3).expect(201).text("loanId");
            approve(GroupRole.PRESIDENT, loanId).expect(200);
            approve(GroupRole.TREASURER, loanId).expect(200);
            disburse(GroupRole.TREASURER, loanId, "pay").expect(400, "VALIDATION_FAILED");   // key too short

            String key = "pay-" + UUID.randomUUID();
            JsonNode paid = disburse(GroupRole.TREASURER, loanId, key).expect(200).body();
            assertThat(paid.get("status").asString()).isEqualTo("DISBURSED");
            assertThat(paid.get("maturesOn").asString()).isEqualTo(today().plusMonths(3).toString());
            assertThat(paid.get("outstanding").get("total").asString()).isEqualTo("34500.00");   // 30,000 + 5% x 3 months

            JsonNode rows = schedule(loanId).get("installments");
            assertThat(rows).hasSize(3);
            assertThat(rows).allSatisfy(r -> {
                assertThat(r.get("principalDue").asString()).isEqualTo("10000.00");
                assertThat(r.get("interestDue").asString()).isEqualTo("1500.00");
            });

            assertThat(disburse(GroupRole.TREASURER, loanId, key).expect(200).text("status")).as("a retry returns the original")
                    .isEqualTo("DISBURSED");
            disburse(GroupRole.TREASURER, loanId, "pay-" + UUID.randomUUID()).expect(409, "LOAN_INVALID_TRANSITION");

            long groupId = LedgerTestSupport.actor(group.groupId(), group.memberId(GroupRole.MEMBER)).groupId();
            assertThat(reconciler.reconcile(groupId).clean()).isTrue();
            // A payout cannot be reversed; a mistaken loan is cancelled before payout instead.
            api().post(group.path("/journals/" + journalOfDisbursement(loanId) + "/reverse"), as(GroupRole.TREASURER),
                    Map.of("reason", "wrong")).expect(409, "JOURNAL_NOT_REVERSIBLE");
        }

        @Test
        void withThreeOfficersThePresidentGivesTheSingleApprovalAndTheTreasurerPaysOut() {
            // Owner decision (Phase 3 review): the approver of a single-approval loan cannot record its payout, and only
            // the Treasurer records payouts - so the Treasurer does not give that approval, and no loan can get stuck.
            save(GroupRole.MEMBER, "100000");
            String productId = product(Map.of("dualApprovalThreshold", "500000", "allowConcurrentLoans", true));
            String loanId = request(GroupRole.MEMBER, productId, "10000", 3).expect(201).text("loanId");
            assertThat(loan(loanId).get("waitingFor")).extracting(JsonNode::asString).containsExactly("PRESIDENT");

            approve(GroupRole.TREASURER, loanId).expect(403, "LOAN_APPROVER_NOT_ALLOWED");
            approve(GroupRole.PRESIDENT, loanId).expect(200);
            disburse(GroupRole.TREASURER, loanId, "pay-" + UUID.randomUUID()).expect(200);

            // A borrowing President: the Secretary gives the approval instead, and the Treasurer pays out.
            String presidents = request(GroupRole.PRESIDENT, productId, "10000", 3).expect(201).text("loanId");
            assertThat(loan(presidents).get("waitingFor")).extracting(JsonNode::asString).containsExactly("SECRETARY");
            approve(GroupRole.SECRETARY, presidents).expect(200);
            disburse(GroupRole.TREASURER, presidents, "pay-" + UUID.randomUUID()).expect(200);
        }

        @Test
        void aPayoutNeedsTheCash() {
            save(GroupRole.MEMBER, "20000");
            String productId = product(Map.of());
            String loanId = request(GroupRole.MEMBER, productId, "20000", 3).expect(201).text("loanId");
            approve(GroupRole.PRESIDENT, loanId).expect(200);
            approve(GroupRole.TREASURER, loanId).expect(200);
            // The cash leaves before the payout (a reversed contribution).
            String journal = api().get(group.path("/members/" + group.memberId(GroupRole.MEMBER) + "/transactions"),
                    as(GroupRole.TREASURER)).body().get("items").get(0).get("journalId").asString();
            String requestId = api().post(group.path("/journals/" + journal + "/reverse"), as(GroupRole.TREASURER),
                    Map.of("reason", "counted twice")).expect(202).text("requestId");
            api().post(group.path("/reversals/" + requestId + "/approve"), group.president(), null).expect(200);

            disburse(GroupRole.TREASURER, loanId, "pay-" + UUID.randomUUID()).expect(422, "INSUFFICIENT_GROUP_FUNDS");
        }
    }

    // --- repaying ----------------------------------------------------------------------------

    @Nested
    class Repaying {

        @Test
        void interestThenPrincipalOldestFirstUntilSettled() {
            save(GroupRole.MEMBER, "100000");
            String loanId = disbursedLoan(product(Map.of()), "30000", 3);   // 3 x (10,000 + 1,500)

            JsonNode afterFirst = repay(loanId, "12000", "repay-" + UUID.randomUUID()).expect(201).body();
            assertThat(afterFirst.get("repayments").get(0).get("interest").asString()).isEqualTo("2000.00");
            assertThat(afterFirst.get("repayments").get(0).get("principal").asString()).isEqualTo("10000.00");
            JsonNode rows = schedule(loanId).get("installments");
            assertThat(rows.get(0).get("status").asString()).isEqualTo("PAID");
            assertThat(rows.get(1).get("status").asString()).isEqualTo("PARTIAL");
            assertThat(rows.get(1).get("interestPaid").asString()).isEqualTo("500.00");
            assertThat(rows.get(1).get("principalPaid").asString()).isEqualTo("0.00");

            repay(loanId, "22501", "repay-" + UUID.randomUUID()).expect(422, "LOAN_OVERPAYMENT");
            JsonNode settled = repay(loanId, "22500", "repay-" + UUID.randomUUID()).expect(201).body();
            assertThat(settled.get("status").asString()).isEqualTo("SETTLED");
            assertThat(settled.get("outstanding").get("total").asString()).isEqualTo("0.00");
            repay(loanId, "1000", "repay-" + UUID.randomUUID()).expect(409, "LOAN_INVALID_TRANSITION");
        }

        @Test
        void aRetryRecordsOnceAndAChangedRetryIsRefused() {
            save(GroupRole.MEMBER, "100000");
            String loanId = disbursedLoan(product(Map.of()), "30000", 3);
            String key = "repay-" + UUID.randomUUID();
            repay(loanId, "5000", key).expect(201);
            assertThat(repay(loanId, "5000", key).expect(201).body().get("repayments")).hasSize(1);
            repay(loanId, "6000", key).expect(409, "IDEMPOTENCY_CONFLICT");
            repay(loanId, "1000", null).expect(400, "VALIDATION_FAILED");
        }

        @Test
        void aReversedRepaymentIsOwedAgainAndReopensASettledLoan() throws SQLException {
            save(GroupRole.MEMBER, "100000");
            String loanId = disbursedLoan(product(Map.of()), "30000", 3);
            repay(loanId, "20000", "repay-" + UUID.randomUUID()).expect(201);
            JsonNode settled = repay(loanId, "14500", "repay-" + UUID.randomUUID()).expect(201).body();
            assertThat(settled.get("status").asString()).isEqualTo("SETTLED");

            String journal = settled.get("repayments").get(1).get("journalId").asString();
            String requestId = api().post(group.path("/journals/" + journal + "/reverse"), as(GroupRole.TREASURER),
                    Map.of("reason", "recorded against the wrong loan")).expect(202).text("requestId");
            api().post(group.path("/reversals/" + requestId + "/approve"), as(GroupRole.TREASURER), null)
                    .expect(403, "SELF_APPROVAL_FORBIDDEN");
            api().post(group.path("/reversals/" + requestId + "/approve"), group.president(), null).expect(200);

            JsonNode reopened = loan(loanId);
            assertThat(reopened.get("status").asString()).isEqualTo("DISBURSED");
            assertThat(reopened.get("outstanding").get("total").asString()).isEqualTo("14500.00");
            assertThat(reopened.get("repayments").get(1).get("reversed").asBoolean()).isTrue();
            assertThat(schedule(loanId).get("installments").get(2).get("status").asString()).isEqualTo("PENDING");
            long groupId = LedgerTestSupport.actor(group.groupId(), group.memberId(GroupRole.MEMBER)).groupId();
            assertThat(reconciler.reconcile(groupId).clean()).isTrue();
        }
    }

    // --- review fixes: each test failed before its fix --------------------------------------------

    @Nested
    class ReviewFixes {

        @Test
        void loansApprovedTogetherCannotBlockEachOthersPayout() {
            save(GroupRole.MEMBER, "100000");
            String productId = product(Map.of("allowConcurrentLoans", true));
            // Both requests fit the cash on their own, because neither is approved yet.
            String first = request(GroupRole.MEMBER, productId, "80000", 3).expect(201).text("loanId");
            String second = request(GroupRole.SECRETARY, productId, "80000", 3).expect(201).text("loanId");
            approve(GroupRole.PRESIDENT, first).expect(200);
            approve(GroupRole.TREASURER, first).expect(200);
            approve(GroupRole.PRESIDENT, second).expect(200);
            // The last approval of the second loan would commit cash the first one already holds.
            approve(GroupRole.TREASURER, second).expect(422, "INSUFFICIENT_GROUP_FUNDS");
            disburse(GroupRole.TREASURER, first, "pay-" + UUID.randomUUID()).expect(200);
        }

        @Test
        void aRepaymentThatFindsTheLoanLateMarksItOverdueWithAuditAndEvent() throws SQLException {
            save(GroupRole.MEMBER, "100000");
            String loanId = disbursedLoan(product(Map.of()), "30000", 3);
            // A day after the first due date, before the nightly job has run.
            clock.advance(Duration.between(BusinessTime.startOf(today()), BusinessTime.startOf(today().plusMonths(1).plusDays(1))));
            everyoneSignsInAgain();
            repay(loanId, "1000", "repay-" + UUID.randomUUID()).expect(201);

            assertThat(loan(loanId).get("status").asString()).isEqualTo("OVERDUE");
            assertThat(auditRows(loanId, "LOAN_OVERDUE")).isEqualTo(1);
            overdueJob.runNightly();
            assertThat(auditRows(loanId, "LOAN_OVERDUE")).as("the job does not mark it again").isEqualTo(1);
        }

        @Test
        void installmentsWithNothingDueDoNotKeepALoanOpen() {
            save(GroupRole.MEMBER, "100000");
            // 18 RWF over 12 months at 0%: a payment of 2 RWF clears it after 9 months; months 10-12 owe nothing.
            String productId = product(Map.of("interestMethod", "REDUCING_BALANCE", "interestRatePercent", "0"));
            String loanId = disbursedLoan(productId, "18", 12);
            JsonNode rows = schedule(loanId).get("installments");
            assertThat(rows.get(11).get("principalDue").asString()).isEqualTo("0.00");
            assertThat(rows.get(11).get("status").asString()).isEqualTo("PAID");

            assertThat(repay(loanId, "18", "repay-" + UUID.randomUUID()).expect(201).text("status")).isEqualTo("SETTLED");
        }

        @Test
        void aPlainMemberCannotEvenTellThatSomeoneElsesLoanExists() {
            save(GroupRole.MEMBER, "100000");
            String loanId = request(GroupRole.SECRETARY, product(Map.of()), "10000", 3).expect(201).text("loanId");
            api().post(group.path("/loans/" + loanId + "/approve"), as(GroupRole.MEMBER), Map.of()).expect(404, "NOT_FOUND");
            api().post(group.path("/loans/" + loanId + "/reject"), as(GroupRole.MEMBER), Map.of("reason", "no")).expect(404, "NOT_FOUND");
            api().post(group.path("/loans/" + loanId + "/cancel"), as(GroupRole.MEMBER), null).expect(404, "NOT_FOUND");
        }

        @Test
        void aRetryAfterMidnightIsStillTheSameRequest() {
            save(GroupRole.MEMBER, "100000");
            String loanId = request(GroupRole.MEMBER, product(Map.of()), "10000", 3).expect(201).text("loanId");
            approve(GroupRole.PRESIDENT, loanId).expect(200);
            approve(GroupRole.TREASURER, loanId).expect(200);
            String key = "pay-" + UUID.randomUUID();
            disburse(GroupRole.TREASURER, loanId, key).expect(200);

            clock.advance(Duration.between(now(), BusinessTime.startOf(today().plusDays(1)).plus(Duration.ofHours(1))));
            everyoneSignsInAgain();
            assertThat(disburse(GroupRole.TREASURER, loanId, key).expect(200).text("status")).isEqualTo("DISBURSED");
        }

        @Test
        void aLoanNoOfficerCouldApproveIsRefusedUpFront() {
            TestGroup alone = new GroupFixture(api(), sms).create("Alone " + UUID.randomUUID().toString().substring(0, 8));
            String productId = api().post(alone.path("/loan-products"), alone.president(),
                    Map.of("name", "Standard", "terms", terms(Map.of()))).expect(201).text("productId");
            Response refused = api().post(alone.path("/loans"), alone.president(),
                    Map.of("productId", productId, "amount", "1000", "termMonths", 1)).expect(422, "LOAN_NOT_ELIGIBLE");
            assertThat(reasons(refused)).contains("APPROVERS_UNAVAILABLE");
        }

        @Test
        void moneyTheTreasurerRecordsOnTheirOwnLoanIsFlagged() throws SQLException {
            save(GroupRole.MEMBER, "100000");
            String loanId = request(GroupRole.TREASURER, product(Map.of()), "30000", 3).expect(201).text("loanId");
            approve(GroupRole.PRESIDENT, loanId).expect(200);
            approve(GroupRole.SECRETARY, loanId).expect(200);
            assertThat(disburse(GroupRole.TREASURER, loanId, "pay-" + UUID.randomUUID()).expect(200).body()
                    .get("disbursedByBorrower").asBoolean()).isTrue();
            JsonNode repaid = repay(loanId, "5000", "repay-" + UUID.randomUUID()).expect(201).body();
            assertThat(repaid.get("repayments").get(0).get("recordedByBorrower").asBoolean()).isTrue();
            assertThat(auditReasons(loanId, "LOAN_DISBURSED")).containsExactly("recorded by the borrower on their own loan");

            // A member's loan paid out by the Treasurer is not flagged.
            String members = disbursedLoan(product(Map.of()), "10000", 3);
            assertThat(loan(members).get("disbursedByBorrower").asBoolean()).isFalse();
        }
    }

    private int auditRows(String entityId, String action) throws SQLException {
        return auditReasons(entityId, action).size();
    }

    private List<String> auditReasons(String entityId, String action) throws SQLException {
        try (Connection owner = PostgresTestDatabase.connectAsOwner("app_it");
             PreparedStatement query = owner.prepareStatement(
                     "SELECT COALESCE(reason, '') FROM audit_logs WHERE action = ? AND entity_id = ? ORDER BY chain_seq")) {
            query.setString(1, action);
            query.setString(2, entityId);
            List<String> reasons = new ArrayList<>();
            try (ResultSet rs = query.executeQuery()) {
                while (rs.next()) {
                    reasons.add(rs.getString(1));
                }
            }
            return reasons;
        }
    }

    // --- overdue -------------------------------------------------------------------------------

    @Nested
    class Overdue {

        @Test
        void theNightlyJobMarksLateLoansOnceAndRepaymentClearsIt() throws SQLException {
            save(GroupRole.MEMBER, "100000");
            String loanId = disbursedLoan(product(Map.of("graceDays", 3)), "30000", 3);
            long groupId = LedgerTestSupport.actor(group.groupId(), group.memberId(GroupRole.MEMBER)).groupId();

            // The first installment is due in a month; three grace days later it is still not overdue.
            clock.advance(Duration.between(BusinessTime.startOf(today()), BusinessTime.startOf(today().plusMonths(1).plusDays(3))));
            everyoneSignsInAgain();
            assertThat(runFor(groupId)).isZero();
            assertThat(loan(loanId).get("status").asString()).isEqualTo("DISBURSED");
            // A part payment on the last day of grace does not make it overdue either.
            repay(loanId, "1000", "repay-" + UUID.randomUUID()).expect(201);
            assertThat(schedule(loanId).get("installments").get(0).get("status").asString()).isEqualTo("PARTIAL");
            assertThat(loan(loanId).get("status").asString()).isEqualTo("DISBURSED");

            clock.advance(Duration.ofDays(1));
            everyoneSignsInAgain();
            assertThat(runFor(groupId)).isEqualTo(1);
            assertThat(runFor(groupId)).as("re-running changes nothing").isZero();
            overdueJob.runNightly();
            assertThat(loan(loanId).get("status").asString()).isEqualTo("OVERDUE");
            assertThat(schedule(loanId).get("installments").get(0).get("status").asString()).isEqualTo("OVERDUE");
            assertThat(auditCount(loanId, "LOAN_OVERDUE")).isEqualTo(1);

            repay(loanId, "10500", "repay-" + UUID.randomUUID()).expect(201);
            assertThat(loan(loanId).get("status").asString()).as("nothing late any more").isEqualTo("DISBURSED");
        }

        /** One group's pass, exactly as the nightly job runs it: no user, the SYSTEM role, its own transaction. */
        private int runFor(long groupId) {
            TransactionTemplate transaction = new TransactionTemplate(transactionManager);
            Integer marked = TenantContext.callAs(null, new TenantContext.GroupScope(groupId, null, null, "SYSTEM"),
                    () -> transaction.execute(status -> overdueJob.markOverdueInCurrentGroup()));
            return marked == null ? 0 : marked;
        }

        private int auditCount(String loanId, String action) throws SQLException {
            try (Connection owner = PostgresTestDatabase.connectAsOwner("app_it");
                 PreparedStatement query = owner.prepareStatement(
                         "SELECT count(*) FROM audit_logs WHERE action = ? AND entity_id = ?")) {
                query.setString(1, action);
                query.setString(2, loanId);
                try (ResultSet rs = query.executeQuery()) {
                    rs.next();
                    return rs.getInt(1);
                }
            }
        }
    }
}
