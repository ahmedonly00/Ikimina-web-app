package com.example.ikimina.service;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.ikimina.dto.SavingsDTO;
import com.example.ikimina.model.Savings;
import com.example.ikimina.model.User;
import com.example.ikimina.repository.SavingsRepository;
import com.example.ikimina.repository.UserRepository;

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
} 