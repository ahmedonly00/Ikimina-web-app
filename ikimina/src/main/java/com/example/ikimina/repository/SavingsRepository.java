package com.example.ikimina.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.example.ikimina.model.Savings;
import com.example.ikimina.model.User;

@Repository
public interface SavingsRepository extends JpaRepository<Savings, Long> {
    List<Savings> findByUser(User user);
    List<Savings> findByUserAndDateBetween(User user, LocalDate startDate, LocalDate endDate);
    
    @Query("SELECT SUM(s.amount) FROM Savings s WHERE s.date = :date")
    Double getTotalSavingsForDate(LocalDate date);
    
    @Query("SELECT SUM(s.amount) FROM Savings s WHERE s.user = :user AND s.date BETWEEN :startDate AND :endDate")
    Double getTotalSavingsForUserBetweenDates(User user, LocalDate startDate, LocalDate endDate);
} 