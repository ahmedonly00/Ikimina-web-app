package com.example.ikimina.dto;

import java.time.LocalDate;

import com.example.ikimina.model.Loans.LoanStatus;

public class LoanDTO {
    private Long id;
    private Double amount;
    private Double interestRate;
    private LocalDate requestDate;
    private LocalDate dueDate;
    private LoanStatus status;
    private Long userId;

    public LoanDTO() {
    }

    public LoanDTO(Long id, Double amount, Double interestRate, LocalDate requestDate, LocalDate dueDate,
            LoanStatus status, Long userId) {
        this.id = id;
        this.amount = amount;
        this.interestRate = interestRate;
        this.requestDate = requestDate;
        this.dueDate = dueDate;
        this.status = status;
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

    public Double getInterestRate() {
        return interestRate;
    }

    public void setInterestRate(Double interestRate) {
        this.interestRate = interestRate;
    }

    public LocalDate getRequestDate() {
        return requestDate;
    }

    public void setRequestDate(LocalDate requestDate) {
        this.requestDate = requestDate;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public LoanStatus getStatus() {
        return status;
    }

    public void setStatus(LoanStatus status) {
        this.status = status;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }
}
