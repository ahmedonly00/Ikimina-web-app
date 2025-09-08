package com.example.ikimina.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.ikimina.dto.ReportDTO;
import com.example.ikimina.model.Reports.Period;
import com.example.ikimina.service.ReportService;

@RestController
@RequestMapping("/api/reports")
@CrossOrigin(origins = "http://localhost:3000")     
public class ReportController {
    
    @Autowired
    private ReportService reportService;
    
    @PostMapping("/generate/{userId}")
    public ResponseEntity<ReportDTO> generateReport(
            @PathVariable Long userId,
            @RequestParam Period period) {
        return ResponseEntity.ok(reportService.generateReport(userId, period));
    }
    
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<ReportDTO>> getUserReports(@PathVariable Long userId) {
        return ResponseEntity.ok(reportService.getUserReports(userId));
    }
    
    @GetMapping("/user/{userId}/period")
    public ResponseEntity<List<ReportDTO>> getUserReportsByPeriod(
            @PathVariable Long userId,
            @RequestParam Period period) {
        return ResponseEntity.ok(reportService.getUserReportsByPeriod(userId, period));
    }
} 