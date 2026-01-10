package com.example.ikimina.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.ikimina.model.MemberPayout;
import com.example.ikimina.model.SavingsCycle;
import com.example.ikimina.model.User;

@Repository
public interface MemberPayoutRepository extends JpaRepository<MemberPayout, Long> {
    
    List<MemberPayout> findBySavingsCycle(SavingsCycle savingsCycle);
    
    Optional<MemberPayout> findBySavingsCycleAndMember(SavingsCycle savingsCycle, User member);
    
    @Query("SELECT mp FROM MemberPayout mp WHERE mp.savingsCycle.id = :cycleId AND mp.status = 'PENDING'")
    List<MemberPayout> findPendingPayoutsByCycle(@Param("cycleId") Long cycleId);
    
    @Query("SELECT mp FROM MemberPayout mp WHERE mp.member.id = :memberId ORDER BY mp.savingsCycle.startDate DESC")
    List<MemberPayout> findByMemberOrderByCycleDesc(@Param("memberId") Long memberId);
}
