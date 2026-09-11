package com.example.ikimina.service;
import java.math.BigDecimal;
import com.example.ikimina.money.Money;
import com.example.ikimina.exception.BusinessRuleException;
import com.example.ikimina.exception.ResourceNotFoundException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.ikimina.dto.GroupSubscriptionSummaryDTO;
import com.example.ikimina.dto.PaymentTransactionDTO;
import com.example.ikimina.dto.SubscriptionDTO;
import com.example.ikimina.model.AuditLog;
import com.example.ikimina.model.GroupSubscription;
import com.example.ikimina.model.GroupSubscription.SubscriptionStatus;
import com.example.ikimina.model.PaymentTransaction;
import com.example.ikimina.model.PaymentTransaction.PaymentMethod;
import com.example.ikimina.model.PaymentTransaction.PaymentStatus;
import com.example.ikimina.model.SavingsGroup;
import com.example.ikimina.model.SubscriptionPlan;
import com.example.ikimina.repository.AuditLogRepository;
import com.example.ikimina.repository.GroupSubscriptionRepository;
import com.example.ikimina.repository.PaymentTransactionRepository;
import com.example.ikimina.repository.SavingsGroupRepository;
import com.example.ikimina.repository.SubscriptionPlanRepository;

@Service
@Transactional
public class SubscriptionService {
    
    @Autowired
    private GroupSubscriptionRepository subscriptionRepository;
    
    @Autowired
    private SubscriptionPlanRepository planRepository;
    
    @Autowired
    private PaymentTransactionRepository paymentRepository;
    
    @Autowired
    private SavingsGroupRepository groupRepository;
    
    @Autowired
    private AuditLogRepository auditRepository;
    
    // Daily job to check subscription status
    @Scheduled(cron = "0 0 2 * * ?") // Run at 2 AM every day
    public void updateSubscriptionStatuses() {
        LocalDate today = LocalDate.now();
        
        // Move expired ACTIVE subscriptions to GRACE_PERIOD
        List<GroupSubscription> expiredActive = subscriptionRepository.findExpiredActiveSubscriptions(today);
        for (GroupSubscription subscription : expiredActive) {
            subscription.setStatus(SubscriptionStatus.GRACE_PERIOD);
            subscription.setGracePeriodEnd(today.plusDays(subscription.getPlan().getGracePeriodDays()));
            subscriptionRepository.save(subscription);
            
            // Log the status change
            logSubscriptionChange(subscription, "STATUS_CHANGE", "ACTIVE", "GRACE_PERIOD", "SYSTEM");
        }
        
        // Move expired GRACE_PERIOD subscriptions to SUSPENDED
        List<GroupSubscription> expiredGrace = subscriptionRepository.findExpiredGracePeriod(today);
        for (GroupSubscription subscription : expiredGrace) {
            suspendGroup(subscription, "Payment not received within grace period");
        }
    }
    
    public SubscriptionDTO createSubscription(Long groupId, Long planId, Long createdBy) {
        SavingsGroup group = groupRepository.findById(groupId)
            .orElseThrow(() -> new ResourceNotFoundException("Group not found"));
        
        SubscriptionPlan plan = planRepository.findById(planId)
            .orElseThrow(() -> new ResourceNotFoundException("Plan not found"));
        
        // Check if subscription already exists
        if (subscriptionRepository.findByGroup(group).isPresent()) {
            throw new BusinessRuleException("Group already has a subscription");
        }
        
        GroupSubscription subscription = new GroupSubscription();
        subscription.setGroup(group);
        subscription.setPlan(plan);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setStartDate(LocalDate.now());
        subscription.setEndDate(LocalDate.now().plusDays(plan.getBillingCycleDays()));
        subscription.setCreatedAt(LocalDateTime.now());
        
        subscription = subscriptionRepository.save(subscription);
        
        // Log the creation
        logSubscriptionChange(subscription, "CREATE", null, "ACTIVE", "User ID: " + createdBy);
        
        return convertToDTO(subscription);
    }
    
    public SubscriptionDTO recordPayment(Long subscriptionId, BigDecimal amount, PaymentMethod method, String reference, Long processedBy) {
        GroupSubscription subscription = subscriptionRepository.findById(subscriptionId)
            .orElseThrow(() -> new ResourceNotFoundException("Subscription not found"));
        
        // Create payment transaction
        PaymentTransaction payment = new PaymentTransaction();
        payment.setGroup(subscription.getGroup());
        payment.setSubscription(subscription);
        payment.setTransactionId(generateTransactionId());
        payment.setAmount(Money.of(amount));
        payment.setCurrency(subscription.getPlan().getCurrency());
        payment.setPaymentMethod(method);
        payment.setStatus(PaymentStatus.COMPLETED);
        payment.setPaymentDate(LocalDateTime.now());
        payment.setProcessedAt(LocalDateTime.now());
        payment.setExternalReference(reference);
        payment.setProcessedBy(processedBy);
        
        payment = paymentRepository.save(payment);
        
        // Update subscription
        subscription.setLastPaymentDate(LocalDate.now());
        
        // If subscription was suspended or in grace period, reactivate it
        if (subscription.getStatus() != SubscriptionStatus.ACTIVE) {
            String oldStatus = subscription.getStatus().name();
            subscription.setStatus(SubscriptionStatus.ACTIVE);
            subscription.setEndDate(LocalDate.now().plusDays(subscription.getPlan().getBillingCycleDays()));
            subscription.setGracePeriodEnd(null);
            subscription.setSuspendedAt(null);
            subscription.setSuspendedReason(null);
            
            // Also unsuspend the group if it was suspended
            if (subscription.getGroup().getIsSuspended()) {
                subscription.getGroup().setIsSuspended(false);
                subscription.getGroup().setSuspensionReason(null);
                subscription.getGroup().setSuspendedAt(null);
                groupRepository.save(subscription.getGroup());
            }
            
            logSubscriptionChange(subscription, "REACTIVATE", oldStatus, "ACTIVE", "Payment received");
        } else {
            // Extend the subscription
            subscription.setEndDate(subscription.getEndDate().plusDays(subscription.getPlan().getBillingCycleDays()));
        }
        
        subscriptionRepository.save(subscription);
        
        return convertToDTO(subscription);
    }
    
    public void suspendGroup(GroupSubscription subscription, String reason) {
        String oldStatus = subscription.getStatus().name();
        subscription.setStatus(SubscriptionStatus.SUSPENDED);
        subscription.setSuspendedAt(LocalDateTime.now());
        subscription.setSuspendedReason(reason);
        
        // Also suspend the group
        subscription.getGroup().setIsSuspended(true);
        subscription.getGroup().setSuspensionReason(reason);
        subscription.getGroup().setSuspendedAt(LocalDateTime.now());
        
        subscriptionRepository.save(subscription);
        groupRepository.save(subscription.getGroup());
        
        logSubscriptionChange(subscription, "SUSPEND", oldStatus, "SUSPENDED", reason);
    }
    
    public SubscriptionDTO manuallyActivateSubscription(Long subscriptionId, String reason, Long activatedBy) {
        GroupSubscription subscription = subscriptionRepository.findById(subscriptionId)
            .orElseThrow(() -> new ResourceNotFoundException("Subscription not found"));
        
        String oldStatus = subscription.getStatus().name();
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setEndDate(LocalDate.now().plusDays(subscription.getPlan().getBillingCycleDays()));
        subscription.setGracePeriodEnd(null);
        subscription.setSuspendedAt(null);
        subscription.setSuspendedReason(null);
        subscription.setManuallyActivatedBy(activatedBy);
        subscription.setManualActivationReason(reason);
        
        // Also unsuspend the group
        subscription.getGroup().setIsSuspended(false);
        subscription.getGroup().setSuspensionReason(null);
        subscription.getGroup().setSuspendedAt(null);
        
        subscriptionRepository.save(subscription);
        groupRepository.save(subscription.getGroup());
        
        logSubscriptionChange(subscription, "MANUAL_ACTIVATE", oldStatus, "ACTIVE", reason);
        
        return convertToDTO(subscription);
    }
    
    public List<GroupSubscriptionSummaryDTO> getAllGroupSubscriptions() {
        List<GroupSubscription> subscriptions = subscriptionRepository.findAll();
        
        return subscriptions.stream()
            .map(this::convertToSummaryDTO)
            .collect(Collectors.toList());
    }
    
    public SubscriptionDTO getSubscriptionByGroup(Long groupId) {
        GroupSubscription subscription = subscriptionRepository.findByGroup(
            groupRepository.findById(groupId).orElseThrow(() -> new ResourceNotFoundException("Group not found"))
        ).orElseThrow(() -> new ResourceNotFoundException("Subscription not found"));
        
        return convertToDTO(subscription);
    }
    
    public List<PaymentTransactionDTO> getPaymentHistory(Long groupId) {
        SavingsGroup group = groupRepository.findById(groupId)
            .orElseThrow(() -> new ResourceNotFoundException("Group not found"));
        
        return paymentRepository.findByGroup(group).stream()
            .map(this::convertToPaymentDTO)
            .collect(Collectors.toList());
    }
    
    private void logSubscriptionChange(GroupSubscription subscription, String action, String oldValue, String newValue, String description) {
        AuditLog log = new AuditLog();
        log.setEntityType("GroupSubscription");
        log.setEntityId(subscription.getId());
        log.setAction(action);
        log.setOldValue(oldValue);
        log.setNewValue(newValue);
        log.setModule("SUBSCRIPTION");
        log.setDescription(description);
        // Set performed by details based on current user context
        // This would be implemented based on your authentication context
        
        auditRepository.save(log);
    }
    
    private String generateTransactionId() {
        return "TXN" + System.currentTimeMillis() + (int)(Math.random() * 1000);
    }
    
    private SubscriptionDTO convertToDTO(GroupSubscription subscription) {
        SubscriptionDTO dto = new SubscriptionDTO();
        dto.setId(subscription.getId());
        dto.setGroupId(subscription.getGroup().getId());
        dto.setGroupName(subscription.getGroup().getName());
        dto.setPlanId(subscription.getPlan().getId());
        dto.setPlanName(subscription.getPlan().getName());
        dto.setMonthlyPrice(subscription.getPlan().getMonthlyPrice());
        dto.setCurrency(subscription.getPlan().getCurrency());
        dto.setStatus(subscription.getStatus().name());
        dto.setStartDate(subscription.getStartDate());
        dto.setEndDate(subscription.getEndDate());
        dto.setGracePeriodEnd(subscription.getGracePeriodEnd());
        dto.setLastPaymentDate(subscription.getLastPaymentDate());
        dto.setAutoRenew(subscription.getAutoRenew());
        dto.setCreatedAt(subscription.getCreatedAt());
        dto.setUpdatedAt(subscription.getUpdatedAt());
        dto.setSuspendedAt(subscription.getSuspendedAt());
        dto.setSuspendedReason(subscription.getSuspendedReason());
        
        // Calculate days until expiry
        if (subscription.getEndDate() != null) {
            dto.setDaysUntilExpiry((int) java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), subscription.getEndDate()));
        }
        
        // Calculate days in grace period
        if (subscription.getGracePeriodEnd() != null) {
            dto.setDaysInGracePeriod((int) java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), subscription.getGracePeriodEnd()));
        }
        
        dto.setIsSuspended(subscription.getGroup().getIsSuspended());
        
        return dto;
    }
    
    private GroupSubscriptionSummaryDTO convertToSummaryDTO(GroupSubscription subscription) {
        GroupSubscriptionSummaryDTO dto = new GroupSubscriptionSummaryDTO();
        dto.setGroupId(subscription.getGroup().getId());
        dto.setGroupName(subscription.getGroup().getName());
        dto.setGroupAdminName(subscription.getGroup().getAdmin().getFirstName() + " " + subscription.getGroup().getAdmin().getLastName());
        dto.setStatus(subscription.getStatus().name());
        dto.setPlanName(subscription.getPlan().getName());
        dto.setMonthlyPrice(subscription.getPlan().getMonthlyPrice());
        dto.setEndDate(subscription.getEndDate());
        dto.setGracePeriodEnd(subscription.getGracePeriodEnd());
        dto.setMemberCount(subscription.getGroup().getMembers().size());
        dto.setIsSuspended(subscription.getGroup().getIsSuspended());
        dto.setSuspensionReason(subscription.getGroup().getSuspensionReason());
        
        if (subscription.getEndDate() != null) {
            dto.setDaysUntilExpiry((int) java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), subscription.getEndDate()));
        }
        
        // Flag groups that need attention
        dto.setNeedsAttention(
            subscription.getStatus() == SubscriptionStatus.GRACE_PERIOD ||
            subscription.getStatus() == SubscriptionStatus.SUSPENDED ||
            (dto.getDaysUntilExpiry() != null && dto.getDaysUntilExpiry() <= 7)
        );
        
        return dto;
    }
    
    private PaymentTransactionDTO convertToPaymentDTO(PaymentTransaction payment) {
        PaymentTransactionDTO dto = new PaymentTransactionDTO();
        dto.setId(payment.getId());
        dto.setGroupId(payment.getGroup().getId());
        dto.setGroupName(payment.getGroup().getName());
        dto.setSubscriptionId(payment.getSubscription().getId());
        dto.setTransactionId(payment.getTransactionId());
        dto.setAmount(payment.getAmount());
        dto.setCurrency(payment.getCurrency());
        dto.setPaymentMethod(payment.getPaymentMethod().name());
        dto.setStatus(payment.getStatus().name());
        dto.setPaymentDate(payment.getPaymentDate());
        dto.setProcessedAt(payment.getProcessedAt());
        dto.setExternalReference(payment.getExternalReference());
        dto.setFailureReason(payment.getFailureReason());
        dto.setProcessedBy(payment.getProcessedBy());
        dto.setNotes(payment.getNotes());
        
        return dto;
    }
}
