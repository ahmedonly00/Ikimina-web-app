package com.example.ikimina.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.example.ikimina.model.Savings;
import com.example.ikimina.model.User;
import com.example.ikimina.enums.SavingsType;

@Repository
public interface SavingsRepository extends JpaRepository<Savings, Long> {
    List<Savings> findByUser(User user);
    List<Savings> findByUserAndDateBetween(User user, LocalDate startDate, LocalDate endDate);
    List<Savings> findByUserIdAndDateBetween(Long userId, LocalDate startDate, LocalDate endDate);
    
    @Query("SELECT SUM(s.amount) FROM Savings s WHERE s.date = :date")
    Double getTotalSavingsForDate(LocalDate date);
    
    @Query("SELECT SUM(s.amount) FROM Savings s WHERE s.user = :user AND s.date BETWEEN :startDate AND :endDate")
    Double getTotalSavingsForUserBetweenDates(User user, LocalDate startDate, LocalDate endDate);
    
    @Query("SELECT s FROM Savings s WHERE s.user.id IN :userIds AND s.date BETWEEN :startDate AND :endDate ORDER BY s.user.id, s.date")
    List<Savings> findByUserIdsAndDateBetween(List<Long> userIds, LocalDate startDate, LocalDate endDate);
    
    @Query("SELECT s FROM Savings s WHERE s.date = :date AND s.user.id = :userId AND s.type = :type")
    Savings findByDateAndUserAndType(LocalDate date, Long userId, SavingsType type);
}