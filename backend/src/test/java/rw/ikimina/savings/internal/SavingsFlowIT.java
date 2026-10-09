package rw.ikimina.savings.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import rw.ikimina.groups.GroupRole;
import rw.ikimina.shared.time.BusinessTime;
import rw.ikimina.support.Api;
import rw.ikimina.support.Api.Response;
import rw.ikimina.support.Api.User;
import rw.ikimina.support.GroupFixture;
import rw.ikimina.support.GroupFixture.TestGroup;
import rw.ikimina.support.IntegrationTest;
import tools.jackson.databind.JsonNode;

/** Spec 8, 17.3: buckets, obligations, contributions, statements and two-step reversals, through the API. */
class SavingsFlowIT extends IntegrationTest {

    @Autowired
    private ObligationGenerator generator;

    private TestGroup group;

    @BeforeEach
    void group() {
        group = new GroupFixture(api(), sms).createWithEveryRole("Savings " + UUID.randomUUID().toString().substring(0, 8));
    }

    private LocalDate today() {
        return BusinessTime.today(clock);
    }

    private Map<String, Object> terms(boolean mandatory, String minimum, String frequency) {
        Map<String, Object> terms = new LinkedHashMap<>();
        terms.put("mandatory", mandatory);
        terms.put("minimumContribution", minimum);
        terms.put("contributionFrequency", frequency);
        terms.put("withdrawable", false);
        return terms;
    }

    private String monthlyBucket(String minimum) {
        return api().post(group.path("/buckets"), group.president(), Map.of("name", "Ubwizigame", "type", "SAVINGS",
                        "cycleType", "ROLLING", "startDate", today().withDayOfMonth(1).toString(),
                        "terms", terms(true, minimum, "MONTHLY")))
                .expect(201).text("bucketId");
    }

    private String openBucket() {
        return api().post(group.path("/buckets"), group.president(), Map.of("name", "Open fund", "type", "SAVINGS",
                        "cycleType", "ROLLING", "startDate", "2026-01-01", "terms", terms(false, "0", "ADHOC")))
                .expect(201).text("bucketId");
    }

    private Response contribute(String bucketId, String amount, Map<String, Object> extra, String key) {
        Map<String, Object> body = new HashMap<>(Map.of("memberId", group.memberId(GroupRole.MEMBER), "bucketId", bucketId,
                "amount", amount, "method", "CASH"));
        body.putAll(extra);
        return api().contribute(group.groupId(), group.as(GroupRole.TREASURER), body, key);
    }

    private JsonNode memberObligations(String bucketId) {
        return api().get(group.path("/obligations?bucketId=" + bucketId + "&memberId=" + group.memberId(GroupRole.MEMBER)),
                group.as(GroupRole.TREASURER)).expect(200).body().get("items");
    }

    private String total(User as) {
        return api().get(group.path("/members/" + group.memberId(GroupRole.MEMBER) + "/balances"), as).expect(200).text("total");
    }

    /** After the clock jumps, everyone signs in again (access tokens last 15 minutes). */
    private User fresh(User user) {
        Response login = api().login(user.phone(), user.password(), user.ip()).expect(200);
        return user.withAccessToken(login.text("accessToken")).withRefreshToken(login.cookie(Api.REFRESH_COOKIE).getValue());
    }

    @Nested
    class Obligations {

        @Test
        void aMandatoryBucketCreatesThisPeriodsObligationForEveryActiveMember() {
            String bucketId = monthlyBucket("5000");
            JsonNode all = api().get(group.path("/obligations?bucketId=" + bucketId), group.as(GroupRole.SECRETARY)).expect(200).body();
            assertThat(all.get("totalItems").asInt()).isEqualTo(5);
            JsonNode mine = memberObligations(bucketId).get(0);
            assertThat(mine.get("status").asString()).isEqualTo("OPEN");
            assertThat(mine.get("amountDue").asString()).isEqualTo("5000.00");
            assertThat(mine.get("dueDate").asString()).isEqualTo(today().withDayOfMonth(today().lengthOfMonth()).toString());
        }

        @Test
        void paymentsSettleWhatIsOwedAndAnOverpaymentCarriesToNextMonth() {
            String bucketId = monthlyBucket("5000");
            contribute(bucketId, "3000", Map.of(), null).expect(201);
            assertThat(memberObligations(bucketId).get(0).get("status").asString()).isEqualTo("PARTIAL");
            contribute(bucketId, "9000", Map.of(), null).expect(201);
            JsonNode settled = memberObligations(bucketId).get(0);
            assertThat(settled.get("status").asString()).isEqualTo("PAID");
            assertThat(settled.get("amountPaid").asString()).isEqualTo("5000.00");

            clock.advance(Duration.ofDays(today().lengthOfMonth() - today().getDayOfMonth() + 1L));
            generator.runNightly();

            User treasurer = fresh(group.as(GroupRole.TREASURER));
            JsonNode both = api().get(group.path("/obligations?bucketId=" + bucketId + "&memberId=" + group.memberId(GroupRole.MEMBER)),
                    treasurer).expect(200).body().get("items");
            assertThat(both).hasSize(2);
            assertThat(both.get(1).get("status").asString()).as("next month paid from the carried 7000").isEqualTo("PAID");
            assertThat(total(treasurer)).isEqualTo("12000.00");

            JsonNode othersLastMonth = api().get(group.path("/obligations?bucketId=" + bucketId + "&memberId="
                    + group.memberId(GroupRole.AUDITOR)), treasurer).expect(200).body().get("items").get(0);
            assertThat(othersLastMonth.get("status").asString()).isEqualTo("OVERDUE");
        }

        @Test
        void rerunningTheGeneratorCreatesNothingNew() {
            String bucketId = monthlyBucket("5000");
            generator.runNightly();
            generator.runNightly();
            assertThat(api().get(group.path("/obligations?bucketId=" + bucketId), group.as(GroupRole.SECRETARY))
                    .body().get("totalItems").asInt()).isEqualTo(5);
        }

        @Test
        void anOrdinaryMemberSeesOnlyTheirOwnObligations() {
            String bucketId = monthlyBucket("5000");
            JsonNode seen = api().get(group.path("/obligations?bucketId=" + bucketId), group.as(GroupRole.MEMBER)).expect(200).body();
            assertThat(seen.get("totalItems").asInt()).isEqualTo(1);
            assertThat(seen.get("items").get(0).get("memberId").asString()).isEqualTo(group.memberId(GroupRole.MEMBER));
        }
    }

    @Nested
    class Recording {

        @Test
        void aRetryReturnsTheOriginalAndAChangedRetryIsRefused() {
            String bucketId = openBucket();
            Response first = contribute(bucketId, "2500", Map.of(), "retry-key-1").expect(201);
            Response again = contribute(bucketId, "2500", Map.of(), "retry-key-1").expect(201);
            assertThat(again.text("transactionId")).isEqualTo(first.text("transactionId"));
            assertThat(total(group.as(GroupRole.TREASURER))).isEqualTo("2500.00");
            contribute(bucketId, "9999", Map.of(), "retry-key-1").expect(409, "IDEMPOTENCY_CONFLICT");
        }

        @Test
        void aMomoReferenceIsRequiredAndCannotBeUsedTwice() {
            String bucketId = openBucket();
            contribute(bucketId, "1000", Map.of("method", "MOMO_MANUAL"), null).expect(400, "VALIDATION_FAILED");
            Response retried = contribute(bucketId, "1000", Map.of("method", "MOMO_MANUAL", "externalRef", "MP241009.1530.A1"), "momo-key-0001")
                    .expect(201);
            contribute(bucketId, "1000", Map.of("method", "MOMO_MANUAL", "externalRef", "MP241009.1530.A1"), "momo-key-0001").expect(201);
            contribute(bucketId, "1000", Map.of("method", "MOMO_MANUAL", "externalRef", "MP241009.1530.A1"), "momo-key-0002")
                    .expect(409, "DUPLICATE_EXTERNAL_REF");
            assertThat(retried.text("externalRef")).isEqualTo("MP241009.1530.A1");
        }

        @Test
        void badAmountsDatesAndMissingKeysAreRefused() {
            String bucketId = openBucket();
            contribute(bucketId, "1000.50", Map.of(), null).expect(400, "VALIDATION_FAILED");
            contribute(bucketId, "0", Map.of(), null).expect(400, "VALIDATION_FAILED");
            contribute(bucketId, "-500", Map.of(), null).expect(400, "VALIDATION_FAILED");
            contribute(bucketId, "1000", Map.of("businessDate", today().plusDays(1).toString()), null).expect(400, "VALIDATION_FAILED");
            api().call(HttpMethod.POST, group.path("/contributions"), group.as(GroupRole.TREASURER).accessToken(),
                    "{\"memberId\":\"" + group.memberId(GroupRole.MEMBER) + "\",\"bucketId\":\"" + bucketId
                            + "\",\"amount\":1000,\"method\":\"CASH\"}", null, Map.of("Idempotency-Key", "number-amount"))
                    .expect(400, "VALIDATION_FAILED");
            api().call(HttpMethod.POST, group.path("/contributions"), group.as(GroupRole.TREASURER).accessToken(),
                    Map.of("memberId", group.memberId(GroupRole.MEMBER), "bucketId", bucketId, "amount", "1000", "method", "CASH"), null)
                    .expect(400, "VALIDATION_FAILED");
        }

        @Test
        void onlyActiveMembersAndOpenBucketsTakeMoney() {
            String bucketId = openBucket();
            api().patch(group.path("/members/" + group.memberId(GroupRole.MEMBER)), group.president(), Map.of("status", "SUSPENDED"))
                    .expect(200);
            contribute(bucketId, "1000", Map.of(), null).expect(409, "MEMBER_NOT_ACTIVE");
            api().patch(group.path("/members/" + group.memberId(GroupRole.MEMBER)), group.president(), Map.of("status", "ACTIVE"))
                    .expect(200);

            long version = api().get(group.path("/buckets/" + bucketId), group.president()).body().get("version").asLong();
            api().patch(group.path("/buckets/" + bucketId), group.president(), Map.of("version", version, "status", "CLOSED")).expect(200);
            contribute(bucketId, "1000", Map.of(), null).expect(409, "BUCKET_NOT_ACTIVE");
        }

        @Test
        void onlyTheTreasurerRecordsMoney() {
            String bucketId = openBucket();
            for (GroupRole role : List.of(GroupRole.PRESIDENT, GroupRole.SECRETARY, GroupRole.AUDITOR, GroupRole.MEMBER)) {
                api().contribute(group.groupId(), group.as(role), Map.of("memberId", group.memberId(GroupRole.MEMBER),
                        "bucketId", bucketId, "amount", "1000", "method", "CASH"), null).expect(403, "FORBIDDEN");
            }
        }
    }

    @Nested
    class Reading {

        @Test
        void membersSeeTheirOwnMoneyAndOnlyTheirOwn() {
            String bucketId = openBucket();
            contribute(bucketId, "4000", Map.of(), null).expect(201);
            assertThat(total(group.as(GroupRole.MEMBER))).isEqualTo("4000.00");
            api().get(group.path("/members/" + group.memberId(GroupRole.TREASURER) + "/balances"), group.as(GroupRole.MEMBER))
                    .expect(403, "FORBIDDEN");
            api().get(group.path("/members/" + group.memberId(GroupRole.TREASURER) + "/statement"), group.as(GroupRole.MEMBER))
                    .expect(403, "FORBIDDEN");
            assertThat(total(group.as(GroupRole.AUDITOR))).as("auditors may read everyone's figures").isEqualTo("4000.00");
        }

        @Test
        void theStatementShowsOpeningEveryEntryAndClosing() {
            String bucketId = openBucket();
            LocalDate today = today();
            contribute(bucketId, "1000", Map.of("businessDate", today.minusDays(10).toString()), null).expect(201);
            contribute(bucketId, "2000", Map.of("businessDate", today.minusDays(3).toString()), null).expect(201);
            contribute(bucketId, "4000", Map.of("businessDate", today.toString()), null).expect(201);

            JsonNode statement = api().get(group.path("/members/" + group.memberId(GroupRole.MEMBER) + "/statement?from="
                    + today.minusDays(5) + "&to=" + today + "&bucketId=" + bucketId), group.as(GroupRole.MEMBER)).expect(200).body();
            JsonNode bucket = statement.get("buckets").get(0);
            assertThat(bucket.get("opening").asString()).isEqualTo("1000.00");
            assertThat(bucket.get("entries")).hasSize(2);
            assertThat(bucket.get("entries").get(1).get("balanceAfter").asString()).isEqualTo("7000.00");
            assertThat(bucket.get("closing").asString()).isEqualTo("7000.00");
        }
    }

    @Nested
    class Reversals {

        @Test
        void oneOfficerAsksAnotherApprovesAndTheMoneyAndDuesAreRestored() {
            String bucketId = monthlyBucket("5000");
            Response paid = contribute(bucketId, "5000", Map.of("method", "MOMO_MANUAL", "externalRef", "REF-REV-1"), null).expect(201);
            assertThat(memberObligations(bucketId).get(0).get("status").asString()).isEqualTo("PAID");

            api().post(group.path("/journals/" + paid.text("journalId") + "/reverse"), group.as(GroupRole.SECRETARY),
                    Map.of("reason", "typed the wrong member")).expect(403, "FORBIDDEN");
            String requestId = api().post(group.path("/journals/" + paid.text("journalId") + "/reverse"), group.as(GroupRole.TREASURER),
                    Map.of("reason", "typed the wrong member")).expect(202).text("requestId");
            api().post(group.path("/journals/" + paid.text("journalId") + "/reverse"), group.as(GroupRole.TREASURER),
                    Map.of("reason", "twice")).expect(409, "JOURNAL_ALREADY_REVERSED");
            assertThat(total(group.as(GroupRole.TREASURER))).as("nothing changes before approval").isEqualTo("5000.00");

            api().post(group.path("/reversals/" + requestId + "/approve"), group.as(GroupRole.TREASURER), null)
                    .expect(403, "SELF_APPROVAL_FORBIDDEN");
            Response approved = api().post(group.path("/reversals/" + requestId + "/approve"), group.president(), null).expect(200);
            assertThat(approved.text("status")).isEqualTo("APPROVED");

            assertThat(total(group.as(GroupRole.TREASURER))).isEqualTo("0.00");
            assertThat(memberObligations(bucketId).get(0).get("status").asString()).isEqualTo("OPEN");
            JsonNode txn = api().get(group.path("/members/" + group.memberId(GroupRole.MEMBER) + "/transactions"),
                    group.as(GroupRole.TREASURER)).body().get("items").get(0);
            assertThat(txn.get("reversed").asBoolean()).isTrue();
            // The reference of a reversed entry can be recorded again correctly.
            contribute(bucketId, "5000", Map.of("method", "MOMO_MANUAL", "externalRef", "REF-REV-1"), null).expect(201);
        }

        @Test
        void aRejectedRequestChangesNothing() {
            String bucketId = openBucket();
            Response paid = contribute(bucketId, "3000", Map.of(), null).expect(201);
            String requestId = api().post(group.path("/journals/" + paid.text("journalId") + "/reverse"), group.as(GroupRole.TREASURER),
                    Map.of("reason", "maybe wrong")).expect(202).text("requestId");
            api().post(group.path("/reversals/" + requestId + "/reject"), group.president(), Map.of("reason", "it was right"))
                    .expect(200);
            assertThat(total(group.as(GroupRole.TREASURER))).isEqualTo("3000.00");
            api().post(group.path("/reversals/" + requestId + "/approve"), group.president(), null).expect(409, "CONCURRENT_MODIFICATION");
        }
    }

    @Nested
    class BucketTermsChanges {

        @Test
        void changingMoneyTermsNeedsASecondOfficer() {
            String bucketId = monthlyBucket("5000");
            JsonNode bucket = api().get(group.path("/buckets/" + bucketId), group.president()).body();
            Response proposed = api().patch(group.path("/buckets/" + bucketId), group.as(GroupRole.TREASURER),
                    Map.of("version", bucket.get("version").asLong(), "terms", terms(true, "6000", "MONTHLY"))).expect(202);
            String changeId = proposed.body().get("pendingChange").get("changeId").asString();
            assertThat(proposed.body().get("terms").get("minimumContribution").asString()).isEqualTo("5000.00");

            api().post(group.path("/buckets/" + bucketId + "/changes/" + changeId + "/confirm"), group.as(GroupRole.TREASURER), null)
                    .expect(403, "SELF_APPROVAL_FORBIDDEN");
            Response confirmed = api().post(group.path("/buckets/" + bucketId + "/changes/" + changeId + "/confirm"),
                    group.as(GroupRole.SECRETARY), null).expect(200);
            assertThat(confirmed.body().get("terms").get("minimumContribution").asString()).isEqualTo("6000.00");
        }

        @Test
        void nameChangesApplyAtOnceAndStaleProposalsAreClosed() {
            String bucketId = monthlyBucket("5000");
            long version = api().get(group.path("/buckets/" + bucketId), group.president()).body().get("version").asLong();
            String changeId = api().patch(group.path("/buckets/" + bucketId), group.as(GroupRole.TREASURER),
                            Map.of("version", version, "terms", terms(true, "7000", "MONTHLY")))
                    .expect(202).body().get("pendingChange").get("changeId").asString();
            Response renamed = api().patch(group.path("/buckets/" + bucketId), group.as(GroupRole.SECRETARY),
                    Map.of("version", version, "name", "Ubwizigame bw'umwaka")).expect(200);
            assertThat(renamed.text("name")).isEqualTo("Ubwizigame bw'umwaka");
            api().post(group.path("/buckets/" + bucketId + "/changes/" + changeId + "/confirm"), group.president(), null)
                    .expect(409, "BUCKET_CHANGE_STALE");
        }

        @Test
        void inconsistentTermsAreRefused() {
            api().post(group.path("/buckets"), group.president(), Map.of("name", "Bad 1", "type", "SAVINGS", "cycleType", "ROLLING",
                    "startDate", "2026-01-01", "terms", terms(true, "5000", "ADHOC"))).expect(400, "VALIDATION_FAILED");
            Map<String, Object> withdrawable = terms(false, "0", "ADHOC");
            withdrawable.put("withdrawable", true);
            api().post(group.path("/buckets"), group.president(), Map.of("name", "Bad 2", "type", "SOCIAL_FUND", "cycleType", "ROLLING",
                    "startDate", "2026-01-01", "terms", withdrawable)).expect(400, "VALIDATION_FAILED");
            api().post(group.path("/buckets"), group.president(), Map.of("name", "Bad 3", "type", "SAVINGS", "cycleType", "FIXED_TERM",
                    "startDate", "2026-01-01", "terms", terms(false, "0", "ADHOC"))).expect(400, "VALIDATION_FAILED");
        }
    }
}
