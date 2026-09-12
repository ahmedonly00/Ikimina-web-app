package com.example.ikimina.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.ikimina.enums.MembershipRequestStatus;
import com.example.ikimina.model.MembershipRequest;

@Repository
public interface MembershipRequestRepository extends JpaRepository<MembershipRequest, Long> {

    @Query("SELECT r FROM MembershipRequest r "
            + "JOIN FETCH r.user "
            + "WHERE r.savingsGroup.id = :groupId AND r.status = :status "
            + "ORDER BY r.requestedAt")
    List<MembershipRequest> findByGroupAndStatus(@Param("groupId") Long groupId,
                                                 @Param("status") MembershipRequestStatus status);

    Optional<MembershipRequest> findByUserIdAndSavingsGroupIdAndStatus(
            Long userId, Long savingsGroupId, MembershipRequestStatus status);

    long countBySavingsGroupIdAndStatus(Long savingsGroupId, MembershipRequestStatus status);
}
