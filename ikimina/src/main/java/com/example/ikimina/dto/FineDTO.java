package com.example.ikimina.dto;

import java.time.LocalDate;

public class FineDTO {
    private Long id;
    private String reason;
    private Double amount;
    private LocalDate date;
    private Long userId;

    public FineDTO() {
    }

    public FineDTO(Long id, String reason, Double amount, LocalDate date, Long userId) {
        this.id = id;
        this.reason = reason;
        this.amount = amount;
        this.date = date;
        this.userId = userId;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Double getAmount() {
        return amount;
    }

    public void setAmount(Double amount) {
        this.amount = amount;
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
