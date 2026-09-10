package com.example.ikimina.payments;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Operator view over inbound payments.
 *
 * Two things a group admin needs: which payments arrived and could not be
 * matched to a member, and whether the ledger still agrees with what providers
 * reported. Both are read-only - correcting a mismatch is a deliberate ledger
 * adjustment, not a button here.
 */
@RestController
@RequestMapping("/api/payments")
public class PaymentAdminController {

    private final InboundPaymentEventRepository eventRepository;
    private final PaymentReconciliationService reconciliationService;

    public PaymentAdminController(InboundPaymentEventRepository eventRepository,
                                  PaymentReconciliationService reconciliationService) {
        this.eventRepository = eventRepository;
        this.reconciliationService = reconciliationService;
    }

    /** Payments recorded for a group, newest first. */
    @GetMapping("/groups/{groupId}/events")
    @PreAuthorize("@savingsGroupSecurity.canAdministerGroup(authentication, #groupId)")
    public ResponseEntity<Page<InboundPaymentEvent>> events(
            @PathVariable Long groupId,
            @PageableDefault(size = 25) Pageable pageable) {
        return ResponseEntity.ok(eventRepository.findByGroupIdOrderByReceivedAtDesc(groupId, pageable));
    }

    /**
     * Payments that arrived but have not reached a member. This is money the
     * group has received and nobody has been credited for, so it is the queue
     * that must not be allowed to grow.
     */
    @GetMapping("/unmatched")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Page<InboundPaymentEvent>> unmatched(
            @PageableDefault(size = 50) Pageable pageable) {
        return ResponseEntity.ok(eventRepository.findByProcessingStatusOrderByReceivedAtDesc(
                InboundPaymentEvent.ProcessingStatus.FAILED, pageable));
    }

    /** Does the ledger still agree with what the providers reported? */
    @GetMapping("/groups/{groupId}/reconciliation")
    @PreAuthorize("@savingsGroupSecurity.canAdministerGroup(authentication, #groupId)")
    public ResponseEntity<PaymentReconciliationService.Report> reconcile(
            @PathVariable Long groupId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(reconciliationService.reconcile(groupId, from, to));
    }
}