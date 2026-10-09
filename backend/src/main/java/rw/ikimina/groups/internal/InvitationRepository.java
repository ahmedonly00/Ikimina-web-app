package rw.ikimina.groups.internal;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InvitationRepository extends JpaRepository<Invitation, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Invitation i where i.groupId = :groupId and i.publicId = :publicId")
    Optional<Invitation> findByGroupIdAndPublicIdForUpdate(@Param("groupId") Long groupId, @Param("publicId") UUID publicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Invitation i where i.groupId = :groupId and i.tokenHash = :tokenHash")
    Optional<Invitation> findByGroupIdAndTokenHashForUpdate(@Param("groupId") Long groupId, @Param("tokenHash") String tokenHash);

    @Query("""
            select i from Invitation i
            where i.groupId = :groupId and i.phone = :phone and i.acceptedAt is null and i.revokedAt is null""")
    List<Invitation> findOpenForPhone(@Param("groupId") Long groupId, @Param("phone") String phone);

    @Query("""
            select i from Invitation i
            where i.groupId = :groupId and i.acceptedAt is null and i.revokedAt is null and i.expiresAt > :now
            order by i.createdAt desc""")
    List<Invitation> findPending(@Param("groupId") Long groupId, @Param("now") Instant now);
}
