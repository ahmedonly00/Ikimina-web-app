package rw.ikimina.savings.internal;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ObligationRepository extends JpaRepository<Obligation, Long> {

    Optional<Obligation> findByGroupIdAndPublicId(Long groupId, UUID publicId);

    /** Unsettled obligations of one member for one bucket, oldest first - the order payments settle them in. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select o from Obligation o
            where o.groupId = :groupId and o.membershipId = :membershipId and o.bucketId = :bucketId
              and o.status in (rw.ikimina.savings.internal.Obligation.Status.OPEN,
                               rw.ikimina.savings.internal.Obligation.Status.PARTIAL,
                               rw.ikimina.savings.internal.Obligation.Status.OVERDUE)
            order by o.periodStart""")
    List<Obligation> findUnsettledForUpdate(@Param("groupId") Long groupId, @Param("membershipId") Long membershipId,
                                            @Param("bucketId") Long bucketId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Obligation o where o.groupId = :groupId and o.id in :ids")
    List<Obligation> findByGroupIdAndIdInForUpdate(@Param("groupId") Long groupId, @Param("ids") Collection<Long> ids);

    @Query("""
            select o from Obligation o
            where o.groupId = :groupId
              and (:bucketId is null or o.bucketId = :bucketId)
              and (:membershipId is null or o.membershipId = :membershipId)
              and (:status is null or o.status = :status)""")
    Page<Obligation> search(@Param("groupId") Long groupId, @Param("bucketId") Long bucketId,
                            @Param("membershipId") Long membershipId, @Param("status") Obligation.Status status, Pageable pageable);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Obligation o set o.status = rw.ikimina.savings.internal.Obligation.Status.OVERDUE, o.version = o.version + 1
            where o.groupId = :groupId and o.dueDate < :today
              and o.status in (rw.ikimina.savings.internal.Obligation.Status.OPEN,
                               rw.ikimina.savings.internal.Obligation.Status.PARTIAL)""")
    int markOverdue(@Param("groupId") Long groupId, @Param("today") LocalDate today);
}
