package com.example.ikimina.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.ikimina.model.User;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    List<User> findBySavingsGroupId(Long savingsGroupId);
    Optional<User> findByEmailAndSavingsGroupId(String email, Long savingsGroupId);
    boolean existsByEmailAndSavingsGroupId(String email, Long savingsGroupId);
    
    boolean existsByIdAndSavingsGroupId(Long id, Long savingsGroupId);
    
    boolean existsByUsername(String username);
}