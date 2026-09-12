package com.example.ikimina.service;
import java.time.LocalDateTime;
import com.example.ikimina.dto.RegistrationRequest;
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
        /*
         * Both counts move when a user is removed, so the derived number can
         * collide with one already issued - and member_number is UNIQUE, which
         * would surface as a constraint violation at flush rather than
         * anything a caller could act on. Step past anything already taken.
         *
         * Still not safe against two concurrent registrations racing between
         * the check and the insert; the unique constraint is what actually
         * guarantees it, and this only keeps the common case from failing.
         */
        for (int offset = 1; offset <= 1000; offset++) {
            String candidate = group == null
                    ? String.format("M%05d", userRepository.count() + offset)
                    : String.format("G%dM%04d",
                            group.getId(),
                            userRepository.findBySavingsGroupId(group.getId()).size() + offset);
            if (!userRepository.existsByMemberNumber(candidate)) {
                return candidate;
            }
        }
        throw new BusinessRuleException("Could not allocate a member number");
    }

    /**
     * Create a member from a public registration.
     *
     * The group is passed in by the caller that was entitled to decide it - an
     * invite code lookup, or an administrator - and is never read from the
     * request body. A null group means the person is registered but belongs to
     * no group yet, which is the state a pending join request leaves them in.
     */
    @Transactional
    public User registerMember(RegistrationRequest request, Long groupId) {
        /*
         * Loaded here rather than accepted as an entity from the caller.
         * addMemberGroup maintains both sides of the association, so it reads
         * group.getMembers() - and with open-in-view off, a SavingsGroup that
         * crossed a transaction boundary is detached and that read throws.
         */
        SavingsGroup group = groupId == null ? null
                : savingsGroupRepository.findById(groupId)
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Savings group not found: " + groupId));

        String email = request.getEmail().trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            throw new BusinessRuleException("An account with this email already exists");
        }

        User user = new User();
        user.setFirstName(request.getFirstName().trim());
        user.setLastName(request.getLastName().trim());
        user.setFullName((user.getFirstName() + " " + user.getLastName()).trim());
        user.setEmail(email);
        user.setUsername(uniqueUsername(email));
        user.setPhoneNumber(request.getPhoneNumber());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setActive(true);
        // Always a plain member. Registration never confers administration.
        user.setRole(Role.ROLE_USER);
        user.setMemberNumber(nextMemberNumber(group));
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());

        if (group != null) {
            user.addMemberGroup(group);
        }
        return userRepository.save(user);
    }

    /**
     * The group this user belongs to, or null if none.
     *
     * A projection rather than a read of user.getMemberGroups(): open-in-view
     * is off, so that collection cannot initialise on a detached entity.
     */
    public Long findPrimaryGroupId(Long userId) {
        return userRepository.findPrimaryGroupId(userId);
    }

    /** username is NOT NULL UNIQUE but is not something a registrant supplies. */
    private String uniqueUsername(String email) {
        String base = email.split("@")[0].replaceAll("[^a-zA-Z0-9._-]", "");
        if (base.isBlank()) {
            base = "member";
        }
        if (!userRepository.existsByUsername(base)) {
            return base;
        }
        for (int i = 2; i < 1000; i++) {
            String candidate = base + i;
            if (!userRepository.existsByUsername(candidate)) {
                return candidate;
            }
        }
        throw new BusinessRuleException("Could not allocate a username");
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
        // UserDTO declares memberNumber and the entity stores one, but this
        // mapping never copied it, so every user came back with
        // memberNumber: null and the members roster had no member column.
        userDTO.setMemberNumber(user.getMemberNumber());

        if (user.getMemberGroups() != null && !user.getMemberGroups().isEmpty()) {
            userDTO.setSavingsGroupId(user.getMemberGroups().iterator().next().getId());
        }
        
        return userDTO;
    }
}