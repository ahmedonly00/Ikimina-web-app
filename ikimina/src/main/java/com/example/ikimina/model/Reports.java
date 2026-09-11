package com.example.ikimina.model;

import java.math.BigDecimal;

import java.time.LocalDate;

import com.example.ikimina.enums.Period;

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
import lombok.*;

@Entity
@Table(name = "reports")
@Data
@NoArgsConstructor
@AllArgsConstructor
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

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal totalSavings;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal totalFines;

    @Column(nullable = false)
    private LocalDate generatedOn;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;  

    
}
