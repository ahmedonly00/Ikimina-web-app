package com.example.ikimina.service;
import java.math.BigDecimal;
import com.example.ikimina.money.Money;
import com.example.ikimina.exception.ResourceNotFoundException;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.example.ikimina.dto.FineDTO;
import com.example.ikimina.model.Fines;
import com.example.ikimina.model.Loans;
import com.example.ikimina.model.User;
import com.example.ikimina.repository.FineRepository;
import com.example.ikimina.repository.UserRepository;

import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class FineService {

    /** Late fee rate applied to an overdue loan principal. */
    private static final BigDecimal LATE_FEE_RATE = new BigDecimal("0.05");
    
    @Autowired
    private FineRepository fineRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    @Transactional
    public FineDTO createFine(FineDTO fineDTO) {
        User user = userRepository.findById(fineDTO.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        
        Fines fine = new Fines();
        fine.setReason(fineDTO.getReason());
        fine.setAmount(fineDTO.getAmount());
        fine.setDate(fineDTO.getDate() != null ? fineDTO.getDate() : LocalDate.now());
        fine.setUser(user);
        
        Fines savedFine = fineRepository.save(fine);
        return convertToDTO(savedFine);
    }
    
    @Transactional
    public void createFineForOverdueLoan(Loans loan) {
        Fines fine = new Fines();
        fine.setReason("Late loan payment");
        fine.setAmount(calculateLateFee(loan));
        fine.setDate(LocalDate.now());
        fine.setUser(loan.getUser());
        
        fineRepository.save(fine);
    }
    
    /** Late fee is 5% of the loan principal, rounded once at the end. */
    private BigDecimal calculateLateFee(Loans loan) {
        return Money.multiply(loan.getAmount(), LATE_FEE_RATE);
    }
    
    /** Paged history. Member histories grow without bound over a group's life. */
    public Page<FineDTO> getUserFinesPaged(Long userId, Pageable pageable) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return fineRepository.findByUser(user, pageable).map(this::convertToDTO);
    }

    public List<FineDTO> getUserFines(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        
        return fineRepository.findByUser(user).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }
    
    public BigDecimal getUserTotalFinesBetweenDates(Long userId, LocalDate startDate, LocalDate endDate) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        
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