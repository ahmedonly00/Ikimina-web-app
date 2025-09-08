package com.example.ikimina.service;

import com.example.ikimina.model.SavingsGroup;
import com.example.ikimina.model.User;
import com.example.ikimina.repository.RoleRepository;
import com.example.ikimina.repository.SavingsGroupRepository;
import com.example.ikimina.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class GroupService {

    @Autowired
    private SavingsGroupRepository savingsGroupRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private RoleRepository roleRepository;
    
    @Transactional
    public SavingsGroup createGroup(SavingsGroup group, User adminUser) {
        // Save the group first
        SavingsGroup savedGroup = savingsGroupRepository.save(group);
        
        // Make the user a group admin if they aren't already
        User.Role groupAdminRole = roleRepository.findByName(User.RoleType.ROLE_GROUP_ADMIN)
            .orElseThrow(() -> new RuntimeException("Group admin role not found"));
            
        // Add the user as admin of this group
        adminUser.getMemberGroups().add(savedGroup);
        adminUser.getRoles().add(groupAdminRole);
        savedGroup.setAdmin(adminUser);
        
        userRepository.save(adminUser);
        savingsGroupRepository.save(savedGroup);
        
        return savedGroup;
    }
    
    public List<SavingsGroup> getAllGroups() {
        return savingsGroupRepository.findAll();
    }
    
    public Optional<SavingsGroup> getGroupById(Long id) {
        return savingsGroupRepository.findById(id);
    }
    
    public boolean isUserInGroup(Long userId, Long groupId) {
        return userRepository.existsByIdAndSavingsGroupId(userId, groupId);
    }
    
    @Transactional
    public void deleteGroup(Long groupId) {
        // First, remove all users from this group
        List<User> groupUsers = userRepository.findBySavingsGroupId(groupId);
        for (User user : groupUsers) {
            user.setSavingsGroup(null);
            userRepository.save(user);
        }
        
        // Then delete the group
        savingsGroupRepository.deleteById(groupId);
    }
    
    public boolean groupExists(String name) {
        return savingsGroupRepository.existsByName(name);
    }
}
