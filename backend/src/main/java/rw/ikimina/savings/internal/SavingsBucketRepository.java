package rw.ikimina.savings.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SavingsBucketRepository extends JpaRepository<SavingsBucket, Long> {

    List<SavingsBucket> findByGroupIdOrderByName(Long groupId);

    Optional<SavingsBucket> findByGroupIdAndPublicId(Long groupId, UUID publicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from SavingsBucket b where b.groupId = :groupId and b.publicId = :publicId")
    Optional<SavingsBucket> findByGroupIdAndPublicIdForUpdate(@Param("groupId") Long groupId, @Param("publicId") UUID publicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from SavingsBucket b where b.groupId = :groupId and b.id = :id")
    Optional<SavingsBucket> findByGroupIdAndIdForUpdate(@Param("groupId") Long groupId, @Param("id") Long id);

    boolean existsByGroupIdAndNameIgnoreCase(Long groupId, String name);

    List<SavingsBucket> findByGroupIdAndStatus(Long groupId, SavingsBucket.Status status);
}
