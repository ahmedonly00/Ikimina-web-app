package rw.ikimina.audit;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import rw.ikimina.shared.security.AuthenticatedUser;
import rw.ikimina.shared.security.CurrentUser;
import rw.ikimina.shared.tenancy.TenantContext;
import rw.ikimina.shared.web.RequestContext;
import tools.jackson.databind.json.JsonMapper;

/**
 * Writes audit rows (Hard Rule H8) - in the caller's transaction, never its own: the audit
 * row and the change it describes commit or roll back together. Calling this outside a
 * transaction is a programming error and fails.
 *
 * <p>The group comes from {@link TenantContext} (none = platform event); hashing and
 * chaining happen in the database (V4), so callers cannot get them wrong.
 */
@Service
public class AuditService {

    private static final String INSERT = """
            INSERT INTO audit_logs (group_id, actor_user_id, actor_role, action, entity_type, entity_id,
                                    before_state, after_state, reason, ip_address, user_agent, request_id)
            VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?::inet, ?, ?)""";

    private final JdbcTemplate jdbc;
    private final JsonMapper json;

    public AuditService(JdbcTemplate jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(AuditEvent event) {
        TenantContext.GroupScope group = TenantContext.currentGroup().orElse(null);
        AuthenticatedUser caller = CurrentUser.optional().orElse(null);
        Long actor = event.actorUserId() != null ? event.actorUserId() : caller == null ? null : caller.id();
        String actorRole = group != null && group.role() != null ? group.role()
                : caller == null ? null : caller.platformRole();
        RequestContext request = RequestContext.current().orElse(null);

        jdbc.update(INSERT,
                group == null ? null : group.groupId(),
                actor,
                actorRole,
                event.action(),
                event.entityType(),
                event.entityId(),
                toJson(event.before()),
                toJson(event.after()),
                event.reason(),
                request == null ? null : request.clientIp(),
                request == null ? null : request.userAgent(),
                request == null ? null : request.requestId());
    }

    private String toJson(Object state) {
        return state == null ? null : json.writeValueAsString(state);
    }
}
