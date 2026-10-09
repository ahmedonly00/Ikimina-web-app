package rw.ikimina.groups;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import rw.ikimina.support.Api.Response;
import rw.ikimina.support.GroupFixture;
import rw.ikimina.support.GroupFixture.TestGroup;
import rw.ikimina.support.GroupRoutes;
import rw.ikimina.support.IntegrationTest;

/**
 * Phase 1 acceptance: the role matrix holds over HTTP, judged against spec 5.5 itself. For every permission-guarded group
 * route and every role, a member of that very group is refused (403 FORBIDDEN) exactly when
 * spec 5.5 withholds the permission. Also checks that each route is guarded by the
 * permission the spec implies, so an annotation cannot silently drift.
 *
 * <p>Calls use random resource ids or deliberately conflicting input, so a permitted call
 * reaches the service and stops at 404/409 without changing anything.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RoleMatrixIT extends IntegrationTest {

    private static final Pattern PERMISSION = Pattern.compile("'([A-Z_]+)'");

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping mappings;

    private TestGroup group;

    @BeforeAll
    void groupWithEveryRole() {
        group = new GroupFixture(api(), sms).createWithEveryRole("Role Matrix");
    }

    @Test
    void eachRouteIsGuardedByThePermissionTheSpecImplies() {
        for (GroupRoutes route : GroupRoutes.ALL) {
            assertThat(annotatedPermissions(route)).as(route.key()).isEqualTo(route.permission());
        }
    }

    @Test
    void everyRoleIsAllowedOrRefusedExactlyAsTheMatrixSays() {
        List<String> mismatches = new ArrayList<>();
        Map<String, String> ids = Map.of(
                "groupId", group.groupId(),
                "memberId", UUID.randomUUID().toString(),
                "invitationId", UUID.randomUUID().toString(),
                "transferId", UUID.randomUUID().toString(),
                "changeId", UUID.randomUUID().toString());
        for (GroupRoutes route : GroupRoutes.ALL) {
            if (route.permission().isEmpty()) {
                continue;   // membership + object rules; covered in GroupFlowIT
            }
            for (GroupRole role : GroupRole.values()) {
                // Expected outcome from the spec's own table, not from the code under test.
                boolean expected = route.permission().stream().anyMatch(p -> SpecPermissionMatrix.allows(role, p));
                Response response = api().call(route.method(), route.path(ids), group.as(role).accessToken(),
                        route.body(ids), group.as(role).ip());
                boolean refused = response.status() == 403 && "FORBIDDEN".equals(response.code());
                if (refused == expected) {
                    mismatches.add("%s as %s: expected %s, got %d %s".formatted(route.key(), role,
                            expected ? "allowed" : "refused", response.status(), response.code()));
                }
            }
        }
        assertThat(mismatches).isEmpty();
    }

    private List<String> annotatedPermissions(GroupRoutes route) {
        List<String> found = new ArrayList<>();
        mappings.getHandlerMethods().forEach((info, handler) -> {
            boolean matches = info.getPatternValues().contains(route.template())
                    && info.getMethodsCondition().getMethods().stream().anyMatch(m -> m.name().equals(route.method().name()));
            PreAuthorize guard = handler.getMethodAnnotation(PreAuthorize.class);
            if (matches && guard != null) {
                Matcher matcher = PERMISSION.matcher(guard.value());
                while (matcher.find()) {
                    found.add(matcher.group(1));
                }
            }
        });
        return found;
    }
}
