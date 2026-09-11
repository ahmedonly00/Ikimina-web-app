package com.example.ikimina.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.ikimina.model.AuditLog;

/**
 * The audit table grows fastest of anything here - one row per money movement -
 * so every read path that a human can trigger is paged.
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    Page<AuditLog> findByEntityTypeOrderByCreatedAtDesc(String entityType, Pageable pageable);

    Page<AuditLog> findByModuleOrderByCreatedAtDesc(String module, Pageable pageable);

    Page<AuditLog> findByPerformedByOrderByCreatedAtDesc(Long performedBy, Pageable pageable);

    Page<AuditLog> findByCreatedAtAfterOrderByCreatedAtDesc(LocalDateTime since, Pageable pageable);

    Page<AuditLog> findByEntityTypeAndCreatedAtAfterOrderByCreatedAtDesc(
            String entityType, LocalDateTime since, Pageable pageable);

    Page<AuditLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /** Full history for one entity. Bounded by the entity, so a list is safe. */
    List<AuditLog> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(String entityType, Long entityId);

    @Query("SELECT al FROM AuditLog al WHERE al.createdAt BETWEEN :start AND :end ORDER BY al.createdAt DESC")
    Page<AuditLog> findByCreatedAtBetween(@Param("start") LocalDateTime start,
                                          @Param("end") LocalDateTime end,
                                          Pageable pageable);
}