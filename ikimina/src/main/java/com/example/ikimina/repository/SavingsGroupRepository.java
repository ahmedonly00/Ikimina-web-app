package com.example.ikimina.repository;

import com.example.ikimina.model.SavingsGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SavingsGroupRepository extends JpaRepository<SavingsGroup, Long> {
    Optional<SavingsGroup> findByName(String name);

    boolean existsByName(String name);

    List<SavingsGroup> findByIsActiveTrue();

    /**
     * Resolve a group from an invite code.
     *
     * The code is the only input: nothing the caller says about which group
     * they want is consulted. Groups with no code set are excluded explicitly
     * so a blank or null submitted code can never match one.
     */
    @Query("SELECT g FROM SavingsGroup g WHERE g.joinCode IS NOT NULL AND g.joinCode = :code")
    Optional<SavingsGroup> findByJoinCode(@Param("code") String code);

    /** True when {@code adminId} administers a group that {@code memberId} belongs to. */
    @Query("SELECT COUNT(g) > 0 FROM SavingsGroup g JOIN g.members m "
            + "WHERE g.admin.id = :adminId AND m.id = :memberId")
    boolean isAdminOfGroupContainingUser(@Param("adminId") Long adminId,
                                        @Param("memberId") Long memberId);

    @Query("SELECT COUNT(g) > 0 FROM SavingsGroup g WHERE g.id = :groupId AND g.admin.id = :adminId")
    boolean isAdminOfGroup(@Param("adminId") Long adminId, @Param("groupId") Long groupId);
}
