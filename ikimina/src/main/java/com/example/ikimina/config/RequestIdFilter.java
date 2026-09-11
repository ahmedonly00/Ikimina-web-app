package com.example.ikimina.config;

import com.example.ikimina.security.SecurityUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Puts a correlation id on every request and into the logging MDC.
 *
 * Without this, a report of "my contribution went missing" cannot be traced
 * through the logs to the request that caused it. An inbound X-Request-Id is
 * honoured so a reverse proxy or mobile client can supply its own, which is
 * what lets a single member action be followed end to end.
 *
 * Runs first so that even rejected requests are correlated.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    private static final String MDC_REQUEST_ID = "requestId";
    private static final String MDC_USER_ID = "userId";
    private static final int MAX_LENGTH = 64;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String requestId = sanitise(request.getHeader(HEADER));
        if (requestId == null) {
            requestId = UUID.randomUUID().toString();
        }

        MDC.put(MDC_REQUEST_ID, requestId);
        response.setHeader(HEADER, requestId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            // The security context is populated downstream, so the user id is
            // only known on the way out - still useful for the access log.
            SecurityUtils.currentPrincipal()
                    .map(p -> p.getUserId())
                    .ifPresent(id -> MDC.put(MDC_USER_ID, String.valueOf(id)));
            MDC.remove(MDC_REQUEST_ID);
            MDC.remove(MDC_USER_ID);
        }
    }

    /**
     * A client-supplied id is untrusted input that ends up in log files, so it
     * is length-capped and restricted to characters that cannot forge a new log
     * record or break a JSON field.
     */
    private String sanitise(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > MAX_LENGTH) {
            trimmed = trimmed.substring(0, MAX_LENGTH);
        }
        return trimmed.matches("[A-Za-z0-9._:-]+") ? trimmed : null;
    }
}