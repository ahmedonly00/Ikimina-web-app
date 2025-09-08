package com.example.ikimina.controller;

import com.example.ikimina.dto.LoginRequest;
import com.example.ikimina.dto.UserDTO;
import com.example.ikimina.security.CustomUserDetails;
import com.example.ikimina.security.JwtTokenProvider;
import com.example.ikimina.service.CustomUserDetailsService;
import com.example.ikimina.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "http://localhost:3000")
public class AuthController {
    
    @Autowired
    private UserService userService;
    
    @Autowired
    private AuthenticationManager authenticationManager;
    
    @Autowired
    private JwtTokenProvider tokenProvider;
    
    @Autowired
    private CustomUserDetailsService userDetailsService;
    
    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@RequestBody UserDTO userDTO) {
        try {
            // Check if user already exists in this savings group
            if (userDTO.getSavingsGroupId() != null && 
                userService.existsByEmailAndSavingsGroupId(userDTO.getEmail(), userDTO.getSavingsGroupId())) {
                return ResponseEntity.badRequest().body("User with this email already exists in the specified savings group");
            }
            
            UserDTO createdUser = userService.createUser(userDTO);
            return ResponseEntity.ok(createdUser);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
    
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest loginRequest) {
        try {
            if (loginRequest.getSavingsGroupId() == null) {
                return ResponseEntity.badRequest().body("Savings group ID is required");
            }
            
            // Load user by email and savings group
            UserDetails userDetails = userDetailsService.loadUserByEmailAndSavingsGroup(
                loginRequest.getEmail(), 
                loginRequest.getSavingsGroupId()
            );
            
            // Authenticate
            Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                    loginRequest.getEmail(), 
                    loginRequest.getPassword()
                )
            );
            
            SecurityContextHolder.getContext().setAuthentication(authentication);
            
            // Generate JWT token
            String jwt = tokenProvider.generateToken(authentication);
            
            // Prepare response
            Map<String, Object> response = new HashMap<>();
            response.put("token", jwt);
            response.put("type", "Bearer");
            
            // Add user details to the response
            Map<String, Object> userInfo = new HashMap<>();
            userInfo.put("email", userDetails.getUsername());
            if (userDetails instanceof CustomUserDetails) {
                userInfo.put("savingsGroupId", ((CustomUserDetails) userDetails).getSavingsGroupId());
            }
            response.put("user", userInfo);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Invalid email, password, or savings group");
        }
    }
    
    @PostMapping("/create-group")
    public ResponseEntity<?> createGroupWithAdmin(@RequestBody UserDTO userDTO) {
        try {
            // This will be handled by the SavingsGroupController
            return ResponseEntity.badRequest().body("Use /api/savings-groups endpoint to create a new group");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}

















