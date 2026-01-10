package com.example.ikimina.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.ikimina.model.SavingsCycle;
import com.example.ikimina.model.SavingsGroup;

@Repository
public interface SavingsCycleRepository extends JpaRepository<SavingsCycle, Long> {
    
    List<SavingsCycle> findBySavingsGroupOrderByStartDateDesc(SavingsGroup savingsGroup);
    
    Optional<SavingsCycle> findBySavingsGroupAndStatus(SavingsGroup savingsGroup, SavingsCycle.CycleStatus status);
    
    @Query("SELECT sc FROM SavingsCycle sc WHERE sc.savingsGroup.id = :groupId AND sc.startDate <= :date AND sc.endDate >= :date")
    Optional<SavingsCycle> findActiveCycleForDate(@Param("groupId") Long groupId, @Param("date") LocalDate date);
    
    @Query("SELECT sc FROM SavingsCycle sc WHERE sc.status = 'COMPLETED' AND sc.endDate < :currentDate")
    List<SavingsCycle> findCompletedCyclesReadyForDistribution(@Param("currentDate") LocalDate currentDate);
}
