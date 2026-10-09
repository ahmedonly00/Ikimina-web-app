package rw.ikimina.groups.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import rw.ikimina.groups.GroupRole;

public interface MembershipRepository extends JpaRepository<Membership, Long> {

    Optional<Membership> findByGroupIdAndPublicId(Long groupId, UUID publicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Membership m where m.groupId = :groupId and m.publicId = :publicId")
    Optional<Membership> findByGroupIdAndPublicIdForUpdate(@Param("groupId") Long groupId, @Param("publicId") UUID publicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Membership m where m.groupId = :groupId and m.id = :id")
    Optional<Membership> findByGroupIdAndIdForUpdate(@Param("groupId") Long groupId, @Param("id") Long id);

    Optional<Membership> findByGroupIdAndUserId(Long groupId, Long userId);

    Optional<Membership> findByGroupIdAndRoleAndStatus(Long groupId, GroupRole role, Membership.Status status);

    Page<Membership> findByGroupId(Long groupId, Pageable pageable);

    long countByGroupId(Long groupId);

    List<Membership> findByGroupIdAndStatus(Long groupId, Membership.Status status, Sort sort);

    long countByGroupIdAndStatus(Long groupId, Membership.Status status);

    /** The caller's own memberships across groups (RLS lets a user see their own rows). */
    List<Membership> findByUserIdAndStatus(Long userId, Membership.Status status);
}
