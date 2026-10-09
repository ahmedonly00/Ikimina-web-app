package rw.ikimina.support;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpMethod;

/**
 * Every endpoint under {@code /api/v1/groups/{groupId}}. {@code GroupRouteCoverageIT} fails
 * when the application has a group route that is missing here, so a new endpoint cannot
 * skip the tenant-isolation and role-matrix tests (spec 5.6).
 *
 * <p>Placeholders: {groupId}, {memberId}, {invitationId}, {transferId}, {changeId}.
 *
 * @param permission the matrix permission(s) the spec implies for the route; empty when access
 *                   is decided by membership plus an object rule (own record, office holder, invitee)
 * @param body       a request body that passes validation, so a denial cannot hide behind a 400
 */
public record GroupRoutes(HttpMethod method, String template, List<String> permission, Object body) {

    public static final List<GroupRoutes> ALL = List.of(
            route(HttpMethod.GET, "/api/v1/groups/{groupId}", List.of(), null),
            route(HttpMethod.PATCH, "/api/v1/groups/{groupId}", List.of("SETTINGS_EDIT"), Map.of("sector", "Kimironko")),

            route(HttpMethod.GET, "/api/v1/groups/{groupId}/settings", List.of(), null),
            route(HttpMethod.PUT, "/api/v1/groups/{groupId}/settings", List.of("SETTINGS_EDIT"),
                    Map.of("version", 999_999, "settings", Map.of("defaultLocale", "rw", "interestRecognition", "WHEN_PAID",
                            "withdrawalsAllowed", false, "withdrawalNoticeDays", 0, "exitFee", "0.00"))),
            route(HttpMethod.POST, "/api/v1/groups/{groupId}/settings/changes/{changeId}/confirm", List.of("SETTINGS_EDIT"), null),
            route(HttpMethod.POST, "/api/v1/groups/{groupId}/settings/changes/{changeId}/reject", List.of("SETTINGS_EDIT"),
                    Map.of("reason", "not agreed")),

            route(HttpMethod.GET, "/api/v1/groups/{groupId}/members", List.of("MEMBER_MANAGE", "REPORT_VIEW_GROUP"), null),
            route(HttpMethod.GET, "/api/v1/groups/{groupId}/members/{memberId}", List.of(), null),
            route(HttpMethod.PATCH, "/api/v1/groups/{groupId}/members/{memberId}", List.of("MEMBER_MANAGE"),
                    Map.of("status", "SUSPENDED")),

            route(HttpMethod.POST, "/api/v1/groups/{groupId}/invitations", List.of("MEMBER_MANAGE"),
                    Map.of("phone", "+250789999999", "role", "MEMBER")),
            route(HttpMethod.GET, "/api/v1/groups/{groupId}/invitations", List.of("MEMBER_MANAGE"), null),
            route(HttpMethod.POST, "/api/v1/groups/{groupId}/invitations/{invitationId}/revoke", List.of("MEMBER_MANAGE"), null),
            route(HttpMethod.POST, "/api/v1/groups/{groupId}/invitations/accept", List.of(), Map.of("token", "x".repeat(43))),

            route(HttpMethod.POST, "/api/v1/groups/{groupId}/offices/transfer", List.of(),
                    Map.of("role", "PRESIDENT", "toMemberId", "{memberId}")),
            route(HttpMethod.GET, "/api/v1/groups/{groupId}/offices/transfers", List.of(), null),
            route(HttpMethod.POST, "/api/v1/groups/{groupId}/offices/transfers/{transferId}/accept", List.of(), null),
            route(HttpMethod.POST, "/api/v1/groups/{groupId}/offices/transfers/{transferId}/decline", List.of(), null),
            route(HttpMethod.POST, "/api/v1/groups/{groupId}/offices/transfers/{transferId}/cancel", List.of(), null));

    private static GroupRoutes route(HttpMethod method, String template, List<String> permission, Object body) {
        return new GroupRoutes(method, template, permission, body);
    }

    public String key() {
        return method.name() + " " + template;
    }

    /** The route with every placeholder replaced, in the path and in string body values. */
    public String path(Map<String, String> values) {
        return fill(template, values);
    }

    public Object body(Map<String, String> values) {
        if (body instanceof Map<?, ?> map) {
            java.util.Map<Object, Object> filled = new java.util.LinkedHashMap<>();
            map.forEach((k, v) -> filled.put(k, v instanceof String s ? fill(s, values) : v));
            return filled;
        }
        return body;
    }

    private static String fill(String text, Map<String, String> values) {
        String result = text;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }
}
