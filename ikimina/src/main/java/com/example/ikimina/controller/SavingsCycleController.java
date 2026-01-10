package com.example.ikimina.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.example.ikimina.dto.MemberPayoutDTO;
import com.example.ikimina.dto.SavingsCycleDTO;
import com.example.ikimina.service.SavingsCycleService;

@RestController
@RequestMapping("/api/savings-cycles")
@CrossOrigin(origins = "*")
public class SavingsCycleController {
    
    @Autowired
    private SavingsCycleService savingsCycleService;
    
    @PostMapping("/groups/{groupId}/start")
    @PreAuthorize("hasRole('ROLE_GROUP_ADMIN') or hasRole('ROLE_SUPER_ADMIN')")
    public ResponseEntity<SavingsCycleDTO> startNewCycle(@PathVariable Long groupId) {
        SavingsCycleDTO cycle = savingsCycleService.createNewCycle(groupId);
        return ResponseEntity.ok(cycle);
    }
    
    @PostMapping("/{cycleId}/calculate-payouts")
    @PreAuthorize("hasRole('ROLE_GROUP_ADMIN') or hasRole('ROLE_SUPER_ADMIN')")
    public ResponseEntity<SavingsCycleDTO> calculatePayouts(@PathVariable Long cycleId) {
        SavingsCycleDTO cycle = savingsCycleService.calculateCyclePayouts(cycleId);
        return ResponseEntity.ok(cycle);
    }
    
    @GetMapping("/groups/{groupId}")
    @PreAuthorize("hasRole('ROLE_GROUP_ADMIN') or hasRole('ROLE_SUPER_ADMIN')")
    public ResponseEntity<List<SavingsCycleDTO>> getCyclesByGroup(@PathVariable Long groupId) {
        List<SavingsCycleDTO> cycles = savingsCycleService.getCyclesByGroup(groupId);
        return ResponseEntity.ok(cycles);
    }
    
    @GetMapping("/groups/{groupId}/current")
    @PreAuthorize("hasRole('ROLE_GROUP_ADMIN') or hasRole('ROLE_SUPER_ADMIN')")
    public ResponseEntity<SavingsCycleDTO> getCurrentCycle(@PathVariable Long groupId) {
        SavingsCycleDTO cycle = savingsCycleService.getCurrentCycle(groupId);
        return cycle != null ? ResponseEntity.ok(cycle) : ResponseEntity.notFound().build();
    }
    
    @GetMapping("/{cycleId}/payouts")
    @PreAuthorize("hasRole('ROLE_GROUP_ADMIN') or hasRole('ROLE_SUPER_ADMIN')")
    public ResponseEntity<List<MemberPayoutDTO>> getPayouts(@PathVariable Long cycleId) {
        List<MemberPayoutDTO> payouts = savingsCycleService.getMemberPayouts(cycleId);
        return ResponseEntity.ok(payouts);
    }
    
    @PostMapping("/payouts/{payoutId}/mark-paid")
    @PreAuthorize("hasRole('ROLE_GROUP_ADMIN') or hasRole('ROLE_SUPER_ADMIN')")
    public ResponseEntity<Void> markPayoutAsPaid(@PathVariable Long payoutId) {
        savingsCycleService.markPayoutAsPaid(payoutId);
        return ResponseEntity.ok().build();
    }
}
