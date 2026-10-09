package rw.ikimina.shared.security;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;
import rw.ikimina.shared.tenancy.TenantContext;

/**
 * After authentication, records the caller in {@link TenantContext} (so every transaction
 * tells PostgreSQL who is acting) and in the logging MDC. Clears both when the request ends,
 * so a pooled thread never carries one caller into the next request.
 */
class TenantUserFilter extends OncePerRequestFilter {

    private static final String MDC_USER_ID = "userId";
    private static final String MDC_GROUP_ID = "groupId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            CurrentUser.optional().ifPresent(user -> {
                TenantContext.setUser(user.id());
                MDC.put(MDC_USER_ID, user.publicId().toString());
            });
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
            MDC.remove(MDC_USER_ID);
            MDC.remove(MDC_GROUP_ID);
        }
    }
}
