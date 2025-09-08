package com.example.ikimina.service;

import com.example.ikimina.model.SavingsGroup;
import com.example.ikimina.model.User;
import com.example.ikimina.repository.SavingsGroupRepository;
import com.example.ikimina.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
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
        adminUser.setSavingsGroup(savedGroup);
        adminUser.setRole(User.Role.ADMIN);
        userRepository.save(adminUser);
        
        return savedGroup;
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
}
