package com.example.ikimina.service;

import com.example.ikimina.model.SavingsGroup;
import com.example.ikimina.model.User;
import com.example.ikimina.enums.Role;
import com.example.ikimina.repository.SavingsGroupRepository;
import com.example.ikimina.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class GroupService {

    @Autowired
    private SavingsGroupRepository savingsGroupRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    @Transactional
    public SavingsGroup createGroup(SavingsGroup group, User adminUser) {
        // Save the group first
        SavingsGroup savedGroup = savingsGroupRepository.save(group);
        
        // Make the user a group admin if they aren't already
        if (adminUser.getRole() != Role.ROLE_GROUP_ADMIN) {
            adminUser.setRole(Role.ROLE_GROUP_ADMIN);
        }
            
        // Add the user as admin of this group
        adminUser.addMemberGroup(savedGroup);
        savedGroup.setAdmin(adminUser);
        
        userRepository.save(adminUser);
        return savingsGroupRepository.save(savedGroup);
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
        // First, get the group
        SavingsGroup group = savingsGroupRepository.findById(groupId)
            .orElseThrow(() -> new RuntimeException("Group not found with id: " + groupId));
            
        // Create a copy of the members set to avoid concurrent modification
        Set<User> members = new HashSet<>(group.getMembers());
        
        // Remove all users from this group
        for (User user : members) {
            user.removeMemberGroup(group);
        }
        
        // Clear admin references if this user is an admin of any groups
        User admin = group.getAdmin();
        if (admin != null) {
            admin.getAdminOfGroups().remove(group);
            userRepository.save(admin);
        }
        
        // Then delete the group
        savingsGroupRepository.delete(group);
    }
    
    public boolean groupExists(String name) {
        return savingsGroupRepository.existsByName(name);
    }
}
