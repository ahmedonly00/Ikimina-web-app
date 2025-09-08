package com.example.ikimina.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.ikimina.dto.FineDTO;
import com.example.ikimina.service.FineService;

@RestController
@RequestMapping("/api/fines")
@CrossOrigin(origins = "http://localhost:3000") 
public class FineController {
    
    @Autowired
    private FineService fineService;
    
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<FineDTO> createFine(@RequestBody FineDTO fineDTO) {
        return ResponseEntity.ok(fineService.createFine(fineDTO));
    }
    
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<FineDTO>> getUserFines(@PathVariable Long userId) {
        return ResponseEntity.ok(fineService.getUserFines(userId));
    }
    
    @GetMapping("/user/{userId}/total")
    public ResponseEntity<Double> getUserTotalFines(
            @PathVariable Long userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(fineService.getUserTotalFinesBetweenDates(userId, startDate, endDate));
    }
} 