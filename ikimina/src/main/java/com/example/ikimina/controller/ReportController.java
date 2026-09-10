package com.example.ikimina.controller;

import java.util.List;

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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.ikimina.dto.ReportDTO;
import com.example.ikimina.enums.Period;
import com.example.ikimina.service.ReportService;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    @Autowired
    private ReportService reportService;

    @PostMapping("/generate/{userId}")
    @PreAuthorize("@userSecurity.hasAccessToUser(authentication, #userId)")
    public ResponseEntity<ReportDTO> generateReport(
            @PathVariable Long userId,
            @RequestParam Period period) {
        return ResponseEntity.ok(reportService.generateReport(userId, period));
    }

    @GetMapping("/user/{userId}")
    @PreAuthorize("@userSecurity.hasAccessToUser(authentication, #userId)")
    public ResponseEntity<Page<ReportDTO>> getUserReports(
            @PathVariable Long userId,
            @PageableDefault(size = 25, sort = "generatedOn", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(reportService.getUserReportsPaged(userId, pageable));
    }

    @GetMapping("/user/{userId}/period")
    @PreAuthorize("@userSecurity.hasAccessToUser(authentication, #userId)")
    public ResponseEntity<List<ReportDTO>> getUserReportsByPeriod(
            @PathVariable Long userId,
            @RequestParam Period period) {
        return ResponseEntity.ok(reportService.getUserReportsByPeriod(userId, period));
    }
}
