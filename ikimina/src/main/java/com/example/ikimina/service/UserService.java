package com.example.ikimina.service;
import com.example.ikimina.exception.BusinessRuleException;
import com.example.ikimina.exception.ResourceNotFoundException;

import com.example.ikimina.dto.UserDTO;
import com.example.ikimina.model.SavingsGroup;
import com.example.ikimina.model.User;
import com.example.ikimina.enums.Role;
import com.example.ikimina.repository.SavingsGroupRepository;
import com.example.ikimina.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class UserService {
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private SavingsGroupRepository savingsGroupRepository;
    
    
    @Transactional
    public UserDTO createUser(UserDTO userDTO) {
        // Check if username or email already exists
        if (userRepository.existsByUsername(userDTO.getUsername())) {
            throw new BusinessRuleException("Username is already taken");
        }
        if (userRepository.existsByEmail(userDTO.getEmail())) {
            throw new BusinessRuleException("Email is already in use");
        }
        
        // Create new user
        User user = new User();
        user.setUsername(userDTO.getUsername());
        user.setFirstName(userDTO.getFirstName());
        user.setLastName(userDTO.getLastName());
        user.setEmail(userDTO.getEmail());
        user.setPhoneNumber(userDTO.getPhoneNumber());
        user.setPassword(passwordEncoder.encode(userDTO.getPassword()));
        user.setActive(true);
        
        // Set default role to ROLE_USER
        user.setRole(Role.ROLE_USER);
        
        // If this is the first user, make them a super admin
        if (userRepository.count() == 0) {
            user.setRole(Role.ROLE_SUPER_ADMIN);
        }
        
        // member_number is NOT NULL UNIQUE, and nothing was setting it, so every
        // registration failed on the insert.
        SavingsGroup group = null;
        if (userDTO.getSavingsGroupId() != null) {
            group = savingsGroupRepository.findById(userDTO.getSavingsGroupId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Savings group not found: " + userDTO.getSavingsGroupId()));
        }
        user.setMemberNumber(nextMemberNumber(group));

        // The requested group was also being ignored, so a registered member
        // belonged to no group - and login requires group membership, so they
        // could never sign in.
        if (group != null) {
            user.addMemberGroup(group);
        }

        User savedUser = userRepository.save(user);
        return convertToDTO(savedUser);
    }

    /**
     * Human-readable member number, unique per group.
     *
     * Derived from the current member count, so two simultaneous registrations
     * into the same group can collide; the unique constraint rejects the loser
     * and the caller sees a conflict rather than a duplicate number. Groups add
     * members one at a time in practice, so a sequence table would be more
     * machinery than the problem warrants.
     */
    private String nextMemberNumber(SavingsGroup group) {
        if (group == null) {
            return String.format("M%05d", userRepository.count() + 1);
        }
        long seq = userRepository.findBySavingsGroupId(group.getId()).size() + 1L;
        return String.format("G%dM%04d", group.getId(), seq);
    }
    
    @Transactional
    public UserDTO updateUser(Long id, UserDTO userDTO) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        
        user.setFirstName(userDTO.getFirstName());
        user.setLastName(userDTO.getLastName());
        user.setPhoneNumber(userDTO.getPhoneNumber());
        if (userDTO.getPassword() != null && !userDTO.getPassword().isEmpty()) {
            user.setPassword(passwordEncoder.encode(userDTO.getPassword()));
        }
        
        User updatedUser = userRepository.save(user);
        return convertToDTO(updatedUser);
    }
    
    @Transactional
    public void deleteUser(Long id) {
        userRepository.deleteById(id);
    }
    
    public Optional<UserDTO> getUserById(Long id) {
       return userRepository.findById(id)
                .map(this::convertToDTO);
    }
    
    public List<UserDTO> getAllUsers() {
        return userRepository.findAll().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }
    
    public List<UserDTO> getAllActiveUsers() {
        return userRepository.findByActive(true).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Active members of a single group.
     *
     * Not paged on purpose: the caller is populating a meeting sheet and needs
     * every member, so truncating at a page boundary would silently drop people
     * from a contribution round. It is bounded by group size instead.
     */
    public List<UserDTO> getActiveUsersForGroup(Long groupId) {
        return userRepository.findActiveBySavingsGroupId(groupId).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }
    
    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }
    
    public Optional<User> findByUsername(String username) {
        // In this system, username is the same as email
        return userRepository.findByEmail(username);
    }
    
    public boolean existsByEmailAndSavingsGroupId(String email, Long savingsGroupId) {
        return userRepository.existsByEmailAndSavingsGroupId(email, savingsGroupId);
    }
    
    public List<UserDTO> getUsersBySavingsGroup(Long savingsGroupId) {
        return userRepository.findBySavingsGroupId(savingsGroupId).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }
    
    public UserDTO getUserDTOById(Long id) {
        return userRepository.findById(id)
                .map(this::convertToDTO)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
    }

    @Transactional
    public UserDTO updateUserStatus(Long id, boolean active) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
        
        user.setActive(active);
        User updatedUser = userRepository.save(user);
        return convertToDTO(updatedUser);
    }

    @Transactional
    public UserDTO addRoleToUser(Long userId, Long roleId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
        
        Role role = Role.fromId(roleId)
                .orElseThrow(() -> new BusinessRuleException("Invalid role ID: " + roleId));
        
        // Set the new role
        user.setRole(role);
        User updatedUser = userRepository.save(user);
        return convertToDTO(updatedUser);
    }
    
    @Transactional
    public UserDTO removeRoleFromUser(Long userId, Long roleId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
        
        Role role = Role.fromId(roleId)
                .orElseThrow(() -> new BusinessRuleException("Invalid role ID: " + roleId));
        
        // Remove the role if it matches the current role
        if (user.getRole() != null && user.getRole().equals(role)) {
            user.setRole(null); // or set to a default role if needed
            User updatedUser = userRepository.save(user);
            return convertToDTO(updatedUser);
        }
        
        return convertToDTO(user);
    }
    
    private UserDTO convertToDTO(User user) {
        UserDTO userDTO = new UserDTO();
        userDTO.setId(user.getId());
        userDTO.setUsername(user.getUsername());
        userDTO.setFirstName(user.getFirstName());
        userDTO.setLastName(user.getLastName());
        userDTO.setEmail(user.getEmail());
        userDTO.setPhoneNumber(user.getPhoneNumber());
        userDTO.setActive(user.isActive());
        userDTO.setRole(user.getRole());
        
        if (user.getMemberGroups() != null && !user.getMemberGroups().isEmpty()) {
            userDTO.setSavingsGroupId(user.getMemberGroups().iterator().next().getId());
        }
        
        return userDTO;
    }
}