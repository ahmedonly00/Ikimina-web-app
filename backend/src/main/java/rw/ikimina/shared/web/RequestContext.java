package rw.ikimina.shared.web;

import java.util.Optional;

/**
 * Who is calling from where, for the audit trail (spec 6.9: ip_address, user_agent,
 * request_id). Populated by {@link RequestIdFilter} for the duration of a request;
 * empty on scheduler threads.
 */
public record RequestContext(String requestId, String clientIp, String userAgent) {

    private static final int MAX_USER_AGENT = 500;
    private static final ThreadLocal<RequestContext> CURRENT = new ThreadLocal<>();

    public RequestContext {
        if (userAgent != null && userAgent.length() > MAX_USER_AGENT) {
            userAgent = userAgent.substring(0, MAX_USER_AGENT);
        }
    }

    public static Optional<RequestContext> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    static void set(RequestContext context) {
        CURRENT.set(context);
    }

    static void clear() {
        CURRENT.remove();
    }
}
