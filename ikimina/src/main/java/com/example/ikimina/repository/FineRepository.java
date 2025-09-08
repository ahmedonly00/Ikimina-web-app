package com.example.ikimina.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.example.ikimina.model.Fines;
import com.example.ikimina.model.User;

@Repository
public interface FineRepository extends JpaRepository<Fines, Long> {
    List<Fines> findByUser(User user);
    List<Fines> findByUserAndDateBetween(User user, LocalDate startDate, LocalDate endDate);
    
    @Query("SELECT SUM(f.amount) FROM Fines f WHERE f.user = :user AND f.date BETWEEN :startDate AND :endDate")
    Double getTotalFinesForUserBetweenDates(User user, LocalDate startDate, LocalDate endDate);
} 