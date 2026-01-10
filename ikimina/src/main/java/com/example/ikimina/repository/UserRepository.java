package com.example.ikimina.repository;

import java.util.List;
import java.util.Optional;

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
    
    @Query("SELECT u FROM User u JOIN u.memberGroups g WHERE u.email = :email AND g.id = :groupId")
    Optional<User> findByEmailAndSavingsGroupId(@Param("email") String email, @Param("groupId") Long groupId);
    
    @Query("SELECT COUNT(u) > 0 FROM User u JOIN u.memberGroups g WHERE u.email = :email AND g.id = :groupId")
    boolean existsByEmailAndSavingsGroupId(@Param("email") String email, @Param("groupId") Long groupId);
    
    @Query("SELECT COUNT(u) > 0 FROM User u JOIN u.memberGroups g WHERE u.id = :userId AND g.id = :groupId")
    boolean existsByIdAndSavingsGroupId(@Param("userId") Long userId, @Param("groupId") Long groupId);
    
    boolean existsByUsername(String username);
    
    List<User> findByActive(boolean active);
}