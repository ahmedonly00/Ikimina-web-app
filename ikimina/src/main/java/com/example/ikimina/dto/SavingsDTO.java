package com.example.ikimina.dto;

import java.time.LocalDate;

import com.example.ikimina.model.Savings.SavingsType;

public class SavingsDTO {
    private Long id;
    private Double amount;
    private SavingsType type;
    private LocalDate date;
    private Long userId;

    public SavingsDTO() {
    }

    public SavingsDTO(Long id, Double amount, SavingsType type, LocalDate date, Long userId) {
        this.id = id;
        this.amount = amount;
        this.type = type;
        this.date = date;
        this.userId = userId;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Double getAmount() {
        return amount;
    }

    public void setAmount(Double amount) {
        this.amount = amount;
    }

    public SavingsType getType() {
        return type;
    }

    public void setType(SavingsType type) {
        this.type = type;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }
}
