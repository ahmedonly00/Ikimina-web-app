package com.example.ikimina.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.ikimina.model.Reports;
import com.example.ikimina.model.User;
import com.example.ikimina.enums.Period;

@Repository
public interface ReportRepository extends JpaRepository<Reports, Long> {
    List<Reports> findByUser(User user);

    /** Paged variant for member-facing history, which grows without bound. */
    Page<Reports> findByUser(User user, Pageable pageable);
    List<Reports> findByUserAndPeriod(User user, Period period);
    List<Reports> findByUserAndGeneratedOnBetween(User user, LocalDate startDate, LocalDate endDate);
} 