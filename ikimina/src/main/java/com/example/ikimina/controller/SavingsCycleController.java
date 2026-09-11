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
public class SavingsCycleController {
    
    @Autowired
    private SavingsCycleService savingsCycleService;
    
    @PostMapping("/groups/{groupId}/start")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','GROUP_ADMIN')")
    public ResponseEntity<SavingsCycleDTO> startNewCycle(@PathVariable Long groupId) {
        SavingsCycleDTO cycle = savingsCycleService.createNewCycle(groupId);
        return ResponseEntity.ok(cycle);
    }
    
    @PostMapping("/{cycleId}/calculate-payouts")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','GROUP_ADMIN')")
    public ResponseEntity<SavingsCycleDTO> calculatePayouts(@PathVariable Long cycleId) {
        SavingsCycleDTO cycle = savingsCycleService.calculateCyclePayouts(cycleId);
        return ResponseEntity.ok(cycle);
    }
    
    @GetMapping("/groups/{groupId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','GROUP_ADMIN')")
    public ResponseEntity<List<SavingsCycleDTO>> getCyclesByGroup(@PathVariable Long groupId) {
        List<SavingsCycleDTO> cycles = savingsCycleService.getCyclesByGroup(groupId);
        return ResponseEntity.ok(cycles);
    }
    
    @GetMapping("/groups/{groupId}/current")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','GROUP_ADMIN')")
    public ResponseEntity<SavingsCycleDTO> getCurrentCycle(@PathVariable Long groupId) {
        SavingsCycleDTO cycle = savingsCycleService.getCurrentCycle(groupId);
        // A group that has not started a cycle is an ordinary state, not a
        // missing resource. Answering 404 made every client treat "no cycle
        // yet" as a failure - the savings distribution screen logged a console
        // error on first visit for any new group. 204 says "nothing to return"
        // without claiming the group does not exist; the group itself is still
        // resolved above, so a bad groupId is still a genuine 404.
        return cycle != null ? ResponseEntity.ok(cycle) : ResponseEntity.noContent().build();
    }
    
    @GetMapping("/{cycleId}/payouts")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','GROUP_ADMIN')")
    public ResponseEntity<List<MemberPayoutDTO>> getPayouts(@PathVariable Long cycleId) {
        List<MemberPayoutDTO> payouts = savingsCycleService.getMemberPayouts(cycleId);
        return ResponseEntity.ok(payouts);
    }
    
    @PostMapping("/payouts/{payoutId}/mark-paid")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','GROUP_ADMIN')")
    public ResponseEntity<Void> markPayoutAsPaid(@PathVariable Long payoutId) {
        savingsCycleService.markPayoutAsPaid(payoutId);
        return ResponseEntity.ok().build();
    }
}
