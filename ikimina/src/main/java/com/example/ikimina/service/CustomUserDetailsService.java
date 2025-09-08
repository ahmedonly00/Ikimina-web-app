package com.example.ikimina.service;

import com.example.ikimina.security.CustomUserDetails;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.example.ikimina.model.User;
import com.example.ikimina.repository.UserRepository;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    @Autowired
    private UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));

        return CustomUserDetails.create(user);
    }
    
    public UserDetails loadUserByEmailAndSavingsGroup(String email, Long savingsGroupId) throws UsernameNotFoundException {
        User user = userRepository.findByEmailAndSavingsGroupId(email, savingsGroupId)
                .orElseThrow(() -> new UsernameNotFoundException(
                    String.format("User not found with email %s in savings group %d", email, savingsGroupId)
                ));
                
        return CustomUserDetails.create(user);
    }
}