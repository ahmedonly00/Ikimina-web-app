package rw.ikimina.shared.security;

/** Header names shared between the security configuration and the endpoints that rely on them. */
public final class AuthHeaders {

    /**
     * Required on endpoints that act on the refresh-token cookie. A cross-site page cannot
     * add a custom header without a CORS preflight, which the allow-list refuses (spec 16.2).
     */
    public static final String CSRF_HEADER = "X-Ikimina-Csrf";

    private AuthHeaders() {
    }
}
