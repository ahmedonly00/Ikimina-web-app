package com.example.ikimina.service;
import java.math.BigDecimal;
import com.example.ikimina.money.Money;
import com.example.ikimina.exception.BusinessRuleException;
import com.example.ikimina.exception.ResourceNotFoundException;

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
            .orElseThrow(() -> new ResourceNotFoundException("Savings group not found"));
        
        // Check if there's an active cycle
        Optional<SavingsCycle> activeCycle = savingsCycleRepository.findBySavingsGroupAndStatus(group, CycleStatus.ACTIVE);
        if (activeCycle.isPresent()) {
            throw new BusinessRuleException("Group already has an active savings cycle");
        }
        
        SavingsCycle cycle = new SavingsCycle();
        cycle.setSavingsGroup(group);
        cycle.setStartDate(LocalDate.now());
        cycle.setEndDate(LocalDate.now().plusMonths(6));
        cycle.setStatus(CycleStatus.ACTIVE);
        
        cycle = savingsCycleRepository.save(cycle);
        return convertToDTO(cycle);
    }
    
    /**
     * Computes each member's payout for a completed cycle and records the cycle
     * totals.
     *
     * All arithmetic is BigDecimal. The group totals are derived by summing the
     * same per-member figures that are written to each payout row, so the cycle
     * total always equals the sum of member balances exactly - see
     * SavingsCycleServiceTest for the reconciliation property.
     */
    public SavingsCycleDTO calculateCyclePayouts(Long cycleId) {
        SavingsCycle cycle = savingsCycleRepository.findById(cycleId)
            .orElseThrow(() -> new ResourceNotFoundException("Savings cycle not found"));

        if (cycle.getStatus() != CycleStatus.COMPLETED) {
            throw new BusinessRuleException("Cycle must be completed before calculating payouts");
        }

        List<User> members = userRepository.findBySavingsGroupId(cycle.getSavingsGroup().getId());

        BigDecimal totalUbwizigameCollected = BigDecimal.ZERO;
        BigDecimal totalIngobokaCollected = BigDecimal.ZERO;

        for (User member : members) {
            List<Savings> memberSavings = savingsRepository.findByUserIdAndDateBetween(
                member.getId(), cycle.getStartDate(), cycle.getEndDate()
            );

            BigDecimal memberUbwizigame = BigDecimal.ZERO;
            BigDecimal memberIngoboka = BigDecimal.ZERO;

            for (Savings saving : memberSavings) {
                if (saving.getType() == SavingsType.UBWIZIGAME) {
                    memberUbwizigame = memberUbwizigame.add(saving.getAmount());
                } else if (saving.getType() == SavingsType.INGOBOKA) {
                    memberIngoboka = memberIngoboka.add(saving.getAmount());
                }
            }

            memberUbwizigame = Money.of(memberUbwizigame);
            memberIngoboka = Money.of(memberIngoboka);

            // Group totals are the sum of exactly what each member is credited,
            // so the two can never disagree.
            totalUbwizigameCollected = totalUbwizigameCollected.add(memberUbwizigame);
            totalIngobokaCollected = totalIngobokaCollected.add(memberIngoboka);

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

        cycle.setTotalUbwizigameCollected(Money.of(totalUbwizigameCollected));
        cycle.setTotalIngobokaCollected(Money.of(totalIngobokaCollected));
        cycle.setStatus(CycleStatus.DISTRIBUTED);
        cycle.setDistributedAt(LocalDate.now());

        cycle = savingsCycleRepository.save(cycle);
        return convertToDTO(cycle);
    }
    
    public List<SavingsCycleDTO> getCyclesByGroup(Long savingsGroupId) {
        SavingsGroup group = savingsGroupRepository.findById(savingsGroupId)
            .orElseThrow(() -> new ResourceNotFoundException("Savings group not found"));
        
        return savingsCycleRepository.findBySavingsGroupOrderByStartDateDesc(group)
            .stream()
            .map(this::convertToDTO)
            .collect(Collectors.toList());
    }
    
    public SavingsCycleDTO getCurrentCycle(Long savingsGroupId) {
        SavingsGroup group = savingsGroupRepository.findById(savingsGroupId)
            .orElseThrow(() -> new ResourceNotFoundException("Savings group not found"));
        
        Optional<SavingsCycle> activeCycle = savingsCycleRepository.findBySavingsGroupAndStatus(group, CycleStatus.ACTIVE);
        return activeCycle.map(this::convertToDTO).orElse(null);
    }
    
    public List<MemberPayoutDTO> getMemberPayouts(Long cycleId) {
        SavingsCycle cycle = savingsCycleRepository.findById(cycleId)
            .orElseThrow(() -> new ResourceNotFoundException("Savings cycle not found"));
        
        return memberPayoutRepository.findBySavingsCycle(cycle)
            .stream()
            .map(this::convertToPayoutDTO)
            .collect(Collectors.toList());
    }
    
    public void markPayoutAsPaid(Long payoutId) {
        MemberPayout payout = memberPayoutRepository.findById(payoutId)
            .orElseThrow(() -> new ResourceNotFoundException("Payout not found"));
        
        payout.setStatus(PayoutStatus.PAID);
        payout.setPaidAt(LocalDate.now());
        
        // Update cycle distributed total
        SavingsCycle cycle = payout.getSavingsCycle();
        cycle.setTotalUbwizigameDistributed(
            Money.add(cycle.getTotalUbwizigameDistributed(), payout.getPayoutAmount())
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
