package com.example.ikimina.repository;

import com.example.ikimina.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RoleRepository extends JpaRepository<User.Role, Long> {
    Optional<User.Role> findByName(User.RoleType name);
}
