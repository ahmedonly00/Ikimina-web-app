package com.example.ikimina.service;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.ikimina.dto.FineDTO;
import com.example.ikimina.model.Fines;
import com.example.ikimina.model.Loans;
import com.example.ikimina.model.User;
import com.example.ikimina.repository.FineRepository;
import com.example.ikimina.repository.UserRepository;

@Service
public class FineService {
    
    @Autowired
    private FineRepository fineRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    public FineDTO createFine(FineDTO fineDTO) {
        User user = userRepository.findById(fineDTO.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        Fines fine = new Fines();
        fine.setReason(fineDTO.getReason());
        fine.setAmount(fineDTO.getAmount());
        fine.setDate(fineDTO.getDate() != null ? fineDTO.getDate() : LocalDate.now());
        fine.setUser(user);
        
        Fines savedFine = fineRepository.save(fine);
        return convertToDTO(savedFine);
    }
    
    public void createFineForOverdueLoan(Loans loan) {
        Fines fine = new Fines();
        fine.setReason("Late loan payment");
        fine.setAmount(calculateLateFee(loan));
        fine.setDate(LocalDate.now());
        fine.setUser(loan.getUser());
        
        fineRepository.save(fine);
    }
    
    private Double calculateLateFee(Loans loan) {
        // Calculate late fee as 5% of loan amount
        return loan.getAmount() * 0.05;
    }
    
    public List<FineDTO> getUserFines(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        return fineRepository.findByUser(user).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }
    
    public Double getUserTotalFinesBetweenDates(Long userId, LocalDate startDate, LocalDate endDate) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        return fineRepository.getTotalFinesForUserBetweenDates(user, startDate, endDate);
    }
    
    private FineDTO convertToDTO(Fines fine) {
        return new FineDTO(
                fine.getId(),
                fine.getReason(),
                fine.getAmount(),
                fine.getDate(),
                fine.getUser().getId()
        );
    }
} 