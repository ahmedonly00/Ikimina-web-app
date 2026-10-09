package rw.ikimina.savings.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BucketChangeRequestRepository extends JpaRepository<BucketChangeRequest, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from BucketChangeRequest c where c.groupId = :groupId and c.publicId = :publicId")
    Optional<BucketChangeRequest> findByGroupIdAndPublicIdForUpdate(@Param("groupId") Long groupId, @Param("publicId") UUID publicId);

    List<BucketChangeRequest> findByGroupIdAndBucketIdAndStatus(Long groupId, Long bucketId, BucketChangeRequest.Status status);
}
