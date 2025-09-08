package com.example.ikimina.service;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.ikimina.dto.LoanDTO;
import com.example.ikimina.model.Loans;
import com.example.ikimina.model.User;
import com.example.ikimina.model.Loans.LoanStatus;
import com.example.ikimina.repository.LoanRepository;
import com.example.ikimina.repository.UserRepository;

@Service
public class LoanService {
    
    @Autowired
    private LoanRepository loanRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private FineService fineService;
    
    public LoanDTO requestLoan(LoanDTO loanDTO) {
        User user = userRepository.findById(loanDTO.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        Loans loan = new Loans();
        loan.setAmount(loanDTO.getAmount());
        loan.setInterestRate(loanDTO.getInterestRate());
        loan.setRequestDate(LocalDate.now());
        loan.setDueDate(loanDTO.getDueDate());
        loan.setStatus(LoanStatus.PENDING);
        loan.setUser(user);
        
        Loans savedLoan = loanRepository.save(loan);
        return convertToDTO(savedLoan);
    }
    
    public LoanDTO updateLoanStatus(Long loanId, LoanStatus status) {
        Loans loan = loanRepository.findById(loanId)
                .orElseThrow(() -> new RuntimeException("Loan not found"));
        
        loan.setStatus(status);
        Loans updatedLoan = loanRepository.save(loan);
        return convertToDTO(updatedLoan);
    }
    
    public List<LoanDTO> getUserLoans(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        return loanRepository.findByUser(user).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }
    
    public List<LoanDTO> getOverdueLoans() {
        return loanRepository.findOverdueLoans(LocalDate.now(), LoanStatus.APPROVED).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }
    
    public void checkAndUpdateOverdueLoans() {
        List<Loans> overdueLoans = loanRepository.findByDueDateBeforeAndStatus(LocalDate.now(), LoanStatus.APPROVED);
        
        for (Loans loan : overdueLoans) {
            loan.setStatus(LoanStatus.OVERDUE);
            loanRepository.save(loan);
            
            // Create fine for overdue loan
            fineService.createFineForOverdueLoan(loan);
        }
    }
    
    private LoanDTO convertToDTO(Loans loan) {
        return new LoanDTO(
                loan.getId(),
                loan.getAmount(),
                loan.getInterestRate(),
                loan.getRequestDate(),
                loan.getDueDate(),
                loan.getStatus(),
                loan.getUser().getId()
        );
    }
} 