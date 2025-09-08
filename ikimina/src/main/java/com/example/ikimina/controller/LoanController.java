package com.example.ikimina.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.ikimina.dto.LoanDTO;
import com.example.ikimina.model.Loans.LoanStatus;
import com.example.ikimina.service.LoanService;

@RestController
@RequestMapping("/api/loans")
@CrossOrigin(origins = "http://localhost:3000") 
public class LoanController {
    
    @Autowired
    private LoanService loanService;
    
    @PostMapping("/request")
    public ResponseEntity<LoanDTO> requestLoan(@RequestBody LoanDTO loanDTO) {
        return ResponseEntity.ok(loanService.requestLoan(loanDTO));
    }
    
    @PutMapping("/{loanId}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<LoanDTO> updateLoanStatus(
            @PathVariable Long loanId,
            @RequestBody LoanStatus status) {
        return ResponseEntity.ok(loanService.updateLoanStatus(loanId, status));
    }
    
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<LoanDTO>> getUserLoans(@PathVariable Long userId) {
        return ResponseEntity.ok(loanService.getUserLoans(userId));
    }
    
    @GetMapping("/overdue")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<LoanDTO>> getOverdueLoans() {
        return ResponseEntity.ok(loanService.getOverdueLoans());
    }
    
    @PostMapping("/check-overdue")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> checkAndUpdateOverdueLoans() {
        loanService.checkAndUpdateOverdueLoans();
        return ResponseEntity.ok().build();
    }
} 