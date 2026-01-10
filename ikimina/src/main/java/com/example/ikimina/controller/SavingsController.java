package com.example.ikimina.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.ikimina.dto.SavingsDTO;
import com.example.ikimina.dto.BulkSavingsEntryDTO;
import com.example.ikimina.dto.MemberSavingsLedgerDTO;
import com.example.ikimina.service.SavingsService;

@RestController
@RequestMapping("/api/savings")
@CrossOrigin(origins = "*")     
public class SavingsController {
    
    @Autowired
    private SavingsService savingsService;
    
    @PostMapping
    public ResponseEntity<SavingsDTO> createSavings(@RequestBody SavingsDTO savingsDTO) {
        return ResponseEntity.ok(savingsService.createSavings(savingsDTO));
    }
    
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<SavingsDTO>> getUserSavings(@PathVariable Long userId) {
        return ResponseEntity.ok(savingsService.getUserSavings(userId));
    }
    
    @GetMapping("/user/{userId}/total")
    public ResponseEntity<Double> getUserTotalSavings(
            @PathVariable Long userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(savingsService.getUserTotalSavingsBetweenDates(userId, startDate, endDate));
    }
    
    @GetMapping("/daily-total")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Double> getDailyTotalSavings(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(savingsService.getTotalSavingsForDate(date));
    }
    
    @PostMapping("/bulk")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<SavingsDTO>> createBulkSavings(@RequestBody BulkSavingsEntryDTO bulkEntry) {
        return ResponseEntity.ok(savingsService.createBulkSavings(bulkEntry));
    }
    
    @GetMapping("/ledger")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<MemberSavingsLedgerDTO>> getSavingsLedger(
            @RequestParam List<Long> userIds,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(savingsService.getMemberSavingsLedger(userIds, startDate, endDate));
    }
}