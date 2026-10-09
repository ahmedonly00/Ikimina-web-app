package rw.ikimina.ledger.internal;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import rw.ikimina.shared.security.RequiresRecentAuthentication;

/**
 * Reversing a ledger entry (spec 17.3 POST .../journals/{journalId}/reverse), in two steps.
 *
 * <p>The person who records money (CONTRIBUTION_RECORD - the Treasurer) asks; an approver of
 * money movements (LOAN_APPROVE - President or Treasurer) who is not the requester decides.
 * The spec's matrix has no separate reversal permission, so these existing ones are used.
 */
@RestController
@RequestMapping("/api/v1/groups/{groupId}")
class ReversalController {

    record ReasonRequest(@NotBlank @Size(max = 500) String reason) {
    }

    private final ReversalService reversals;

    ReversalController(ReversalService reversals) {
        this.reversals = reversals;
    }

    @PostMapping("/journals/{journalId}/reverse")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("@perm.has(#groupId, 'CONTRIBUTION_RECORD')")
    ReversalService.ReversalView request(@PathVariable UUID groupId, @PathVariable UUID journalId,
                                         @Valid @RequestBody ReasonRequest request) {
        return reversals.request(journalId, request.reason());
    }

    @GetMapping("/reversals")
    @PreAuthorize("@perm.hasAny(#groupId, 'CONTRIBUTION_RECORD', 'LOAN_APPROVE')")
    List<ReversalService.ReversalView> pending(@PathVariable UUID groupId) {
        return reversals.pending();
    }

    @PostMapping("/reversals/{requestId}/approve")
    @PreAuthorize("@perm.has(#groupId, 'LOAN_APPROVE')")
    @RequiresRecentAuthentication
    ReversalService.ReversalView approve(@PathVariable UUID groupId, @PathVariable UUID requestId) {
        return reversals.approve(requestId);
    }

    @PostMapping("/reversals/{requestId}/reject")
    @PreAuthorize("@perm.has(#groupId, 'LOAN_APPROVE')")
    ReversalService.ReversalView reject(@PathVariable UUID groupId, @PathVariable UUID requestId,
                                        @Valid @RequestBody ReasonRequest request) {
        return reversals.reject(requestId, request.reason());
    }
}
