package rw.ikimina.groups;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import rw.ikimina.support.GroupRoutes;
import rw.ikimina.support.IntegrationTest;

/**
 * Spec 5.6: "scan registered routes and fail CI if a route lacks an isolation test".
 * Reads every route the running application actually serves under a group, and fails if
 * any is missing from {@link GroupRoutes} - the catalogue the isolation and role-matrix
 * tests iterate - or carries no explicit access declaration.
 */
class GroupRouteCoverageIT extends IntegrationTest {

    private static final String GROUP_PREFIX = "/api/v1/groups/{groupId}";

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping mappings;

    @Test
    void everyGroupRouteIsInTheIsolationCatalogue() {
        Set<String> served = new TreeSet<>();
        mappings.getHandlerMethods().forEach((info, handler) -> {
            for (String pattern : info.getPatternValues()) {
                if (pattern.startsWith(GROUP_PREFIX)) {
                    info.getMethodsCondition().getMethods().forEach(method -> served.add(method.name() + " " + pattern));
                }
            }
        });
        Set<String> catalogued = new TreeSet<>();
        GroupRoutes.ALL.forEach(route -> catalogued.add(route.key()));

        assertThat(served).as("group routes served by the application").isNotEmpty();
        assertThat(catalogued).as("routes in GroupRoutes - add new endpoints there").containsAll(served);
        assertThat(served).as("catalogued routes that no longer exist").containsAll(catalogued);
    }

    @Test
    void everyGroupRouteDeclaresWhoMayCallIt() {
        mappings.getHandlerMethods().forEach((RequestMappingInfo info, HandlerMethod handler) -> {
            boolean groupRoute = info.getPatternValues().stream().anyMatch(p -> p.startsWith(GROUP_PREFIX));
            if (groupRoute) {
                boolean declared = handler.hasMethodAnnotation(PreAuthorize.class)
                        || handler.hasMethodAnnotation(GroupAccess.AnyMember.class)
                        || handler.hasMethodAnnotation(GroupAccess.NonMemberAllowed.class);
                assertThat(declared).as("%s declares @PreAuthorize, @AnyMember or @NonMemberAllowed", handler).isTrue();
            }
        });
    }
}
