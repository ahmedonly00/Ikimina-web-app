package com.example.ikimina.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.example.ikimina.model.Loans;
import com.example.ikimina.model.User;
import com.example.ikimina.enums.LoanStatus;

@Repository
public interface LoanRepository extends JpaRepository<Loans, Long> {
    List<Loans> findByUser(User user);
    List<Loans> findByUserAndStatus(User user, LoanStatus status);
    List<Loans> findByDueDateBeforeAndStatus(LocalDate date, LoanStatus status);
    
    @Query("SELECT l FROM Loans l WHERE l.dueDate < :currentDate AND l.status = :status")
    List<Loans> findOverdueLoans(LocalDate currentDate, LoanStatus status);
} 