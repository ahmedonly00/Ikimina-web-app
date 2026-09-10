package com.example.ikimina.service;
import com.example.ikimina.exception.ResourceNotFoundException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.ikimina.dto.GroupSubscriptionSummaryDTO;
import com.example.ikimina.enums.Role;
import com.example.ikimina.model.AuditLog;
import com.example.ikimina.model.SavingsGroup;
import com.example.ikimina.model.SubscriptionPlan;
import com.example.ikimina.model.User;
import com.example.ikimina.repository.AuditLogRepository;
import com.example.ikimina.repository.SavingsGroupRepository;
import com.example.ikimina.repository.SubscriptionPlanRepository;
import com.example.ikimina.repository.UserRepository;

@Service
@Transactional
public class SuperAdminService {
    
    @Autowired
    private SavingsGroupRepository groupRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private SubscriptionPlanRepository planRepository;
    
    @Autowired
    private SubscriptionService subscriptionService;
    
    @Autowired
    private AuditLogRepository auditRepository;
    
    // Group Management
    public Page<SavingsGroup> getAllGroups(Pageable pageable) {
        return groupRepository.findAll(pageable);
    }
    
    public SavingsGroup activateGroup(Long groupId, String reason, Long activatedBy) {
        SavingsGroup group = groupRepository.findById(groupId)
            .orElseThrow(() -> new ResourceNotFoundException("Group not found"));
        
        if (!group.getIsActive()) {
            group.setIsActive(true);
            groupRepository.save(group);
            
            logAction("GROUP", groupId, "ACTIVATE", null, "ACTIVE", reason, activatedBy);
        }
        
        return group;
    }
    
    public SavingsGroup suspendGroup(Long groupId, String reason, Long suspendedBy) {
        SavingsGroup group = groupRepository.findById(groupId)
            .orElseThrow(() -> new ResourceNotFoundException("Group not found"));
        
        if (group.getIsActive()) {
            group.setIsActive(false);
            group.setIsSuspended(true);
            group.setSuspensionReason(reason);
            group.setSuspendedAt(LocalDateTime.now());
            groupRepository.save(group);
            
            // Also suspend subscription if exists
            try {
                subscriptionService.getSubscriptionByGroup(groupId);
                // Suspend through subscription service for proper logging
            } catch (Exception e) {
                // No subscription exists
            }
            
            logAction("GROUP", groupId, "SUSPEND", "ACTIVE", "SUSPENDED", reason, suspendedBy);
        }
        
        return group;
    }
    
    public SavingsGroup forceReadOnlyMode(Long groupId, String reason, Long activatedBy) {
        SavingsGroup group = groupRepository.findById(groupId)
            .orElseThrow(() -> new ResourceNotFoundException("Group not found"));
        
        group.setIsSuspended(true);
        group.setSuspensionReason("READ_ONLY: " + reason);
        group.setSuspendedAt(LocalDateTime.now());
        groupRepository.save(group);
        
        logAction("GROUP", groupId, "FORCE_READ_ONLY", null, "READ_ONLY", reason, activatedBy);
        
        return group;
    }
    
    // User Management
    public Page<User> getAllUsers(Pageable pageable) {
        return userRepository.findAll(pageable);
    }
    
    public User promoteToGroupAdmin(Long userId, Long promotedBy) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        
        if (user.getRole() != Role.ROLE_GROUP_ADMIN) {
            String oldRole = user.getRole().name();
            user.setRole(Role.ROLE_GROUP_ADMIN);
            userRepository.save(user);
            
            logAction("USER", userId, "PROMOTE", oldRole, "ROLE_GROUP_ADMIN", "Promoted to Group Admin", promotedBy);
        }
        
        return user;
    }
    
    public User demoteFromGroupAdmin(Long userId, Long demotedBy) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        
        if (user.getRole() == Role.ROLE_GROUP_ADMIN) {
            String oldRole = user.getRole().name();
            user.setRole(Role.ROLE_USER);
            userRepository.save(user);
            
            logAction("USER", userId, "DEMOTE", oldRole, "ROLE_USER", "Demoted from Group Admin", demotedBy);
        }
        
        return user;
    }
    
    public User suspendUser(Long userId, String reason, Long suspendedBy) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        
        if (user.isActive()) {
            user.setActive(false);
            userRepository.save(user);
            
            logAction("USER", userId, "SUSPEND", "ACTIVE", "SUSPENDED", reason, suspendedBy);
        }
        
        return user;
    }
    
    public User reactivateUser(Long userId, String reason, Long reactivatedBy) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        
        if (!user.isActive()) {
            user.setActive(true);
            userRepository.save(user);
            
            logAction("USER", userId, "REACTIVATE", "SUSPENDED", "ACTIVE", reason, reactivatedBy);
        }
        
        return user;
    }
    
    // Subscription Plan Management
    public SubscriptionPlan createSubscriptionPlan(SubscriptionPlan plan, Long createdBy) {
        plan = planRepository.save(plan);
        
        logAction("SUBSCRIPTION_PLAN", plan.getId(), "CREATE", null, "ACTIVE", 
                 "Created plan: " + plan.getName(), createdBy);
        
        return plan;
    }
    
    public SubscriptionPlan updateSubscriptionPlan(SubscriptionPlan plan, Long updatedBy) {
        SubscriptionPlan existing = planRepository.findById(plan.getId())
            .orElseThrow(() -> new ResourceNotFoundException("Plan not found"));
        
        // Log changes
        logAction("SUBSCRIPTION_PLAN", plan.getId(), "UPDATE", 
                 "Old: " + existing.toString(), 
                 "New: " + plan.toString(), 
                 "Updated plan", updatedBy);
        
        planRepository.save(plan);
        return plan;
    }
    
    public List<SubscriptionPlan> getAllSubscriptionPlans() {
        return planRepository.findAll();
    }
    
    // Financial Oversight
    public List<GroupSubscriptionSummaryDTO> getFinancialOverview() {
        return subscriptionService.getAllGroupSubscriptions();
    }
    
    // Audit Logs
    /**
     * Audit history, newest first. Paged because this table grows with every
     * money movement and previously returned the whole thing, filtered in
     * memory in the application.
     */
    public Page<AuditLog> getAuditLogs(String entityType, LocalDateTime since, Pageable pageable) {
        if (entityType != null && since != null) {
            return auditRepository.findByEntityTypeAndCreatedAtAfterOrderByCreatedAtDesc(
                    entityType, since, pageable);
        }
        if (entityType != null) {
            return auditRepository.findByEntityTypeOrderByCreatedAtDesc(entityType, pageable);
        }
        if (since != null) {
            return auditRepository.findByCreatedAtAfterOrderByCreatedAtDesc(since, pageable);
        }
        return auditRepository.findAllByOrderByCreatedAtDesc(pageable);
    }    
    // Global Settings
    public void updateGlobalSettings(String key, String value, Long updatedBy) {
        // This would typically use a settings table
        logAction("GLOBAL_SETTINGS", null, "UPDATE", key, value, "Updated global setting", updatedBy);
    }
    
    private void logAction(String entityType, Long entityId, String action, 
                          String oldValue, String newValue, String description, Long performedBy) {
        AuditLog log = new AuditLog();
        log.setEntityType(entityType);
        if (entityId != null) log.setEntityId(entityId);
        log.setAction(action);
        log.setOldValue(oldValue);
        log.setNewValue(newValue);
        log.setModule("SUPER_ADMIN");
        log.setDescription(description);
        log.setPerformedBy(performedBy);
        // Set performed by name and role based on user lookup
        
        auditRepository.save(log);
    }
}
