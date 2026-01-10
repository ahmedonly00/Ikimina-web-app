package com.example.ikimina.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.ikimina.model.AuditLog;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    
    List<AuditLog> findByEntityType(String entityType);
    
    List<AuditLog> findByEntityTypeAndEntityId(String entityType, Long entityId);
    
    List<AuditLog> findByPerformedBy(Long performedBy);
    
    List<AuditLog> findByModule(String module);
    
    @Query("SELECT al FROM AuditLog al WHERE al.createdAt BETWEEN :start AND :end ORDER BY al.createdAt DESC")
    List<AuditLog> findByCreatedAtBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
    
    @Query("SELECT al FROM AuditLog al WHERE al.entityType = :entityType AND al.action = :action ORDER BY al.createdAt DESC")
    List<AuditLog> findByEntityTypeAndAction(@Param("entityType") String entityType, @Param("action") String action);
    
    @Query("SELECT al FROM AuditLog al WHERE al.performedBy = :userId AND al.createdAt >= :since ORDER BY al.createdAt DESC")
    List<AuditLog> findRecentByUser(@Param("userId") Long userId, @Param("since") LocalDateTime since);
}
