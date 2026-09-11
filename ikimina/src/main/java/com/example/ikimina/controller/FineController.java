package com.example.ikimina.controller;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.ikimina.dto.FineDTO;
import com.example.ikimina.service.FineService;

@RestController
@RequestMapping("/api/fines")
public class FineController {

    @Autowired
    private FineService fineService;

    @PostMapping(value = "/create")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','GROUP_ADMIN')")
    public ResponseEntity<FineDTO> createFine(@Valid @RequestBody FineDTO fineDTO) {
        return ResponseEntity.ok(fineService.createFine(fineDTO));
    }

    @GetMapping(value = "/user/{userId}")
    @PreAuthorize("@userSecurity.hasAccessToUser(authentication, #userId)")
    public ResponseEntity<Page<FineDTO>> getUserFines(
            @PathVariable Long userId,
            @PageableDefault(size = 25, sort = "date", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(fineService.getUserFinesPaged(userId, pageable));
    }

    @GetMapping(value = "/user/{userId}/total")
    @PreAuthorize("@userSecurity.hasAccessToUser(authentication, #userId)")
    public ResponseEntity<BigDecimal> getUserTotalFines(
            @PathVariable Long userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(fineService.getUserTotalFinesBetweenDates(userId, startDate, endDate));
    }
}
