package com.example.ikimina.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.example.ikimina.model.SubscriptionPlan;

@Repository
public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, Long> {
    
    List<SubscriptionPlan> findByIsActive(Boolean isActive);
    
    Optional<SubscriptionPlan> findByName(String name);
    
    @Query("SELECT sp FROM SubscriptionPlan sp WHERE sp.isActive = true ORDER BY sp.monthlyPrice ASC")
    List<SubscriptionPlan> findActivePlansOrderByPrice();
}
