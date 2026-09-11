package com.example.ikimina.controller;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.example.ikimina.dto.GroupSubscriptionSummaryDTO;
import com.example.ikimina.security.SecurityUtils;
import com.example.ikimina.model.AuditLog;
import com.example.ikimina.model.SavingsGroup;
import com.example.ikimina.model.SubscriptionPlan;
import com.example.ikimina.model.User;
import com.example.ikimina.service.SuperAdminService;
import com.example.ikimina.service.SubscriptionService;

@RestController
@RequestMapping("/api/super-admin")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class SuperAdminController {
    
    @Autowired
    private SuperAdminService superAdminService;
    
    @Autowired
    private SubscriptionService subscriptionService;
    
    // Group Management
    @GetMapping("/groups")
    public ResponseEntity<Page<SavingsGroup>> getAllGroups(
            @PageableDefault(size = 25, sort = "name") Pageable pageable) {
        return ResponseEntity.ok(superAdminService.getAllGroups(pageable));
    }
    
    @PostMapping("/groups/{groupId}/activate")
    public ResponseEntity<SavingsGroup> activateGroup(
            @PathVariable Long groupId,
            @RequestBody String reason) {
        SavingsGroup group = superAdminService.activateGroup(groupId, reason, getCurrentUserId());
        return ResponseEntity.ok(group);
    }
    
    @PostMapping("/groups/{groupId}/suspend")
    public ResponseEntity<SavingsGroup> suspendGroup(
            @PathVariable Long groupId,
            @RequestBody String reason) {
        SavingsGroup group = superAdminService.suspendGroup(groupId, reason, getCurrentUserId());
        return ResponseEntity.ok(group);
    }
    
    @PostMapping("/groups/{groupId}/readonly")
    public ResponseEntity<SavingsGroup> forceReadOnlyMode(
            @PathVariable Long groupId,
            @RequestBody String reason) {
        SavingsGroup group = superAdminService.forceReadOnlyMode(groupId, reason, getCurrentUserId());
        return ResponseEntity.ok(group);
    }
    
    // User Management
    @GetMapping("/users")
    public ResponseEntity<Page<User>> getAllUsers(
            @PageableDefault(size = 25, sort = "id") Pageable pageable) {
        return ResponseEntity.ok(superAdminService.getAllUsers(pageable));
    }
    
    @PostMapping("/users/{userId}/promote-admin")
    public ResponseEntity<User> promoteToGroupAdmin(@PathVariable Long userId) {
        User user = superAdminService.promoteToGroupAdmin(userId, getCurrentUserId());
        return ResponseEntity.ok(user);
    }
    
    @PostMapping("/users/{userId}/demote-admin")
    public ResponseEntity<User> demoteFromGroupAdmin(@PathVariable Long userId) {
        User user = superAdminService.demoteFromGroupAdmin(userId, getCurrentUserId());
        return ResponseEntity.ok(user);
    }
    
    @PostMapping("/users/{userId}/suspend")
    public ResponseEntity<User> suspendUser(
            @PathVariable Long userId,
            @RequestBody String reason) {
        User user = superAdminService.suspendUser(userId, reason, getCurrentUserId());
        return ResponseEntity.ok(user);
    }
    
    @PostMapping("/users/{userId}/reactivate")
    public ResponseEntity<User> reactivateUser(
            @PathVariable Long userId,
            @RequestBody String reason) {
        User user = superAdminService.reactivateUser(userId, reason, getCurrentUserId());
        return ResponseEntity.ok(user);
    }
    
    // Subscription Management
    @GetMapping("/subscriptions")
    public ResponseEntity<List<GroupSubscriptionSummaryDTO>> getAllSubscriptions() {
        return ResponseEntity.ok(superAdminService.getFinancialOverview());
    }
    
    @PostMapping("/subscriptions/{subscriptionId}/activate")
    public ResponseEntity<?> manuallyActivateSubscription(
            @PathVariable Long subscriptionId,
            @RequestBody String reason) {
        return ResponseEntity.ok(subscriptionService.manuallyActivateSubscription(
            subscriptionId, reason, getCurrentUserId()));
    }
    
    @PostMapping("/subscriptions/{subscriptionId}/extend")
    public ResponseEntity<?> extendSubscription(
            @PathVariable Long subscriptionId,
            @RequestBody Integer days) {
        // Implementation would extend subscription by specified days
        return ResponseEntity.ok().build();
    }
    
    @GetMapping("/groups/{groupId}/payments")
    public ResponseEntity<?> getPaymentHistory(@PathVariable Long groupId) {
        return ResponseEntity.ok(subscriptionService.getPaymentHistory(groupId));
    }
    
    // Subscription Plans
    @PostMapping("/plans")
    public ResponseEntity<SubscriptionPlan> createPlan(@RequestBody SubscriptionPlan plan) {
        return ResponseEntity.ok(superAdminService.createSubscriptionPlan(plan, getCurrentUserId()));
    }
    
    @PutMapping("/plans")
    public ResponseEntity<SubscriptionPlan> updatePlan(@RequestBody SubscriptionPlan plan) {
        return ResponseEntity.ok(superAdminService.updateSubscriptionPlan(plan, getCurrentUserId()));
    }
    
    @GetMapping("/plans")
    public ResponseEntity<List<SubscriptionPlan>> getAllPlans() {
        return ResponseEntity.ok(superAdminService.getAllSubscriptionPlans());
    }
    
    // Audit Logs
    @GetMapping("/audit")
    public ResponseEntity<Page<AuditLog>> getAuditLogs(
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime since,
            @PageableDefault(size = 50) Pageable pageable) {
        return ResponseEntity.ok(superAdminService.getAuditLogs(entityType, since, pageable));
    }
    
    // Financial Reports
    @GetMapping("/reports/financial")
    public ResponseEntity<?> getFinancialReport() {
        // Return comprehensive financial overview
        return ResponseEntity.ok(superAdminService.getFinancialOverview());
    }
    
    // Global Settings
    @PostMapping("/settings")
    public ResponseEntity<?> updateGlobalSetting(
            @RequestParam String key,
            @RequestParam String value) {
        superAdminService.updateGlobalSettings(key, value, getCurrentUserId());
        return ResponseEntity.ok().build();
    }
    
    private Long getCurrentUserId() {
        return SecurityUtils.currentUserId();
    }
}
