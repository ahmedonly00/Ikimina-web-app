package com.example.ikimina.controller;

import com.example.ikimina.dto.LoginRequest;
import com.example.ikimina.dto.RegistrationRequest;
import com.example.ikimina.dto.UserDTO;
import com.example.ikimina.exception.BusinessRuleException;
import com.example.ikimina.model.SavingsGroup;
import com.example.ikimina.model.User;
import com.example.ikimina.security.CustomUserDetails;
import com.example.ikimina.security.JwtTokenProvider;
import com.example.ikimina.service.CustomUserDetailsService;
import com.example.ikimina.service.GroupMembershipService;
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

    @Autowired
    private GroupMembershipService groupMembershipService;

    /**
     * Public registration.
     *
     * This endpoint used to take a {@code savingsGroupId} and put the new
     * account straight into that group, so anyone who guessed a group id
     * joined a group whose money they had nothing to do with.
     *
     * A registrant can no longer name the group they end up in. Either they
     * present an invite code - which resolves to exactly one group and is the
     * only thing consulted - or they ask to join and an administrator decides.
     * A request on its own grants nothing: the account is created belonging to
     * no group, and cannot sign in until somebody approves it.
     */
    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> registerUser(
            @Valid @RequestBody RegistrationRequest request) {

        boolean hasCode = request.getJoinCode() != null && !request.getJoinCode().isBlank();
        boolean hasRequest = request.getRequestGroupId() != null;

        if (hasCode == hasRequest) {
            throw new BusinessRuleException(
                    "Provide either an invite code or the group you are asking to join");
        }

        Map<String, Object> body = new LinkedHashMap<>();

        if (hasCode) {
            SavingsGroup group = groupMembershipService.groupForJoinCode(request.getJoinCode());
            User user = userService.registerMember(request, group.getId());
            body.put("outcome", "JOINED");
            body.put("userId", user.getId());
            body.put("savingsGroupId", group.getId());
            body.put("savingsGroupName", group.getName());
            body.put("message", "You have joined " + group.getName() + ". You can sign in now.");
        } else {
            User user = userService.registerMember(request, (Long) null);
            groupMembershipService.requestToJoin(user.getId(), request.getRequestGroupId());
            body.put("outcome", "PENDING_APPROVAL");
            body.put("userId", user.getId());
            body.put("message",
                    "Your request has been sent. You can sign in once an administrator approves it.");
        }

        return ResponseEntity.ok(body);
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
            /*
             * Someone whose join request has not been approved has a valid
             * password but belongs to no group. Without this they would be told
             * "Savings group is required" and then find every group rejects
             * them, with nothing explaining why.
             *
             * Asked as a query rather than by reading user.getMemberGroups():
             * open-in-view is off, so the collection cannot initialise on a
             * detached entity and touching it throws.
             */
            if (userService.findPrimaryGroupId(user.getId()) == null) {
                throw new BusinessRuleException(
                        "Your request to join a group has not been approved yet");
            }
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
