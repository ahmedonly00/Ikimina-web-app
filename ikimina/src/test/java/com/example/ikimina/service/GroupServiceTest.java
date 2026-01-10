package com.example.ikimina.service;

import com.example.ikimina.model.SavingsGroup;
import com.example.ikimina.model.User;
import com.example.ikimina.repository.SavingsGroupRepository;
import com.example.ikimina.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class GroupServiceTest {

    @Mock
    private SavingsGroupRepository savingsGroupRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private GroupService groupService;

    private SavingsGroup testGroup;
    private User testUser;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        testGroup = new SavingsGroup();
        testGroup.setId(1L);
        testGroup.setName("Test Group");
        testGroup.setDescription("Test Description");
        
        testUser = new User();
        testUser.setId(1L);
        testUser.setFirstName("Test");
        testUser.setLastName("User");
        testUser.setFullName("Test User");
        testUser.setEmail("test@example.com");
        testUser.setRole(com.example.ikimina.enums.Role.ROLE_GROUP_ADMIN);
        testUser.setActive(true);
    }

    @Test
    void createGroup() {
        when(savingsGroupRepository.save(any(SavingsGroup.class))).thenReturn(testGroup);
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        SavingsGroup createdGroup = groupService.createGroup(testGroup, testUser);

        assertNotNull(createdGroup);
        assertEquals(testGroup.getName(), createdGroup.getName());
        verify(userRepository, times(1)).save(testUser);
        verify(savingsGroupRepository, times(2)).save(testGroup); // Once for initial save, once after setting admin
        assertTrue(testUser.getMemberGroups().contains(testGroup));
        assertEquals(testUser, testGroup.getAdmin());
    }

    @Test
    void getAllGroups_ShouldReturnAllGroups() {
        // Arrange
        when(savingsGroupRepository.findAll()).thenReturn(Arrays.asList(testGroup));

        // Act
        List<SavingsGroup> result = groupService.getAllGroups();

        // Assert
        assertFalse(result.isEmpty());
        assertEquals(1, result.size());
        assertEquals(testGroup.getName(), result.get(0).getName());
    }

    @Test
    void getGroupById_ShouldReturnGroup_WhenExists() {
        // Arrange
        when(savingsGroupRepository.findById(1L)).thenReturn(Optional.of(testGroup));

        // Act
        Optional<SavingsGroup> result = groupService.getGroupById(1L);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(testGroup.getName(), result.get().getName());
    }

    @Test
    void isUserInGroup_ShouldReturnTrue_WhenUserIsInGroup() {
        // Arrange
        when(userRepository.existsByIdAndSavingsGroupId(1L, 1L)).thenReturn(true);

        // Act
        boolean result = groupService.isUserInGroup(1L, 1L);

        // Assert
        assertTrue(result);
    }

    @Test
    void deleteGroup_ShouldRemoveGroupAndUsers() {
        // Arrange
        when(userRepository.findBySavingsGroupId(1L)).thenReturn(Arrays.asList(testUser));
        doNothing().when(savingsGroupRepository).deleteById(1L);

        // Act
        groupService.deleteGroup(1L);

        // Assert
        verify(userRepository, times(1)).findBySavingsGroupId(1L);
        verify(userRepository, times(1)).save(testUser);
        verify(savingsGroupRepository, times(1)).deleteById(1L);
        assertNull(testUser.getSavingsGroup());
    }

    @Test
    void groupExists_ShouldReturnTrue_WhenGroupExists() {
        // Arrange
        when(savingsGroupRepository.existsByName("Existing Group")).thenReturn(true);

        // Act
        boolean exists = groupService.groupExists("Existing Group");

        // Assert
        assertTrue(exists);
    }
}
