package com.example.ikimina.dto;

import java.time.LocalDate;

import com.example.ikimina.model.Reports.Period;

public class ReportDTO {
    private Long id;
    private Period period;
    private LocalDate fromDate;
    private LocalDate toDate;
    private Double totalSavings;
    private Double totalFines;
    private LocalDate generatedOn;
    private Long userId;

    public ReportDTO() {
    }

    public ReportDTO(Long id, Period period, LocalDate fromDate, LocalDate toDate, Double totalSavings,
            Double totalFines, LocalDate generatedOn, Long userId) {
        this.id = id;
        this.period = period;
        this.fromDate = fromDate;
        this.toDate = toDate;
        this.totalSavings = totalSavings;
        this.totalFines = totalFines;
        this.generatedOn = generatedOn;
        this.userId = userId;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Period getPeriod() {
        return period;
    }

    public void setPeriod(Period period) {
        this.period = period;
    }

    public LocalDate getFromDate() {
        return fromDate;
    }

    public void setFromDate(LocalDate fromDate) {
        this.fromDate = fromDate;
    }

    public LocalDate getToDate() {
        return toDate;
    }

    public void setToDate(LocalDate toDate) {
        this.toDate = toDate;
    }

    public Double getTotalSavings() {
        return totalSavings;
    }

    public void setTotalSavings(Double totalSavings) {
        this.totalSavings = totalSavings;
    }

    public Double getTotalFines() {
        return totalFines;
    }

    public void setTotalFines(Double totalFines) {
        this.totalFines = totalFines;
    }

    public LocalDate getGeneratedOn() {
        return generatedOn;
    }

    public void setGeneratedOn(LocalDate generatedOn) {
        this.generatedOn = generatedOn;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }
} 