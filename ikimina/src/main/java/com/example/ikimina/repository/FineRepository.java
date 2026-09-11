package com.example.ikimina.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.ikimina.model.Fines;
import com.example.ikimina.model.User;

@Repository
public interface FineRepository extends JpaRepository<Fines, Long> {

    List<Fines> findByUser(User user);

    /** Paged variant for member-facing history, which grows without bound. */
    Page<Fines> findByUser(User user, Pageable pageable);

    List<Fines> findByUserAndDateBetween(User user, LocalDate startDate, LocalDate endDate);

    @Query("SELECT COALESCE(SUM(f.amount), 0) FROM Fines f "
            + "WHERE f.user = :user AND f.date BETWEEN :startDate AND :endDate")
    BigDecimal getTotalFinesForUserBetweenDates(@Param("user") User user,
                                                @Param("startDate") LocalDate startDate,
                                                @Param("endDate") LocalDate endDate);
}
