package com.example.ikimina.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.example.ikimina.dto.PaymentTransactionDTO;
import com.example.ikimina.dto.SubscriptionDTO;
import com.example.ikimina.model.PaymentTransaction.PaymentMethod;
import com.example.ikimina.service.SubscriptionService;

@RestController
@RequestMapping("/api/subscription")
@CrossOrigin(origins = "*")
public class SubscriptionController {
    
    @Autowired
    private SubscriptionService subscriptionService;
    
    @GetMapping("/status/{groupId}")
    @PreAuthorize("hasRole('ROLE_GROUP_ADMIN') or hasRole('ROLE_SUPER_ADMIN')")
    public ResponseEntity<SubscriptionDTO> getSubscriptionStatus(@PathVariable Long groupId) {
        return ResponseEntity.ok(subscriptionService.getSubscriptionByGroup(groupId));
    }
    
    @PostMapping("/payment")
    @PreAuthorize("hasRole('ROLE_GROUP_ADMIN') or hasRole('ROLE_SUPER_ADMIN')")
    public ResponseEntity<SubscriptionDTO> recordPayment(
            @RequestParam Long subscriptionId,
            @RequestParam Double amount,
            @RequestParam PaymentMethod method,
            @RequestParam(required = false) String reference) {
        return ResponseEntity.ok(subscriptionService.recordPayment(
            subscriptionId, amount, method, reference, getCurrentUserId()));
    }
    
    @GetMapping("/payments/{groupId}")
    @PreAuthorize("hasRole('ROLE_GROUP_ADMIN') or hasRole('ROLE_SUPER_ADMIN')")
    public ResponseEntity<List<PaymentTransactionDTO>> getPaymentHistory(@PathVariable Long groupId) {
        return ResponseEntity.ok(subscriptionService.getPaymentHistory(groupId));
    }
    
    private Long getCurrentUserId() {
        // Implement based on your authentication context
        return 1L;
    }
}
