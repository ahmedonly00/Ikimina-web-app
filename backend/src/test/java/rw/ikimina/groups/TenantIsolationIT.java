package rw.ikimina.groups;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import rw.ikimina.support.Api;
import rw.ikimina.support.Api.Response;
import rw.ikimina.support.Api.User;
import rw.ikimina.support.GroupFixture;
import rw.ikimina.support.GroupFixture.Invite;
import rw.ikimina.support.GroupFixture.TestGroup;
import rw.ikimina.support.GroupRoutes;
import rw.ikimina.support.IntegrationTest;
import rw.ikimina.support.PostgresTestDatabase;
import tools.jackson.databind.JsonNode;

/**
 * Spec 5.6, acceptance criterion of Phase 1: for every group endpoint, a member of group A -
 * in every role - tries to read or modify group B's resources, both through B's path and
 * through A's own path carrying B's resource ids. Every attempt must fail with 404 (or 403
 * where A's own permission check comes first), leak nothing of B, and change nothing in B.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TenantIsolationIT extends IntegrationTest {

    private TestGroup alpha;
    private TestGroup bravo;
    private Map<String, String> bravoIds;
    private Invite bravoInvite;
    private long bravoSettingsVersion;

    @BeforeAll
    void twoGroups() {
        GroupFixture groups = new GroupFixture(api(), sms);
        alpha = groups.createWithEveryRole("Alpha Isolation");
        bravo = groups.createWithEveryRole("Bravo Isolation");

        // Give B one of every kind of resource, each in an open state an attacker would like to touch.
        String invitee = Api.newPhone();
        bravoInvite = groups.invite(bravo, invitee, GroupRole.MEMBER);
        String invitationId = api().get(bravo.path("/invitations"), bravo.president()).expect(200)
                .body().get(0).get("invitationId").asString();
        String transferId = api().post(bravo.path("/offices/transfer"), bravo.president(),
                Map.of("role", "PRESIDENT", "toMemberId", bravo.memberId(GroupRole.MEMBER))).expect(201).text("transferId");
        JsonNode settings = api().get(bravo.path("/settings"), bravo.president()).body();
        Map<String, Object> financial = new java.util.LinkedHashMap<>(json.convertValue(settings.get("settings"), Map.class));
        financial.put("exitFee", "1000.00");
        String changeId = api().put(bravo.path("/settings"), bravo.as(GroupRole.TREASURER),
                        Map.of("version", settings.get("version").asLong(), "settings", financial))
                .expect(202).body().get("pendingChange").get("changeId").asString();
        bravoSettingsVersion = api().get(bravo.path("/settings"), bravo.president()).body().get("version").asLong();

        bravoIds = Map.of(
                "groupId", bravo.groupId(),
                "memberId", bravo.memberId(GroupRole.MEMBER),
                "invitationId", invitationId,
                "transferId", transferId,
                "changeId", changeId);
    }

    @Test
    void noMemberOfAnotherGroupCanReachAnyGroupRoute() {
        List<String> failures = new ArrayList<>();
        for (GroupRole attackerRole : GroupRole.values()) {
            User attacker = alpha.as(attackerRole);
            for (GroupRoutes route : GroupRoutes.ALL) {
                // 1. B's own path, B's ids.
                Response direct = call(route, attacker, bravoIds);
                checkRefused(route, attackerRole, "B's path", direct, Set.of(404), failures);

                // 2. A's path, smuggling B's resource ids.
                if (route.template().matches(".*\\{(memberId|invitationId|transferId|changeId)}.*")
                        || route.body() != null && route.body().toString().contains("{memberId}")) {
                    Map<String, String> smuggled = new java.util.HashMap<>(bravoIds);
                    smuggled.put("groupId", alpha.groupId());
                    Response crossed = call(route, attacker, smuggled);
                    checkRefused(route, attackerRole, "A's path with B's ids", crossed, Set.of(403, 404), failures);
                }
            }
        }
        assertThat(failures).as("isolation failures").isEmpty();
    }

    @Test
    void bravosRealInvitationTokenIsUselessToAnyoneElse() {
        Response response = api().post("/api/v1/groups/" + bravoInvite.groupId() + "/invitations/accept",
                alpha.president(), Map.of("token", bravoInvite.token()));
        response.expect(400, "INVITATION_INVALID");
        assertNoLeak(response, new ArrayList<>(), "accept with B's token");
    }

    @Test
    void platformAdministratorsHaveNoRoutineAccessToGroups() throws SQLException {
        User admin = api().register("Platform Staff");
        try (Connection owner = PostgresTestDatabase.connectAsOwner("app_it");
             PreparedStatement promote = owner.prepareStatement("UPDATE users SET platform_role = 'PLATFORM_ADMIN' WHERE phone = ?")) {
            promote.setString(1, admin.phone());
            assertThat(promote.executeUpdate()).isEqualTo(1);
        }
        User signedIn = admin.withAccessToken(api().login(admin.phone(), admin.password(), null).expect(200).text("accessToken"));
        api().get(bravo.path(""), signedIn).expect(404, "NOT_FOUND");
        api().get(bravo.path("/members"), signedIn).expect(404, "NOT_FOUND");
    }

    @Test
    void bravoIsUnchangedAfterTheAttempts() {
        noMemberOfAnotherGroupCanReachAnyGroupRoute();

        JsonNode settings = api().get(bravo.path("/settings"), bravo.president()).expect(200).body();
        assertThat(settings.get("version").asLong()).isEqualTo(bravoSettingsVersion);
        assertThat(settings.get("pendingChange").get("changeId").asString()).isEqualTo(bravoIds.get("changeId"));
        assertThat(api().get(bravo.path("/invitations"), bravo.president()).body()).hasSize(1);
        assertThat(api().get(bravo.path("/offices/transfers"), bravo.president()).body()).hasSize(1);
        JsonNode member = api().get(bravo.path("/members/" + bravoIds.get("memberId")), bravo.president()).body();
        assertThat(member.get("status").asString()).isEqualTo("ACTIVE");
        assertThat(api().get(bravo.path(""), bravo.president()).text("sector")).isNotEqualTo("Kimironko");
    }

    private Response call(GroupRoutes route, User as, Map<String, String> ids) {
        return api().call(route.method(), route.path(ids), as.accessToken(), route.body(ids), as.ip());
    }

    private void checkRefused(GroupRoutes route, GroupRole role, String how, Response response, Set<Integer> allowed,
                              List<String> failures) {
        boolean acceptRoute = route.template().endsWith("/invitations/accept");
        boolean refused = acceptRoute
                ? response.status() == 400 && "INVITATION_INVALID".equals(response.code())
                : allowed.contains(response.status());
        if (!refused) {
            failures.add("%s as A's %s via %s -> %d %s".formatted(route.key(), role, how, response.status(), response.code()));
        }
        assertNoLeak(response, failures, route.key() + " as " + role + " via " + how);
    }

    private void assertNoLeak(Response response, List<String> failures, String what) {
        // "instance" echoes the request path the attacker sent; that is their own input, not B's data.
        String body = response.body() == null ? ""
                : response.body().isObject() ? ((tools.jackson.databind.node.ObjectNode) response.body().deepCopy()).without("instance").toString()
                : response.body().toString();
        for (String secret : List.of(bravo.groupId(), "Bravo Isolation", bravoIds.get("memberId"), bravoIds.get("invitationId"),
                bravoIds.get("transferId"), bravoIds.get("changeId"), bravo.president().phone())) {
            if (body.contains(secret)) {
                failures.add(what + " leaked " + secret);
            }
        }
    }
}
