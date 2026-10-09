package rw.ikimina.shared.web;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Puts a correlation id on every request, into the logging MDC and onto the
 * response, so "my contribution went missing" can be traced to one request.
 *
 * <p>An inbound {@code X-Request-Id} is honoured so a proxy or client can supply
 * its own. It is untrusted input that ends up in logs, so it is length-capped and
 * restricted to characters that cannot forge a log record or break a JSON field.
 * Runs first so even rejected requests are correlated.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_REQUEST_ID = "requestId";

    private static final int MAX_LENGTH = 64;
    private static final Pattern SAFE = Pattern.compile("[A-Za-z0-9._:-]{1," + MAX_LENGTH + "}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = sanitise(request.getHeader(HEADER));
        MDC.put(MDC_REQUEST_ID, requestId);
        response.setHeader(HEADER, requestId);
        // getRemoteAddr honours X-Forwarded-For only when a trusted proxy strategy is configured (prod profile).
        RequestContext.set(new RequestContext(requestId, request.getRemoteAddr(), request.getHeader("User-Agent")));
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_REQUEST_ID);
            RequestContext.clear();
        }
    }

    static String sanitise(String candidate) {
        if (candidate != null) {
            String trimmed = candidate.trim();
            if (SAFE.matcher(trimmed).matches()) {
                return trimmed;
            }
        }
        return UUID.randomUUID().toString();
    }
}
