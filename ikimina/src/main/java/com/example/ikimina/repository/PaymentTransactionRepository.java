package com.example.ikimina.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.ikimina.model.PaymentTransaction;
import com.example.ikimina.model.PaymentTransaction.PaymentStatus;
import com.example.ikimina.model.SavingsGroup;

@Repository
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {
    
    Optional<PaymentTransaction> findByTransactionId(String transactionId);
    
    List<PaymentTransaction> findByGroup(SavingsGroup group);
    
    List<PaymentTransaction> findByStatus(PaymentStatus status);
    
    @Query("SELECT pt FROM PaymentTransaction pt WHERE pt.group.id = :groupId ORDER BY pt.paymentDate DESC")
    List<PaymentTransaction> findByGroupIdOrderByPaymentDateDesc(@Param("groupId") Long groupId);
    
    @Query("SELECT pt FROM PaymentTransaction pt WHERE pt.paymentDate BETWEEN :start AND :end")
    List<PaymentTransaction> findByPaymentDateBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
    
    @Query("SELECT COUNT(pt) FROM PaymentTransaction pt WHERE pt.status = 'COMPLETED' AND pt.paymentDate BETWEEN :start AND :end")
    Long countCompletedPaymentsBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
