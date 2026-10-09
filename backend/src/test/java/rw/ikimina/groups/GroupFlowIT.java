package rw.ikimina.groups;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import rw.ikimina.support.Api;
import rw.ikimina.support.Api.Response;
import rw.ikimina.support.Api.User;
import rw.ikimina.support.GroupFixture;
import rw.ikimina.support.GroupFixture.Invite;
import rw.ikimina.support.GroupFixture.TestGroup;
import rw.ikimina.support.IntegrationTest;
import tools.jackson.databind.JsonNode;

/** Spec 17.2: groups, members, invitations, bylaws with dual control, transfer of office. */
class GroupFlowIT extends IntegrationTest {

    private GroupFixture groups;

    @BeforeEach
    void fixture() {
        groups = new GroupFixture(api(), sms);
    }

    @Nested
    class Creating {

        @Test
        void creatorBecomesPresidentAndSeesTheGroupInMyGroups() {
            TestGroup group = groups.create("Twisungane");
            Response view = api().get(group.path(""), group.president()).expect(200);
            assertThat(view.text("name")).isEqualTo("Twisungane");
            assertThat(view.text("myRole")).isEqualTo("PRESIDENT");
            assertThat(view.body().get("activeMembers").asInt()).isEqualTo(1);

            JsonNode mine = api().get("/api/v1/me/groups", group.president()).expect(200).body();
            assertThat(mine).hasSize(1);
            assertThat(mine.get(0).get("groupId").asString()).isEqualTo(group.groupId());
            assertThat(mine.get(0).get("memberNumber").asString()).isEqualTo("001");
        }

        @Test
        void newGroupStartsWithDefaultBylaws() {
            TestGroup group = groups.create("Defaults");
            JsonNode settings = api().get(group.path("/settings"), group.president()).expect(200).body();
            assertThat(settings.get("settings").get("interestRecognition").asString()).isEqualTo("WHEN_PAID");
            assertThat(settings.get("settings").get("exitFee").asString()).isEqualTo("0.00");
            assertThat(settings.get("schemaVersion").asInt()).isEqualTo(1);
        }

        @Test
        void groupProfileCanBeEditedBySettingsEditors() {
            TestGroup group = groups.create("Old Name");
            Response updated = api().patch(group.path(""), group.president(),
                    Map.of("name", "Abishyizehamwe", "sector", "Remera", "phone", "0788 000 111")).expect(200);
            assertThat(updated.text("name")).isEqualTo("Abishyizehamwe");
            assertThat(updated.text("phone")).isEqualTo("+250788000111");
        }

        @Test
        void aPersonCanBelongToSeveralGroupsWithDifferentRoles() {
            TestGroup first = groups.create("First");
            TestGroup second = groups.create("Second");
            groups.join(second, first.president(), GroupRole.MEMBER);

            JsonNode mine = api().get("/api/v1/me/groups", first.president()).expect(200).body();
            Map<String, String> roles = new LinkedHashMap<>();
            mine.forEach(g -> roles.put(g.get("name").asString(), g.get("role").asString()));
            assertThat(roles).containsEntry("First", "PRESIDENT").containsEntry("Second", "MEMBER");
        }
    }

    @Nested
    class Invitations {

        @Test
        void theInvitedPhoneJoinsWithTheNextMemberNumber() {
            TestGroup group = groups.create("Invites");
            User alice = api().register("Alice");
            String memberId = groups.join(group, alice, GroupRole.MEMBER);

            Response me = api().get(group.path("/members/" + memberId), alice).expect(200);
            assertThat(me.text("memberNumber")).isEqualTo("002");
            assertThat(me.text("role")).isEqualTo("MEMBER");
        }

        @Test
        void someoneElseCannotUseAForwardedLink() {
            TestGroup group = groups.create("Forwarded");
            User invited = api().register("Invited");
            User other = api().register("Other");
            Invite invite = groups.invite(group, invited.phone(), GroupRole.MEMBER);
            api().post("/api/v1/groups/" + invite.groupId() + "/invitations/accept", other, Map.of("token", invite.token()))
                    .expect(400, "INVITATION_INVALID");
        }

        @Test
        void linksExpireAfterSevenDays() {
            TestGroup group = groups.create("Expiring");
            User late = api().register("Late");
            Invite invite = groups.invite(group, late.phone(), GroupRole.MEMBER);
            clock.advance(Duration.ofDays(7).plusSeconds(1));
            late = signIn(late);                 // a week later the old access token has long expired
            api().post("/api/v1/groups/" + invite.groupId() + "/invitations/accept", late, Map.of("token", invite.token()))
                    .expect(400, "INVITATION_INVALID");
        }

        @Test
        void linksAreSingleUseAndCanBeRevoked() {
            TestGroup group = groups.create("Revoking");
            User joiner = api().register("Joiner");
            Invite used = groups.invite(group, joiner.phone(), GroupRole.MEMBER);
            api().post("/api/v1/groups/" + used.groupId() + "/invitations/accept", joiner, Map.of("token", used.token())).expect(200);
            api().post("/api/v1/groups/" + used.groupId() + "/invitations/accept", joiner, Map.of("token", used.token()))
                    .expect(400, "INVITATION_INVALID");

            User revoked = api().register("Revoked");
            Invite second = groups.invite(group, revoked.phone(), GroupRole.MEMBER);
            JsonNode pending = api().get(group.path("/invitations"), group.president()).expect(200).body();
            assertThat(pending).hasSize(1);
            String invitationId = pending.get(0).get("invitationId").asString();
            api().post(group.path("/invitations/" + invitationId + "/revoke"), group.president(), null).expect(204);
            api().post("/api/v1/groups/" + second.groupId() + "/invitations/accept", revoked, Map.of("token", second.token()))
                    .expect(400, "INVITATION_INVALID");
        }

        @Test
        void aWrongTokenOrWrongGroupRevealsNothing() {
            TestGroup group = groups.create("Tokens");
            TestGroup other = groups.create("Other Group");
            User invited = api().register("Token Holder");
            Invite invite = groups.invite(group, invited.phone(), GroupRole.MEMBER);
            api().post("/api/v1/groups/" + group.groupId() + "/invitations/accept", invited, Map.of("token", "nonsense"))
                    .expect(400, "INVITATION_INVALID");
            api().post("/api/v1/groups/" + other.groupId() + "/invitations/accept", invited, Map.of("token", invite.token()))
                    .expect(400, "INVITATION_INVALID");
        }

        @Test
        void existingMembersCannotBeInvitedAgainAndOfficesAreNotInvited() {
            TestGroup group = groups.create("Members Only");
            api().post(group.path("/invitations"), group.president(), Map.of("phone", group.president().phone(), "role", "MEMBER"))
                    .expect(409, "ALREADY_MEMBER");
            api().post(group.path("/invitations"), group.president(), Map.of("phone", Api.newPhone(), "role", "TREASURER"))
                    .expect(400, "VALIDATION_FAILED");
        }
    }

    @Nested
    class Members {

        private TestGroup group;

        @BeforeEach
        void group() {
            group = groups.createWithEveryRole("Members");
        }

        @Test
        void officersSeeTheMemberListOrdinaryMembersDoNot() {
            JsonNode page = api().get(group.path("/members"), group.as(GroupRole.SECRETARY)).expect(200).body();
            assertThat(page.get("totalItems").asInt()).isEqualTo(5);
            assertThat(page.get("items").get(0).get("memberNumber").asString()).isEqualTo("001");
            api().get(group.path("/members"), group.as(GroupRole.MEMBER)).expect(403, "FORBIDDEN");
        }

        @Test
        void aMemberSeesTheirOwnRecordButNotOthers() {
            User member = group.as(GroupRole.MEMBER);
            api().get(group.path("/members/" + group.memberId(GroupRole.MEMBER)), member).expect(200);
            api().get(group.path("/members/" + group.memberId(GroupRole.TREASURER)), member).expect(403, "FORBIDDEN");
        }

        @Test
        void nobodyCanChangeTheirOwnRoleOrStatus() {
            api().patch(group.path("/members/" + group.memberId(GroupRole.SECRETARY)), group.as(GroupRole.SECRETARY),
                    Map.of("role", "MEMBER")).expect(403, "SELF_MODIFICATION_FORBIDDEN");
        }

        @Test
        void anOccupiedOfficeIsNotReassignedAndHoldersAreProtected() {
            api().patch(group.path("/members/" + group.memberId(GroupRole.MEMBER)), group.president(),
                    Map.of("role", "TREASURER")).expect(409, "OFFICE_OCCUPIED");
            api().patch(group.path("/members/" + group.memberId(GroupRole.TREASURER)), group.president(),
                    Map.of("role", "MEMBER")).expect(409, "OFFICE_TRANSFER_REQUIRED");
            api().patch(group.path("/members/" + group.memberId(GroupRole.TREASURER)), group.president(),
                    Map.of("status", "SUSPENDED")).expect(409, "OFFICE_TRANSFER_REQUIRED");
        }

        @Test
        void removingAMemberNeedsAReasonAndARecentPassword() {
            String memberPath = group.path("/members/" + group.memberId(GroupRole.MEMBER));
            clock.advance(Duration.ofMinutes(6));
            User president = signIn(group.president());           // token valid, but password entered > 5 min ago
            clock.advance(Duration.ofMinutes(6));
            president = president.withAccessToken(api().refresh(president.refreshToken(), null).expect(200).text("accessToken"));

            api().patch(memberPath, president, Map.of("status", "REMOVED", "reason", "left the village"))
                    .expect(403, "REAUTHENTICATION_REQUIRED");
            User steppedUp = api().reauthenticate(president);
            api().patch(memberPath, steppedUp, Map.of("status", "REMOVED")).expect(400, "VALIDATION_FAILED");
            Response removed = api().patch(memberPath, steppedUp, Map.of("status", "REMOVED", "reason", "left the village"))
                    .expect(200);
            assertThat(removed.text("status")).isEqualTo("REMOVED");
            // A removed member is no longer let in at all.
            api().get(group.path(""), group.as(GroupRole.MEMBER)).expect(404, "NOT_FOUND");
        }

        @Test
        void ordinaryMembersCannotManageMembers() {
            api().patch(group.path("/members/" + group.memberId(GroupRole.AUDITOR)), group.as(GroupRole.TREASURER),
                    Map.of("status", "SUSPENDED")).expect(403, "FORBIDDEN");
        }
    }

    @Nested
    class Bylaws {

        private TestGroup group;

        @BeforeEach
        void group() {
            group = groups.createWithEveryRole("Bylaws");
        }

        private Map<String, Object> settings(String locale, String interest, String exitFee) {
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("defaultLocale", locale);
            s.put("interestRecognition", interest);
            s.put("withdrawalsAllowed", false);
            s.put("withdrawalNoticeDays", 0);
            s.put("exitFee", exitFee);
            return s;
        }

        private long version(User as) {
            return api().get(group.path("/settings"), as).expect(200).body().get("version").asLong();
        }

        @Test
        void nonFinancialChangesApplyImmediately() {
            User secretary = group.as(GroupRole.SECRETARY);
            Response applied = api().put(group.path("/settings"), secretary,
                    Map.of("version", version(secretary), "settings", settings("en", "WHEN_PAID", "0.00"))).expect(200);
            assertThat(applied.body().get("settings").get("defaultLocale").asString()).isEqualTo("en");
        }

        @Test
        void financialChangesNeedASecondOfficer() {
            User treasurer = group.as(GroupRole.TREASURER);
            Response proposed = api().put(group.path("/settings"), treasurer,
                    Map.of("version", version(treasurer), "settings", settings("rw", "WHEN_DUE", "5000.00"))).expect(202);
            JsonNode pending = proposed.body().get("pendingChange");
            assertThat(pending.get("proposed").get("exitFee").asString()).isEqualTo("5000.00");
            assertThat(proposed.body().get("settings").get("exitFee").asString()).as("not applied yet").isEqualTo("0.00");
            String changeId = pending.get("changeId").asString();

            // Maker-checker (H9): the proposer cannot confirm their own change.
            api().post(group.path("/settings/changes/" + changeId + "/confirm"), treasurer, null)
                    .expect(403, "SELF_APPROVAL_FORBIDDEN");
            // Members without SETTINGS_EDIT cannot either.
            api().post(group.path("/settings/changes/" + changeId + "/confirm"), group.as(GroupRole.AUDITOR), null)
                    .expect(403, "FORBIDDEN");

            Response confirmed = api().post(group.path("/settings/changes/" + changeId + "/confirm"), group.president(), null)
                    .expect(200);
            assertThat(confirmed.body().get("settings").get("exitFee").asString()).isEqualTo("5000.00");
            assertThat(confirmed.body().get("settings").get("interestRecognition").asString()).isEqualTo("WHEN_DUE");
            assertThat(confirmed.body().get("pendingChange").isNull()).isTrue();
        }

        @Test
        void aProposalOverTakenByAnotherChangeCannotBeConfirmed() {
            User treasurer = group.as(GroupRole.TREASURER);
            String changeId = api().put(group.path("/settings"), treasurer,
                            Map.of("version", version(treasurer), "settings", settings("rw", "WHEN_DUE", "0.00")))
                    .expect(202).body().get("pendingChange").get("changeId").asString();
            User secretary = group.as(GroupRole.SECRETARY);
            api().put(group.path("/settings"), secretary,
                    Map.of("version", version(secretary), "settings", settings("en", "WHEN_PAID", "0.00"))).expect(200);

            api().post(group.path("/settings/changes/" + changeId + "/confirm"), group.president(), null)
                    .expect(409, "SETTINGS_CHANGE_STALE");
            // The stale proposal is closed, not left dangling.
            assertThat(api().get(group.path("/settings"), group.president()).body().get("pendingChange").isNull()).isTrue();
        }

        @Test
        void aStaleVersionIsRefusedAndNegativeFeesRejected() {
            User secretary = group.as(GroupRole.SECRETARY);
            api().put(group.path("/settings"), secretary, Map.of("version", 999, "settings", settings("en", "WHEN_PAID", "0.00")))
                    .expect(409, "CONCURRENT_MODIFICATION");
            api().put(group.path("/settings"), secretary,
                    Map.of("version", version(secretary), "settings", settings("rw", "WHEN_PAID", "-1.00")))
                    .expect(400, "VALIDATION_FAILED");
        }

        @Test
        void financialProposalsNeedARecentPassword() {
            clock.advance(Duration.ofMinutes(6));
            User treasurer = signIn(group.as(GroupRole.TREASURER));
            clock.advance(Duration.ofMinutes(6));
            treasurer = treasurer.withAccessToken(api().refresh(treasurer.refreshToken(), null).expect(200).text("accessToken"));
            api().put(group.path("/settings"), treasurer,
                    Map.of("version", version(treasurer), "settings", settings("rw", "WHEN_DUE", "0.00")))
                    .expect(403, "REAUTHENTICATION_REQUIRED");
        }
    }

    @Nested
    class TransferOfOffice {

        private TestGroup group;

        @BeforeEach
        void group() {
            group = groups.createWithEveryRole("Offices");
        }

        @Test
        void theHolderOffersAndTheRecipientAccepts() {
            String transferId = api().post(group.path("/offices/transfer"), group.president(),
                            Map.of("role", "PRESIDENT", "toMemberId", group.memberId(GroupRole.MEMBER)))
                    .expect(201).text("transferId");
            // Nothing changes until the recipient accepts.
            assertThat(api().get(group.path(""), group.president()).text("myRole")).isEqualTo("PRESIDENT");

            api().post(group.path("/offices/transfers/" + transferId + "/accept"), group.president(), null).expect(403, "FORBIDDEN");
            api().post(group.path("/offices/transfers/" + transferId + "/accept"), group.as(GroupRole.MEMBER), null).expect(200);

            assertThat(api().get(group.path(""), group.as(GroupRole.MEMBER)).text("myRole")).isEqualTo("PRESIDENT");
            assertThat(api().get(group.path(""), group.president()).text("myRole")).isEqualTo("MEMBER");
        }

        @Test
        void onlyTheHolderCanOfferAnOfficeAndNotToAnotherOfficer() {
            api().post(group.path("/offices/transfer"), group.as(GroupRole.SECRETARY),
                    Map.of("role", "PRESIDENT", "toMemberId", group.memberId(GroupRole.MEMBER))).expect(403, "FORBIDDEN");
            api().post(group.path("/offices/transfer"), group.president(),
                    Map.of("role", "PRESIDENT", "toMemberId", group.memberId(GroupRole.TREASURER))).expect(409, "OFFICE_OCCUPIED");
        }

        @Test
        void theRecipientCanDeclineAndTheHolderCancel() {
            String first = api().post(group.path("/offices/transfer"), group.president(),
                    Map.of("role", "PRESIDENT", "toMemberId", group.memberId(GroupRole.MEMBER))).expect(201).text("transferId");
            api().post(group.path("/offices/transfers/" + first + "/decline"), group.as(GroupRole.MEMBER), null).expect(200);

            String second = api().post(group.path("/offices/transfer"), group.president(),
                    Map.of("role", "PRESIDENT", "toMemberId", group.memberId(GroupRole.AUDITOR))).expect(201).text("transferId");
            api().post(group.path("/offices/transfers/" + second + "/cancel"), group.president(), null).expect(200);
            api().post(group.path("/offices/transfers/" + second + "/accept"), group.as(GroupRole.AUDITOR), null)
                    .expect(409, "CONCURRENT_MODIFICATION");
            List<?> pending = json.convertValue(api().get(group.path("/offices/transfers"), group.president()).body(), List.class);
            assertThat(pending).isEmpty();
        }
    }

    private User signIn(User user) {
        Response login = api().login(user.phone(), user.password(), null).expect(200);
        return user.withAccessToken(login.text("accessToken")).withRefreshToken(login.cookie(Api.REFRESH_COOKIE).getValue());
    }
}
