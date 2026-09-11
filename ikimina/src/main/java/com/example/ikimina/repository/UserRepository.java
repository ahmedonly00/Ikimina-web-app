package com.example.ikimina.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.ikimina.model.User;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    
    @Query("SELECT u FROM User u JOIN u.memberGroups g WHERE g.id = :groupId")
    List<User> findBySavingsGroupId(@Param("groupId") Long groupId);

    @Query("SELECT u FROM User u JOIN u.memberGroups g WHERE g.id = :groupId")
    Page<User> findBySavingsGroupId(@Param("groupId") Long groupId, Pageable pageable);
    
    @Query("SELECT u FROM User u JOIN u.memberGroups g WHERE u.email = :email AND g.id = :groupId")
    Optional<User> findByEmailAndSavingsGroupId(@Param("email") String email, @Param("groupId") Long groupId);
    
    @Query("SELECT COUNT(u) > 0 FROM User u JOIN u.memberGroups g WHERE u.email = :email AND g.id = :groupId")
    boolean existsByEmailAndSavingsGroupId(@Param("email") String email, @Param("groupId") Long groupId);
    
    @Query("SELECT COUNT(u) > 0 FROM User u JOIN u.memberGroups g WHERE u.id = :userId AND g.id = :groupId")
    boolean existsByIdAndSavingsGroupId(@Param("userId") Long userId, @Param("groupId") Long groupId);
    
    /**
     * Lowest group id the user belongs to, used as the active group when the
     * caller did not choose one. Queried rather than read from the lazy
     * memberGroups collection, which cannot initialise outside a transaction
     * (spring.jpa.open-in-view=false).
     */
    @Query("SELECT MIN(g.id) FROM User u JOIN u.memberGroups g WHERE u.id = :userId")
    Long findPrimaryGroupId(@Param("userId") Long userId);

    boolean existsByUsername(String username);
    
    List<User> findByActive(boolean active);

    Page<User> findByActive(boolean active, Pageable pageable);

    /**
     * Matches a member by the normalised subscriber number.
     *
     * Providers report numbers as +250788123456, 250788123456 or 0788123456, so
     * both sides are reduced to the last nine digits. The normalised form is a
     * stored, indexed column rather than a regex over phone_number: a function
     * over every row cannot use an index, and REGEXP_REPLACE is not portable
     * JPQL (it would break the H2-backed tests).
     *
     * Returns a list because more than one match means the caller must not guess.
     */
    List<User> findByPhoneNormalised(String phoneNormalised);

    /**
     * Active members of one group. The unscoped findByActive returns every user
     * in the system, which must never be handed to a group admin.
     */
    @Query("SELECT u FROM User u JOIN u.memberGroups g WHERE g.id = :groupId AND u.active = true")
    List<User> findActiveBySavingsGroupId(@Param("groupId") Long groupId);
}