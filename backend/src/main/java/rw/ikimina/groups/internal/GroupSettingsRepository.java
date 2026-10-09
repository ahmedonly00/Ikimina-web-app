package rw.ikimina.groups.internal;

import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GroupSettingsRepository extends JpaRepository<GroupSettingsRow, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from GroupSettingsRow s where s.groupId = :groupId")
    Optional<GroupSettingsRow> findByGroupIdForUpdate(@Param("groupId") Long groupId);
}
