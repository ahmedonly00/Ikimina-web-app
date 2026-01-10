package com.example.ikimina.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.HashMap;
import java.util.ArrayList;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.ikimina.dto.SavingsDTO;
import com.example.ikimina.dto.BulkSavingsEntryDTO;
import com.example.ikimina.dto.MemberSavingsLedgerDTO;
import com.example.ikimina.model.Savings;
import com.example.ikimina.model.User;
import com.example.ikimina.repository.SavingsRepository;
import com.example.ikimina.repository.UserRepository;
import com.example.ikimina.enums.SavingsType;

@Service
public class SavingsService {
    
    @Autowired
    private SavingsRepository savingsRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    public SavingsDTO createSavings(SavingsDTO savingsDTO) {
        User user = userRepository.findById(savingsDTO.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        Savings savings = new Savings();
        savings.setAmount(savingsDTO.getAmount());
        savings.setType(savingsDTO.getType());
        savings.setDate(savingsDTO.getDate() != null ? savingsDTO.getDate() : LocalDate.now());
        savings.setUser(user);
        
        Savings savedSavings = savingsRepository.save(savings);
        return convertToDTO(savedSavings);
    }
    
    public List<SavingsDTO> getUserSavings(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        return savingsRepository.findByUser(user).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }
    
    public Double getTotalSavingsForDate(LocalDate date) {
        return savingsRepository.getTotalSavingsForDate(date);
    }
    
    public Double getUserTotalSavingsBetweenDates(Long userId, LocalDate startDate, LocalDate endDate) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        return savingsRepository.getTotalSavingsForUserBetweenDates(user, startDate, endDate);
    }
    
    public List<SavingsDTO> getUserSavingsBetweenDates(Long userId, LocalDate startDate, LocalDate endDate) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        return savingsRepository.findByUserAndDateBetween(user, startDate, endDate).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }
    
    private SavingsDTO convertToDTO(Savings savings) {
        return new SavingsDTO(
                savings.getId(),
                savings.getAmount(),
                savings.getType(),
                savings.getDate(),
                savings.getUser().getId()
        );
    }
    
    public List<SavingsDTO> createBulkSavings(BulkSavingsEntryDTO bulkEntry) {
        List<Savings> savingsList = new ArrayList<>();
        
        for (BulkSavingsEntryDTO.MemberSavingsDTO memberSavings : bulkEntry.getMembers()) {
            User user = userRepository.findById(memberSavings.getUserId())
                    .orElseThrow(() -> new RuntimeException("User not found: " + memberSavings.getUserId()));
            
            if (memberSavings.getUbwizigameAmount() != null && memberSavings.getUbwizigameAmount() > 0) {
                Savings ubwizigame = new Savings();
                ubwizigame.setAmount(memberSavings.getUbwizigameAmount());
                ubwizigame.setType(SavingsType.UBWIZIGAME);
                ubwizigame.setDate(bulkEntry.getDate());
                ubwizigame.setUser(user);
                savingsList.add(ubwizigame);
            }
            
            if (memberSavings.getIngobokaAmount() != null && memberSavings.getIngobokaAmount() > 0) {
                Savings ingoboka = new Savings();
                ingoboka.setAmount(memberSavings.getIngobokaAmount());
                ingoboka.setType(SavingsType.INGOBOKA);
                ingoboka.setDate(bulkEntry.getDate());
                ingoboka.setUser(user);
                savingsList.add(ingoboka);
            }
        }
        
        List<Savings> savedSavings = savingsRepository.saveAll(savingsList);
        return savedSavings.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }
    
    public List<MemberSavingsLedgerDTO> getMemberSavingsLedger(List<Long> userIds, LocalDate startDate, LocalDate endDate) {
        List<Savings> savings = savingsRepository.findByUserIdsAndDateBetween(userIds, startDate, endDate);
        
        Map<Long, MemberSavingsLedgerDTO> ledgerMap = new HashMap<>();
        
        for (Savings saving : savings) {
            Long userId = saving.getUser().getId();
            String userName = saving.getUser().getFirstName() + " " + saving.getUser().getLastName();
            
            MemberSavingsLedgerDTO ledger = ledgerMap.computeIfAbsent(userId, id -> {
                MemberSavingsLedgerDTO dto = new MemberSavingsLedgerDTO();
                dto.setMemberId(id);
                dto.setMemberName(userName);
                dto.setDailySavings(new HashMap<>());
                return dto;
            });
            
            MemberSavingsLedgerDTO.DailySavingsDTO dailySavings = 
                ledger.getDailySavings().computeIfAbsent(saving.getDate(), date -> {
                    MemberSavingsLedgerDTO.DailySavingsDTO dto = new MemberSavingsLedgerDTO.DailySavingsDTO();
                    dto.setUbwizigame(0.0);
                    dto.setIngoboka(0.0);
                    return dto;
                });
            
            if (saving.getType() == SavingsType.UBWIZIGAME) {
                dailySavings.setUbwizigame(saving.getAmount());
            } else if (saving.getType() == SavingsType.INGOBOKA) {
                dailySavings.setIngoboka(saving.getAmount());
            }
        }
        
        return new ArrayList<>(ledgerMap.values());
    }
}