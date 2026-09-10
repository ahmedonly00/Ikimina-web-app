package com.example.ikimina.audit;

import com.example.ikimina.model.AuditLog;
import com.example.ikimina.repository.AuditLogRepository;
import com.example.ikimina.security.CustomUserDetails;
import com.example.ikimina.security.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;

/**
 * Writes the audit trail.
 *
 * The AuditLog entity and its repository existed and were read by the
 * super-admin endpoint, but nothing ever wrote to them - the trail was
 * permanently empty. For a financial product the audit trail is a regulatory
 * artifact, not a diagnostic nicety.
 *
 * Records are written in their own transaction (REQUIRES_NEW) so that rolling
 * back the business operation does not also erase the evidence that it was
 * attempted, and failures to audit never fail the operation itself.
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String module,
                       String entityType,
                       Long entityId,
                       String action,
                       String oldValue,
                       String newValue,
                       String description) {
        try {
            AuditLog entry = new AuditLog();
            entry.setModule(module);
            entry.setEntityType(entityType);
            entry.setEntityId(entityId);
            entry.setAction(action);
            entry.setOldValue(oldValue);
            entry.setNewValue(newValue);
            entry.setDescription(description);
            entry.setCreatedAt(LocalDateTime.now());

            CustomUserDetails principal = SecurityUtils.currentPrincipal().orElse(null);
            entry.setPerformedBy(principal != null && principal.getUserId() != null
                    ? principal.getUserId() : 0L);
            entry.setPerformedByName(principal != null ? principal.getUsername() : "system");
            entry.setPerformedByRole(principal != null
                    ? principal.getAuthorities().stream().findFirst()
                        .map(a -> a.getAuthority()).orElse("UNKNOWN")
                    : "SYSTEM");

            HttpServletRequest request = currentRequest();
            if (request != null) {
                entry.setIpAddress(clientIp(request));
                entry.setUserAgent(truncate(request.getHeader("User-Agent"), 1000));
            }

            auditLogRepository.save(entry);
        } catch (Exception ex) {
            // Never let auditing break the operation being audited, but make the
            // gap loud - a silently missing trail is worse than a noisy log.
            log.error("Failed to write audit record for " + entityType + " " + entityId
                    + " action=" + action, ex);
        }
    }

    /** Convenience for the common "something changed" case. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordChange(AuditLog.Module module, String entityType, Long entityId,
                             String action, String description) {
        record(module.name(), entityType, entityId, action, null, null, description);
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return attrs.getRequest();
        }
        return null;
    }

    /**
     * Honours X-Forwarded-For so the recorded address is the client's rather
     * than the reverse proxy's. Only the first hop is taken; the rest of the
     * chain is attacker-controllable.
     */
    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return truncate(forwarded.split(",")[0].trim(), 45);
        }
        return truncate(request.getRemoteAddr(), 45);
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
