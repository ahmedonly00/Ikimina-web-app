package com.example.ikimina.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.ikimina.model.GroupSubscription;
import com.example.ikimina.model.GroupSubscription.SubscriptionStatus;
import com.example.ikimina.model.SavingsGroup;

@Repository
public interface GroupSubscriptionRepository extends JpaRepository<GroupSubscription, Long> {
    
    Optional<GroupSubscription> findByGroup(SavingsGroup group);
    
    List<GroupSubscription> findByStatus(SubscriptionStatus status);
    
    @Query("SELECT gs FROM GroupSubscription gs WHERE gs.status = 'GRACE_PERIOD' AND gs.gracePeriodEnd < :date")
    List<GroupSubscription> findExpiredGracePeriod(@Param("date") LocalDate date);
    
    @Query("SELECT gs FROM GroupSubscription gs WHERE gs.status = 'ACTIVE' AND gs.endDate < :date")
    List<GroupSubscription> findExpiredActiveSubscriptions(@Param("date") LocalDate date);
    
    @Query("SELECT gs FROM GroupSubscription gs WHERE gs.group.isSuspended = false AND (gs.status = 'ACTIVE' OR gs.status = 'GRACE_PERIOD')")
    List<GroupSubscription> findActiveSubscriptions();
    
    @Query("SELECT COUNT(gs) FROM GroupSubscription gs WHERE gs.status = :status")
    Long countByStatus(@Param("status") SubscriptionStatus status);
}
