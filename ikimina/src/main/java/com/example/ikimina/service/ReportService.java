package com.example.ikimina.service;

import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.ikimina.dto.ReportDTO;
import com.example.ikimina.model.Reports;
import com.example.ikimina.model.User;
import com.example.ikimina.enums.Period;
import com.example.ikimina.repository.ReportRepository;
import com.example.ikimina.repository.UserRepository;

@Service
public class ReportService {
    
    @Autowired
    private ReportRepository reportRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private SavingsService savingsService;
    
    @Autowired
    private FineService fineService;
    
    public ReportDTO generateReport(Long userId, Period period) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        LocalDate now = LocalDate.now();
        LocalDate startDate;
        LocalDate endDate;
        
        if (period == Period.WEEKLY) {
            startDate = now.minusWeeks(1);
            endDate = now;
        } else { // MONTHLY
            startDate = now.minusMonths(1).with(TemporalAdjusters.firstDayOfMonth());
            endDate = now.with(TemporalAdjusters.lastDayOfMonth());
        }
        
        Double totalSavings = savingsService.getUserTotalSavingsBetweenDates(userId, startDate, endDate);
        Double totalFines = fineService.getUserTotalFinesBetweenDates(userId, startDate, endDate);
        
        Reports report = new Reports();
        report.setPeriod(period);
        report.setFromDate(startDate);
        report.setToDate(endDate);
        report.setTotalSavings(totalSavings != null ? totalSavings : 0.0);
        report.setTotalFines(totalFines != null ? totalFines : 0.0);
        report.setGeneratedOn(now);
        report.setUser(user);
        
        Reports savedReport = reportRepository.save(report);
        return convertToDTO(savedReport);
    }
    
    public List<ReportDTO> getUserReports(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        return reportRepository.findByUser(user).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }
    
    public List<ReportDTO> getUserReportsByPeriod(Long userId, Period period) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        return reportRepository.findByUserAndPeriod(user, period).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }
    
    private ReportDTO convertToDTO(Reports report) {
        return new ReportDTO(
                report.getId(),
                report.getPeriod(),
                report.getFromDate(),
                report.getToDate(),
                report.getTotalSavings(),
                report.getTotalFines(),
                report.getGeneratedOn(),
                report.getUser().getId()
        );
    }
}