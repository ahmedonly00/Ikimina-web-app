package com.example.ikimina.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import org.springframework.validation.annotation.Validated;
import java.math.BigDecimal;
import jakarta.validation.constraints.DecimalMin;
import com.example.ikimina.dto.PaymentTransactionDTO;
import com.example.ikimina.security.SecurityUtils;
import com.example.ikimina.dto.SubscriptionDTO;
import com.example.ikimina.model.PaymentTransaction.PaymentMethod;
import com.example.ikimina.service.SubscriptionService;

@Validated
@RestController
@RequestMapping("/api/subscription")
public class SubscriptionController {
    
    @Autowired
    private SubscriptionService subscriptionService;
    
    @GetMapping("/status/{groupId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','GROUP_ADMIN')")
    public ResponseEntity<SubscriptionDTO> getSubscriptionStatus(@PathVariable Long groupId) {
        return ResponseEntity.ok(subscriptionService.getSubscriptionByGroup(groupId));
    }
    
    @PostMapping("/payment")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','GROUP_ADMIN')")
    public ResponseEntity<SubscriptionDTO> recordPayment(
            @RequestParam Long subscriptionId,
            @RequestParam @DecimalMin(value = "0.0", inclusive = false, message = "Amount must be greater than zero") BigDecimal amount,
            @RequestParam PaymentMethod method,
            @RequestParam(required = false) String reference) {
        return ResponseEntity.ok(subscriptionService.recordPayment(
            subscriptionId, amount, method, reference, getCurrentUserId()));
    }
    
    @GetMapping("/payments/{groupId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','GROUP_ADMIN')")
    public ResponseEntity<List<PaymentTransactionDTO>> getPaymentHistory(@PathVariable Long groupId) {
        return ResponseEntity.ok(subscriptionService.getPaymentHistory(groupId));
    }
    
    private Long getCurrentUserId() {
        return SecurityUtils.currentUserId();
    }
}
