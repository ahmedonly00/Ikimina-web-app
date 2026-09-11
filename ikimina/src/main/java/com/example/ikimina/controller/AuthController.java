package com.example.ikimina.controller;

import com.example.ikimina.dto.LoginRequest;
import com.example.ikimina.dto.UserDTO;
import com.example.ikimina.exception.BusinessRuleException;
import com.example.ikimina.model.User;
import com.example.ikimina.security.CustomUserDetails;
import com.example.ikimina.security.JwtTokenProvider;
import com.example.ikimina.service.CustomUserDetailsService;
import com.example.ikimina.service.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
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
    public ResponseEntity<UserDTO> registerUser(@Valid @RequestBody UserDTO userDTO) {
        if (userDTO.getSavingsGroupId() != null
                && userService.existsByEmailAndSavingsGroupId(userDTO.getEmail(), userDTO.getSavingsGroupId())) {
            throw new BusinessRuleException("A user with this email already exists in the selected savings group");
        }
        return ResponseEntity.ok(userService.createUser(userDTO));
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@Valid @RequestBody LoginRequest loginRequest) {
        // Verify the password first. The provider hides "user not found" so this
        // cannot be used to enumerate registered emails.
        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(
                loginRequest.getEmail(), loginRequest.getPassword()));

        User user = userService.findByEmail(loginRequest.getEmail())
                .orElseThrow(() -> new BadCredentialsException("Invalid email, password, or savings group"));

        // A super admin is global and belongs to no savings group, so it must not
        // be required to present one. Group-scoped accounts must, and the
        // principal is then resolved against that group so the token carries it.
        CustomUserDetails userDetails;
        if (user.isSuperAdmin()) {
            userDetails = CustomUserDetails.create(user, loginRequest.getSavingsGroupId());
        } else {
            if (loginRequest.getSavingsGroupId() == null) {
                throw new BusinessRuleException("Savings group is required");
            }
            try {
                userDetails = (CustomUserDetails) userDetailsService.loadUserByEmailAndSavingsGroup(
                        loginRequest.getEmail(), loginRequest.getSavingsGroupId());
            } catch (UsernameNotFoundException ex) {
                throw new BadCredentialsException("Invalid email, password, or savings group");
            }
        }

        String jwt = tokenProvider.generateToken(userDetails);

        Map<String, Object> userInfo = new LinkedHashMap<>();
        userInfo.put("id", userDetails.getUserId());
        userInfo.put("email", userDetails.getUsername());
        userInfo.put("savingsGroupId", userDetails.getSavingsGroupId());
        userInfo.put("role", user.getRole().name());
        userInfo.put("firstName", user.getFirstName());
        userInfo.put("lastName", user.getLastName());
        userInfo.put("memberNumber", user.getMemberNumber());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("token", jwt);
        response.put("type", "Bearer");
        response.put("user", userInfo);

        return ResponseEntity.ok(response);
    }
}
