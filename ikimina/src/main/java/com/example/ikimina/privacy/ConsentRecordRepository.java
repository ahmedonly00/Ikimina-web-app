package com.example.ikimina.privacy;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConsentRecordRepository extends JpaRepository<ConsentRecord, Long> {

    List<ConsentRecord> findByUserIdOrderByRecordedAtDesc(Long userId);

    /**
     * The current state of one purpose: the most recent grant or withdrawal.
     * Consent is append-only, so "current" means "latest".
     */
    @Query("SELECT c FROM ConsentRecord c WHERE c.userId = :userId AND c.purpose = :purpose "
            + "ORDER BY c.recordedAt DESC, c.id DESC LIMIT 1")
    Optional<ConsentRecord> findCurrent(@Param("userId") Long userId,
                                        @Param("purpose") ConsentPurpose purpose);

    void deleteByUserId(Long userId);
}