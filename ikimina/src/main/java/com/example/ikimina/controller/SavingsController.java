package com.example.ikimina.controller;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.ikimina.dto.SavingsDTO;
import com.example.ikimina.dto.BulkSavingsEntryDTO;
import com.example.ikimina.dto.MemberSavingsLedgerDTO;
import com.example.ikimina.service.SavingsService;

@RestController
@RequestMapping("/api/savings")
public class SavingsController {

    @Autowired
    private SavingsService savingsService;

    // Recording a contribution against another member is a group-admin action.
    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','GROUP_ADMIN') or @userSecurity.hasAccessToUser(authentication, #savingsDTO.userId)")
    public ResponseEntity<SavingsDTO> createSavings(
            @Valid @RequestBody SavingsDTO savingsDTO,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ResponseEntity.ok(savingsService.createSavings(savingsDTO, idempotencyKey));
    }

    @GetMapping("/user/{userId}")
    @PreAuthorize("@userSecurity.hasAccessToUser(authentication, #userId)")
    public ResponseEntity<Page<SavingsDTO>> getUserSavings(
            @PathVariable Long userId,
            @PageableDefault(size = 25, sort = "date", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(savingsService.getUserSavingsPaged(userId, pageable));
    }

    @GetMapping("/user/{userId}/total")
    @PreAuthorize("@userSecurity.hasAccessToUser(authentication, #userId)")
    public ResponseEntity<BigDecimal> getUserTotalSavings(
            @PathVariable Long userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(savingsService.getUserTotalSavingsBetweenDates(userId, startDate, endDate));
    }

    @GetMapping("/daily-total")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','GROUP_ADMIN')")
    public ResponseEntity<BigDecimal> getDailyTotalSavings(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(savingsService.getTotalSavingsForDate(date));
    }

    @PostMapping("/bulk")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','GROUP_ADMIN')")
    public ResponseEntity<List<SavingsDTO>> createBulkSavings(
            @Valid @RequestBody BulkSavingsEntryDTO bulkEntry,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ResponseEntity.ok(savingsService.createBulkSavings(bulkEntry, idempotencyKey));
    }

    @GetMapping("/ledger")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','GROUP_ADMIN') and @userSecurity.hasAccessToAllUsers(authentication, #userIds)")
    public ResponseEntity<List<MemberSavingsLedgerDTO>> getSavingsLedger(
            @RequestParam @NotEmpty List<Long> userIds,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(savingsService.getMemberSavingsLedger(userIds, startDate, endDate));
    }
}
