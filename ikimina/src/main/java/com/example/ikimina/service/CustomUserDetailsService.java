package com.example.ikimina.service;

import com.example.ikimina.security.CustomUserDetails;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.ikimina.model.User;
import com.example.ikimina.repository.UserRepository;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    @Autowired
    private UserRepository userRepository;

    /**
     * The username here is the user's email - it is the login identifier and the
     * JWT subject, so every lookup must agree on it.
     */
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));

        return CustomUserDetails.create(user, userRepository.findPrimaryGroupId(user.getId()));
    }

    @Transactional(readOnly = true)
    public UserDetails loadUserByEmailAndSavingsGroup(String email, Long savingsGroupId)
            throws UsernameNotFoundException {
        User user = userRepository.findByEmailAndSavingsGroupId(email, savingsGroupId)
                .orElseThrow(() -> new UsernameNotFoundException(
                        String.format("User not found with email %s in savings group %d", email, savingsGroupId)));

        return CustomUserDetails.create(user, savingsGroupId);
    }
}
