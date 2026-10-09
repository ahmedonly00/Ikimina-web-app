package rw.ikimina.audit;

/**
 * One state change to record (spec 16.6). Built fluently:
 * {@code AuditEvent.of("MEMBER_ROLE_CHANGED").entity("membership", id).before(old).after(new)}.
 *
 * <p>{@code before}/{@code after} are serialised to JSON; pass small records or maps of the
 * fields that changed. Never put secrets, tokens, codes or full national ids in them.
 *
 * @param actorUserId overrides the authenticated caller - for events where nobody is signed
 *                    in yet (registration) or the actor is known only by the service
 */
public record AuditEvent(String action, String entityType, String entityId, Object before, Object after,
                         String reason, Long actorUserId) {

    public static AuditEvent of(String action) {
        return new AuditEvent(action, null, null, null, null, null, null);
    }

    public AuditEvent entity(String type, Object id) {
        return new AuditEvent(action, type, id == null ? null : id.toString(), before, after, reason, actorUserId);
    }

    public AuditEvent before(Object state) {
        return new AuditEvent(action, entityType, entityId, state, after, reason, actorUserId);
    }

    public AuditEvent after(Object state) {
        return new AuditEvent(action, entityType, entityId, before, state, reason, actorUserId);
    }

    public AuditEvent reason(String text) {
        return new AuditEvent(action, entityType, entityId, before, after, text, actorUserId);
    }

    public AuditEvent actor(Long userId) {
        return new AuditEvent(action, entityType, entityId, before, after, reason, userId);
    }
}
