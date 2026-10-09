package rw.ikimina.savings.internal;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SavingsTransactionRepository extends JpaRepository<SavingsTransaction, Long> {

    Optional<SavingsTransaction> findByGroupIdAndJournalId(Long groupId, Long journalId);

    Page<SavingsTransaction> findByGroupIdAndMembershipId(Long groupId, Long membershipId, Pageable pageable);

    /** Spec 8.2: a reference already recorded (and not reversed) cannot be recorded again. */
    boolean existsByGroupIdAndExternalRefAndReversedAtIsNull(Long groupId, String externalRef);

    /** Live contributions of a member to a bucket, oldest first: the order their credit is spent in. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select t from SavingsTransaction t
            where t.groupId = :groupId and t.membershipId = :membershipId and t.bucketId = :bucketId
              and t.reversedAt is null and t.txnType = rw.ikimina.savings.internal.SavingsTransaction.Type.CONTRIBUTION
            order by t.businessDate, t.id""")
    List<SavingsTransaction> findLiveContributionsForUpdate(@Param("groupId") Long groupId,
                                                            @Param("membershipId") Long membershipId,
                                                            @Param("bucketId") Long bucketId);
}
