package rw.ikimina.savings.internal;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SavingsWithdrawalRepository extends JpaRepository<SavingsWithdrawal, Long> {

    Optional<SavingsWithdrawal> findByGroupIdAndPublicId(Long groupId, UUID publicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from SavingsWithdrawal w where w.groupId = :groupId and w.publicId = :publicId")
    Optional<SavingsWithdrawal> findByGroupIdAndPublicIdForUpdate(@Param("groupId") Long groupId, @Param("publicId") UUID publicId);

    Optional<SavingsWithdrawal> findByGroupIdAndPayIdempotencyKey(Long groupId, String payIdempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from SavingsWithdrawal w where w.groupId = :groupId and w.transactionId = :transactionId")
    Optional<SavingsWithdrawal> findByGroupIdAndTransactionIdForUpdate(@Param("groupId") Long groupId,
                                                                       @Param("transactionId") Long transactionId);

    List<SavingsWithdrawal> findByGroupIdAndMembershipIdAndBucketIdAndStatusIn(Long groupId, Long membershipId, Long bucketId,
                                                                               Collection<SavingsWithdrawal.Status> statuses);

    List<SavingsWithdrawal> findByGroupIdAndStatus(Long groupId, SavingsWithdrawal.Status status);

    @Query("""
            select w from SavingsWithdrawal w
            where w.groupId = :groupId
              and (:member is null or w.membershipId = :member)
              and (:status is null or w.status = :status)""")
    Page<SavingsWithdrawal> search(@Param("groupId") Long groupId, @Param("member") Long member,
                                   @Param("status") SavingsWithdrawal.Status status, Pageable pageable);
}
