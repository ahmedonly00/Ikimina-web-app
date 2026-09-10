package com.example.ikimina.controller;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.ikimina.dto.LoanDTO;
import com.example.ikimina.enums.LoanStatus;
import com.example.ikimina.service.LoanService;

@RestController
@RequestMapping("/api/loans")
public class LoanController {

    @Autowired
    private LoanService loanService;

    @PostMapping("/request")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','GROUP_ADMIN') or @userSecurity.hasAccessToUser(authentication, #loanDTO.userId)")
    public ResponseEntity<LoanDTO> requestLoan(@Valid @RequestBody LoanDTO loanDTO) {
        return ResponseEntity.ok(loanService.requestLoan(loanDTO));
    }

    // Approving your own loan must not be possible: group admins and above only.
    @PutMapping("/{loanId}/status")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','GROUP_ADMIN')")
    public ResponseEntity<LoanDTO> updateLoanStatus(
            @PathVariable Long loanId,
            @RequestBody LoanStatus status) {
        return ResponseEntity.ok(loanService.updateLoanStatus(loanId, status));
    }

    @GetMapping("/user/{userId}")
    @PreAuthorize("@userSecurity.hasAccessToUser(authentication, #userId)")
    public ResponseEntity<Page<LoanDTO>> getUserLoans(
            @PathVariable Long userId,
            @PageableDefault(size = 25, sort = "requestDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(loanService.getUserLoansPaged(userId, pageable));
    }

    @GetMapping("/overdue")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','GROUP_ADMIN')")
    public ResponseEntity<List<LoanDTO>> getOverdueLoans() {
        return ResponseEntity.ok(loanService.getOverdueLoans());
    }

    @PostMapping("/check-overdue")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','GROUP_ADMIN')")
    public ResponseEntity<Void> checkAndUpdateOverdueLoans() {
        loanService.checkAndUpdateOverdueLoans();
        return ResponseEntity.ok().build();
    }
}
