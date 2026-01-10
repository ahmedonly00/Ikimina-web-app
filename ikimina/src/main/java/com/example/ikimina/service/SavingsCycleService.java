package com.example.ikimina.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.ikimina.dto.MemberPayoutDTO;
import com.example.ikimina.dto.SavingsCycleDTO;
import com.example.ikimina.enums.SavingsType;
import com.example.ikimina.model.MemberPayout;
import com.example.ikimina.model.MemberPayout.PayoutStatus;
import com.example.ikimina.model.Savings;
import com.example.ikimina.model.SavingsCycle;
import com.example.ikimina.model.SavingsCycle.CycleStatus;
import com.example.ikimina.model.SavingsGroup;
import com.example.ikimina.model.User;
import com.example.ikimina.repository.MemberPayoutRepository;
import com.example.ikimina.repository.SavingsCycleRepository;
import com.example.ikimina.repository.SavingsGroupRepository;
import com.example.ikimina.repository.SavingsRepository;
import com.example.ikimina.repository.UserRepository;

@Service
@Transactional
public class SavingsCycleService {
    
    @Autowired
    private SavingsCycleRepository savingsCycleRepository;
    
    @Autowired
    private MemberPayoutRepository memberPayoutRepository;
    
    @Autowired
    private SavingsRepository savingsRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private SavingsGroupRepository savingsGroupRepository;
    
    public SavingsCycleDTO createNewCycle(Long savingsGroupId) {
        SavingsGroup group = savingsGroupRepository.findById(savingsGroupId)
            .orElseThrow(() -> new RuntimeException("Savings group not found"));
        
        // Check if there's an active cycle
        Optional<SavingsCycle> activeCycle = savingsCycleRepository.findBySavingsGroupAndStatus(group, CycleStatus.ACTIVE);
        if (activeCycle.isPresent()) {
            throw new RuntimeException("Group already has an active savings cycle");
        }
        
        SavingsCycle cycle = new SavingsCycle();
        cycle.setSavingsGroup(group);
        cycle.setStartDate(LocalDate.now());
        cycle.setEndDate(LocalDate.now().plusMonths(6));
        cycle.setStatus(CycleStatus.ACTIVE);
        
        cycle = savingsCycleRepository.save(cycle);
        return convertToDTO(cycle);
    }
    
    public SavingsCycleDTO calculateCyclePayouts(Long cycleId) {
        SavingsCycle cycle = savingsCycleRepository.findById(cycleId)
            .orElseThrow(() -> new RuntimeException("Savings cycle not found"));
        
        if (cycle.getStatus() != CycleStatus.COMPLETED) {
            throw new RuntimeException("Cycle must be completed before calculating payouts");
        }
        
        // Get all members in the group
        List<User> members = userRepository.findBySavingsGroupId(cycle.getSavingsGroup().getId());
        
        // Calculate totals for each member
        double totalUbwizigameCollected = 0.0;
        double totalIngobokaCollected = 0.0;
        
        for (User member : members) {
            // Get all savings for this member during the cycle period
            List<Savings> memberSavings = savingsRepository.findByUserIdAndDateBetween(
                member.getId(), cycle.getStartDate(), cycle.getEndDate()
            );
            
            double memberUbwizigame = 0.0;
            double memberIngoboka = 0.0;
            
            for (Savings saving : memberSavings) {
                if (saving.getType() == SavingsType.UBWIZIGAME) {
                    memberUbwizigame += saving.getAmount();
                    totalUbwizigameCollected += saving.getAmount();
                } else if (saving.getType() == SavingsType.INGOBOKA) {
                    memberIngoboka += saving.getAmount();
                    totalIngobokaCollected += saving.getAmount();
                }
            }
            
            // Create or update member payout
            MemberPayout payout = memberPayoutRepository.findBySavingsCycleAndMember(cycle, member)
                .orElse(new MemberPayout());
            
            payout.setSavingsCycle(cycle);
            payout.setMember(member);
            payout.setTotalUbwizigame(memberUbwizigame);
            payout.setTotalIngoboka(memberIngoboka);
            payout.setPayoutAmount(memberUbwizigame); // Only Ubwizigame is paid out
            payout.setStatus(PayoutStatus.PENDING);
            
            memberPayoutRepository.save(payout);
        }
        
        // Update cycle totals
        cycle.setTotalUbwizigameCollected(totalUbwizigameCollected);
        cycle.setTotalIngobokaCollected(totalIngobokaCollected);
        cycle.setStatus(CycleStatus.DISTRIBUTED);
        cycle.setDistributedAt(LocalDate.now());
        
        cycle = savingsCycleRepository.save(cycle);
        return convertToDTO(cycle);
    }
    
    public List<SavingsCycleDTO> getCyclesByGroup(Long savingsGroupId) {
        SavingsGroup group = savingsGroupRepository.findById(savingsGroupId)
            .orElseThrow(() -> new RuntimeException("Savings group not found"));
        
        return savingsCycleRepository.findBySavingsGroupOrderByStartDateDesc(group)
            .stream()
            .map(this::convertToDTO)
            .collect(Collectors.toList());
    }
    
    public SavingsCycleDTO getCurrentCycle(Long savingsGroupId) {
        SavingsGroup group = savingsGroupRepository.findById(savingsGroupId)
            .orElseThrow(() -> new RuntimeException("Savings group not found"));
        
        Optional<SavingsCycle> activeCycle = savingsCycleRepository.findBySavingsGroupAndStatus(group, CycleStatus.ACTIVE);
        return activeCycle.map(this::convertToDTO).orElse(null);
    }
    
    public List<MemberPayoutDTO> getMemberPayouts(Long cycleId) {
        SavingsCycle cycle = savingsCycleRepository.findById(cycleId)
            .orElseThrow(() -> new RuntimeException("Savings cycle not found"));
        
        return memberPayoutRepository.findBySavingsCycle(cycle)
            .stream()
            .map(this::convertToPayoutDTO)
            .collect(Collectors.toList());
    }
    
    public void markPayoutAsPaid(Long payoutId) {
        MemberPayout payout = memberPayoutRepository.findById(payoutId)
            .orElseThrow(() -> new RuntimeException("Payout not found"));
        
        payout.setStatus(PayoutStatus.PAID);
        payout.setPaidAt(LocalDate.now());
        
        // Update cycle distributed total
        SavingsCycle cycle = payout.getSavingsCycle();
        cycle.setTotalUbwizigameDistributed(
            cycle.getTotalUbwizigameDistributed() + payout.getPayoutAmount()
        );
        
        savingsCycleRepository.save(cycle);
        memberPayoutRepository.save(payout);
    }
    
    private SavingsCycleDTO convertToDTO(SavingsCycle cycle) {
        SavingsCycleDTO dto = new SavingsCycleDTO();
        dto.setId(cycle.getId());
        dto.setSavingsGroupId(cycle.getSavingsGroup().getId());
        dto.setSavingsGroupName(cycle.getSavingsGroup().getName());
        dto.setStartDate(cycle.getStartDate());
        dto.setEndDate(cycle.getEndDate());
        dto.setStatus(cycle.getStatus().name());
        dto.setTotalUbwizigameCollected(cycle.getTotalUbwizigameCollected());
        dto.setTotalIngobokaCollected(cycle.getTotalIngobokaCollected());
        dto.setTotalUbwizigameDistributed(cycle.getTotalUbwizigameDistributed());
        dto.setCreatedAt(cycle.getCreatedAt());
        dto.setDistributedAt(cycle.getDistributedAt());
        
        List<MemberPayoutDTO> payouts = memberPayoutRepository.findBySavingsCycle(cycle)
            .stream()
            .map(this::convertToPayoutDTO)
            .collect(Collectors.toList());
        dto.setMemberPayouts(payouts);
        
        return dto;
    }
    
    private MemberPayoutDTO convertToPayoutDTO(MemberPayout payout) {
        MemberPayoutDTO dto = new MemberPayoutDTO();
        dto.setId(payout.getId());
        dto.setMemberId(payout.getMember().getId());
        dto.setMemberName(payout.getMember().getFirstName() + " " + payout.getMember().getLastName());
        dto.setMemberNumber(payout.getMember().getMemberNumber());
        dto.setTotalUbwizigame(payout.getTotalUbwizigame());
        dto.setTotalIngoboka(payout.getTotalIngoboka());
        dto.setPayoutAmount(payout.getPayoutAmount());
        dto.setCreatedAt(payout.getCreatedAt());
        dto.setPaidAt(payout.getPaidAt());
        dto.setStatus(payout.getStatus().name());
        return dto;
    }
}
