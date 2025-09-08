package com.example.ikimina.model;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "reports")
public class Reports {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Period period;

    @Column(nullable = false)
    private LocalDate fromDate;

    @Column(nullable = false)
    private LocalDate toDate;

    @Column(nullable = false)
    private Double totalSavings;

    @Column(nullable = false)
    private Double totalFines;

    @Column(nullable = false)
    private LocalDate generatedOn;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    public enum Period {
        WEEKLY,
        MONTHLY,
    }

    public Reports() {
    }

    public Reports(Long id, Period period, LocalDate fromDate, LocalDate toDate, Double totalSavings, Double totalFines,
            LocalDate generatedOn, User user) {
        this.id = id;
        this.period = period;
        this.fromDate = fromDate;
        this.toDate = toDate;
        this.totalSavings = totalSavings;
        this.totalFines = totalFines;
        this.generatedOn = generatedOn;
        this.user = user;
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

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }
    
}
