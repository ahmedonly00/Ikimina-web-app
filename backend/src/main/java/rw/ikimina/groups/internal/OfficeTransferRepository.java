package rw.ikimina.groups.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import rw.ikimina.groups.GroupRole;

public interface OfficeTransferRepository extends JpaRepository<OfficeTransfer, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from OfficeTransfer t where t.groupId = :groupId and t.publicId = :publicId")
    Optional<OfficeTransfer> findByGroupIdAndPublicIdForUpdate(@Param("groupId") Long groupId, @Param("publicId") UUID publicId);

    Optional<OfficeTransfer> findByGroupIdAndRoleAndStatus(Long groupId, GroupRole role, OfficeTransfer.Status status);

    List<OfficeTransfer> findByGroupIdAndStatusOrderByCreatedAtDesc(Long groupId, OfficeTransfer.Status status);
}
