package com.example.ikimina.repository;

import com.example.ikimina.model.SavingsGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SavingsGroupRepository extends JpaRepository<SavingsGroup, Long> {
    Optional<SavingsGroup> findByName(String name);
    boolean existsByName(String name);
}
