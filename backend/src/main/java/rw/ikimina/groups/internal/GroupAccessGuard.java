package rw.ikimina.groups.internal;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;
import rw.ikimina.groups.GroupAccess;
import rw.ikimina.shared.error.ApiException;
import rw.ikimina.shared.error.ErrorCode;
import rw.ikimina.shared.security.AuthenticatedUser;
import rw.ikimina.shared.security.CurrentUser;
import rw.ikimina.shared.tenancy.TenantContext;

/**
 * Spec 5.2 step 3: for every route with a {@code {groupId}}, resolves (user, group) to an
 * active membership and its role, and puts it in {@link TenantContext}. Anyone who is not
 * an active member gets 404 - not 403 - so the response never confirms that a group exists.
 * That includes platform administrators: they have no routine access to group data (spec 2.1).
 */
@Component
class GroupAccessGuard implements HandlerInterceptor {

    static final String GROUP_ID = "groupId";

    private static final String MEMBERSHIP = """
            SELECT g.id AS group_id, m.id AS membership_id, m.role
            FROM groups g
            JOIN group_memberships m ON m.group_id = g.id
            WHERE g.public_id = ? AND m.user_id = ? AND m.status = 'ACTIVE'""";

    private record Access(long groupId, long membershipId, String role) {
    }

    private final JdbcTemplate jdbc;
    private final TransactionTemplate readOnly;

    GroupAccessGuard(JdbcTemplate jdbc, PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.readOnly = new TransactionTemplate(transactionManager);
        this.readOnly.setReadOnly(true);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod method)) {
            return true;
        }
        @SuppressWarnings("unchecked")
        Map<String, String> variables = (Map<String, String>) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (variables == null || !variables.containsKey(GROUP_ID)) {
            return true;
        }
        UUID groupPublicId = parse(variables.get(GROUP_ID));
        AuthenticatedUser user = CurrentUser.require();

        List<Access> found = readOnly.execute(status -> jdbc.query(MEMBERSHIP,
                (rs, n) -> new Access(rs.getLong("group_id"), rs.getLong("membership_id"), rs.getString("role")),
                groupPublicId, user.id()));
        if (found != null && !found.isEmpty()) {
            Access access = found.getFirst();
            TenantContext.setGroup(new TenantContext.GroupScope(access.groupId(), groupPublicId, access.membershipId(), access.role()));
            MDC.put("groupId", groupPublicId.toString());
            return true;
        }
        if (method.hasMethodAnnotation(GroupAccess.NonMemberAllowed.class)) {
            return true;
        }
        throw new ApiException(ErrorCode.NOT_FOUND);
    }

    private static UUID parse(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }
    }
}
