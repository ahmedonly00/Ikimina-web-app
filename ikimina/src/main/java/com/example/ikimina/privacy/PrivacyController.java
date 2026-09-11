package com.example.ikimina.privacy;

import com.example.ikimina.security.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Data-subject rights endpoints.
 *
 * Every route is scoped to the caller or gated on the same ownership check used
 * elsewhere: a privacy endpoint that let one member read another's data would
 * be worse than not having one.
 */
@RestController
@RequestMapping("/api/privacy")
public class PrivacyController {

    private final PersonalDataExportService exportService;
    private final DataSubjectRightsService rightsService;

    public PrivacyController(PersonalDataExportService exportService,
                             DataSubjectRightsService rightsService) {
        this.exportService = exportService;
        this.rightsService = rightsService;
    }

    /** Right of access: everything held about the caller. */
    @GetMapping("/me/export")
    public ResponseEntity<PersonalDataExport> exportMyData() {
        return ResponseEntity.ok(exportService.export(SecurityUtils.currentUserId()));
    }

    /**
     * A group admin may export a member's data on their behalf, since many
     * members will ask in person rather than through the app.
     */
    @GetMapping("/users/{userId}/export")
    @PreAuthorize("@userSecurity.hasAccessToUser(authentication, #userId)")
    public ResponseEntity<PersonalDataExport> exportUserData(@PathVariable Long userId) {
        return ResponseEntity.ok(exportService.export(userId));
    }

    @GetMapping("/me/consents")
    public ResponseEntity<Map<String, Boolean>> myConsents() {
        Long userId = SecurityUtils.currentUserId();
        Map<String, Boolean> state = new java.util.LinkedHashMap<>();
        for (ConsentPurpose purpose : ConsentPurpose.values()) {
            state.put(purpose.name(), rightsService.hasConsent(userId, purpose));
        }
        return ResponseEntity.ok(state);
    }

    /** Grant or withdraw consent for one purpose. */
    @PostMapping("/me/consents")
    public ResponseEntity<Map<String, Object>> setConsent(
            @Valid @RequestBody ConsentRequest body,
            HttpServletRequest request) {

        ConsentRecord record = rightsService.recordConsent(
                SecurityUtils.currentUserId(),
                body.getPurpose(),
                body.isGranted(),
                body.getPolicyVersion(),
                clientIp(request),
                request.getHeader("User-Agent"));

        return ResponseEntity.ok(Map.of(
                "purpose", record.getPurpose().name(),
                "granted", record.isGranted(),
                "recordedAt", record.getRecordedAt().toString()));
    }

    /**
     * Right to erasure.
     *
     * Restricted to a super admin rather than self-service: erasure is
     * irreversible and interacts with the group's retention obligation, so it
     * goes through a person who can check there is no outstanding balance and
     * explain what will and will not be removed.
     */
    @PostMapping("/users/{userId}/erase")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Map<String, String>> erase(
            @PathVariable Long userId,
            @RequestBody(required = false) Map<String, String> body) {

        String reason = body == null ? null : body.get("reason");
        String pseudonym = rightsService.erase(userId, reason);

        return ResponseEntity.ok(Map.of(
                "status", "erased",
                "pseudonym", pseudonym,
                "note", "Personal data removed. Financial records retained in anonymised form."));
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            // Only the first hop is trustworthy; the rest is client-controlled.
            String first = forwarded.split(",")[0].trim();
            return first.length() <= 45 ? first : first.substring(0, 45);
        }
        return request.getRemoteAddr();
    }

    /** Consent request body. */
    public static class ConsentRequest {

        @NotNull(message = "Purpose is required")
        private ConsentPurpose purpose;

        private boolean granted;

        @NotNull(message = "Policy version is required")
        private String policyVersion;

        public ConsentPurpose getPurpose() { return purpose; }
        public void setPurpose(ConsentPurpose purpose) { this.purpose = purpose; }

        public boolean isGranted() { return granted; }
        public void setGranted(boolean granted) { this.granted = granted; }

        public String getPolicyVersion() { return policyVersion; }
        public void setPolicyVersion(String policyVersion) { this.policyVersion = policyVersion; }
    }
}
