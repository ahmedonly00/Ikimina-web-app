package com.example.ikimina.filter;

import java.io.IOException;
import java.time.LocalDate;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.example.ikimina.model.GroupSubscription;
import com.example.ikimina.model.GroupSubscription.SubscriptionStatus;
import com.example.ikimina.model.SavingsGroup;
import com.example.ikimina.repository.GroupSubscriptionRepository;
import com.example.ikimina.repository.SavingsGroupRepository;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class SubscriptionValidationFilter extends OncePerRequestFilter {
    
    @Autowired
    private GroupSubscriptionRepository subscriptionRepository;
    
    @Autowired
    private SavingsGroupRepository groupRepository;
    
    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, 
                                  @NonNull HttpServletResponse response, 
                                  @NonNull FilterChain filterChain) throws ServletException, IOException {
        
        String path = request.getRequestURI();
        String method = request.getMethod();
        
        // Skip validation for certain paths
        if (path.startsWith("/api/auth") || 
            path.startsWith("/api/subscription/status") ||
            path.startsWith("/api/public") ||
            method.equals("GET")) {
            filterChain.doFilter(request, response);
            return;
        }
        
        // Extract group ID from path or request
        Long groupId = extractGroupId(request);
        
        if (groupId != null) {
            try {
                // Check if group exists and is active
                SavingsGroup group = groupRepository.findById(groupId).orElse(null);
                if (group == null || !group.getIsActive()) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.getWriter().write("{\"error\": \"Group is not active\"}");
                    return;
                }
                
                // Check subscription status
                GroupSubscription subscription = subscriptionRepository.findByGroup(group).orElse(null);
                
                if (subscription == null) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.getWriter().write("{\"error\": \"No active subscription found\"}");
                    return;
                }
                
                // Block operations for suspended groups
                if (subscription.getStatus() == SubscriptionStatus.SUSPENDED) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.getWriter().write("{\"error\": \"Group subscription is suspended. Please contact support.\"}");
                    return;
                }
                
                // Check grace period warnings for write operations
                if (isWriteOperation(path, method) && 
                    subscription.getStatus() == SubscriptionStatus.GRACE_PERIOD) {
                    LocalDate today = LocalDate.now();
                    if (subscription.getGracePeriodEnd() != null && today.isAfter(subscription.getGracePeriodEnd())) {
                        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                        response.getWriter().write("{\"error\": \"Grace period expired. Subscription suspended.\"}");
                        return;
                    }
                    
                    // Add warning header for grace period
                    response.setHeader("X-Subscription-Warning", "Grace period active");
                }
                
            } catch (Exception e) {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                response.getWriter().write("{\"error\": \"Error validating subscription\"}");
                return;
            }
        }
        
        filterChain.doFilter(request, response);
    }
    
    private Long extractGroupId(HttpServletRequest request) {
        String path = request.getRequestURI();
        
        // Extract from URL path patterns
        if (path.contains("/groups/")) {
            String[] parts = path.split("/");
            for (int i = 0; i < parts.length; i++) {
                if ("groups".equals(parts[i]) && i + 1 < parts.length) {
                    try {
                        return Long.parseLong(parts[i + 1]);
                    } catch (NumberFormatException e) {
                        // Continue
                    }
                }
            }
        }
        
        // Extract from query parameter
        String groupIdParam = request.getParameter("groupId");
        if (groupIdParam != null) {
            try {
                return Long.parseLong(groupIdParam);
            } catch (NumberFormatException e) {
                // Invalid format
            }
        }
        
        // Extract from request body for POST/PUT requests
        // This would require more complex parsing depending on your API structure
        
        return null;
    }
    
    private boolean isWriteOperation(String path, String method) {
        return "POST".equals(method) || "PUT".equals(method) || "DELETE".equals(method);
    }
}
