package rw.ikimina.groups.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SettingsChangeRequestRepository extends JpaRepository<SettingsChangeRequest, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from SettingsChangeRequest c where c.groupId = :groupId and c.publicId = :publicId")
    Optional<SettingsChangeRequest> findByGroupIdAndPublicIdForUpdate(@Param("groupId") Long groupId,
                                                                      @Param("publicId") UUID publicId);

    List<SettingsChangeRequest> findByGroupIdAndStatus(Long groupId, SettingsChangeRequest.Status status);
}
