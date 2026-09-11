package com.example.ikimina.service;
import com.example.ikimina.exception.ResourceNotFoundException;
import com.example.ikimina.exception.BusinessRuleException;

import com.example.ikimina.enums.Role;
import com.example.ikimina.model.SavingsGroup;
import com.example.ikimina.model.User;
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
@Transactional(readOnly = true)
public class SavingsGroupService {

    @Autowired
    private SavingsGroupRepository savingsGroupRepository;
    
    @Autowired
    private UserRepository userRepository;

    public List<SavingsGroup> getAllGroups() {
        return savingsGroupRepository.findAll();
    }

    public Optional<SavingsGroup> getGroupById(Long id) {
        return savingsGroupRepository.findById(id);
    }

    public Optional<SavingsGroup> getGroupByName(String name) {
        return savingsGroupRepository.findByName(name);
    }

    public boolean groupExists(String name) {
        return savingsGroupRepository.existsByName(name);
    }

    @Transactional
    public SavingsGroup createGroup(SavingsGroup group, User adminUser) {
        SavingsGroup savedGroup = savingsGroupRepository.save(group);
        
        // Set the admin user for this group
        adminUser.addMemberGroup(savedGroup);
        adminUser.setRole(Role.ROLE_GROUP_ADMIN);
        userRepository.save(adminUser);
        
        return savedGroup;
    }

    @Transactional
    public void deleteGroup(Long groupId) {
        // First, find the group
        SavingsGroup group = savingsGroupRepository.findById(groupId)
            .orElseThrow(() -> new ResourceNotFoundException("Group not found with id: " + groupId));
            
        // Get a copy of the members to avoid concurrent modification
        Set<User> members = new HashSet<>(group.getMembers());
        
        // Remove all users from this group
        for (User user : members) {
            user.getMemberGroups().remove(group);
            userRepository.save(user);
        }
        
        // Clear the group's members collection
        group.getMembers().clear();
        
        // Then delete the group
        savingsGroupRepository.delete(group);
    }
}
