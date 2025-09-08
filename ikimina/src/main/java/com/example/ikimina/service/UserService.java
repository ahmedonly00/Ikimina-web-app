package com.example.ikimina.service;

import com.example.ikimina.dto.UserDTO;
import com.example.ikimina.model.SavingsGroup;
import com.example.ikimina.model.User;
import com.example.ikimina.repository.RoleRepository;
import com.example.ikimina.repository.SavingsGroupRepository;
import com.example.ikimina.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class UserService {
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private PasswordEncoder passwordEncoder;
    
    @Autowired
    private SavingsGroupRepository savingsGroupRepository;
    
    @Autowired
    private RoleRepository roleRepository;
    
    @Transactional
    public UserDTO createUser(UserDTO userDTO) {
        // Check if username or email already exists
        if (userRepository.existsByUsername(userDTO.getUsername())) {
            throw new RuntimeException("Username is already taken");
        }
        if (userRepository.existsByEmail(userDTO.getEmail())) {
            throw new RuntimeException("Email is already in use");
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
        
        // Set default role if not specified
        Set<User.Role> roles = new HashSet<>();
        if (userDTO.getRoleIds() != null && !userDTO.getRoleIds().isEmpty()) {
            roles = roleRepository.findAllById(userDTO.getRoleIds())
                .stream()
                .collect(Collectors.toSet());
        } else {
            // Default role is ROLE_USER
            User.Role userRole = roleRepository.findByName(User.RoleType.ROLE_USER)
                .orElseThrow(() -> new RuntimeException("Error: Role is not found."));
            roles.add(userRole);
        }
        user.setRoles(roles);
        
        // If this is the first user, make them a super admin
        if (userRepository.count() == 0) {
            User.Role adminRole = roleRepository.findByName(User.RoleType.ROLE_SUPER_ADMIN)
                .orElseThrow(() -> new RuntimeException("Error: Role is not found."));
            user.getRoles().add(adminRole);
        }
        
        User savedUser = userRepository.save(user);
        return convertToDTO(savedUser);
    }
    
    public UserDTO updateUser(Long id, UserDTO userDTO) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        user.setFullName(userDTO.getFullName());
        user.setPhoneNumber(userDTO.getPhoneNumber());
        if (userDTO.getPassword() != null && !userDTO.getPassword().isEmpty()) {
            user.setPassword(passwordEncoder.encode(userDTO.getPassword()));
        }
        
        User updatedUser = userRepository.save(user);
        return convertToDTO(updatedUser);
    }
    
    public void deleteUser(Long id) {
        userRepository.deleteById(id);
    }
    
    public UserDTO getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return convertToDTO(user);
    }
    
    public List<UserDTO> getAllUsers() {
        return userRepository.findAll().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }
    
    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }
    
    public boolean existsByEmailAndSavingsGroupId(String email, Long savingsGroupId) {
        return userRepository.existsByEmailAndSavingsGroupId(email, savingsGroupId);
    }
    
    public List<User> getUsersBySavingsGroup(Long savingsGroupId) {
        return userRepository.findBySavingsGroupId(savingsGroupId);
    }
    
    private UserDTO convertToDTO(User user) {
        UserDTO dto = new UserDTO();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setFirstName(user.getFirstName());
        dto.setLastName(user.getLastName());
        dto.setEmail(user.getEmail());
        dto.setPhoneNumber(user.getPhoneNumber());
        dto.setActive(user.isActive());
        
        // Convert roles to role names
        Set<String> roleNames = user.getRoles().stream()
            .map(role -> role.getName().name())
            .collect(Collectors.toSet());
        dto.setRoles(roleNames);
        
        // Get first group if user is a member of any
        Optional<SavingsGroup> firstGroup = user.getMemberGroups().stream().findFirst();
        if (firstGroup.isPresent()) {
            dto.setSavingsGroupId(firstGroup.get().getId());
            dto.setSavingsGroupName(firstGroup.get().getName());
        }
        
        return dto;
    }
}