package io.sentrius.sso.core.services.abac;

import io.sentrius.sso.core.services.security.KeycloakService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.representations.idm.UserRepresentation;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class KeycloakAttributeSyncSchedulerTest {

    @Mock
    private AttributeManagementService attributeManagementService;
    
    @Mock
    private KeycloakService keycloakService;
    
    private KeycloakAttributeSyncScheduler scheduler;
    
    @BeforeEach
    void setUp() {
        scheduler = new KeycloakAttributeSyncScheduler(attributeManagementService, keycloakService);
        
        // Set configuredEnabled and batchSize via reflection since they're @Value annotated
        try {
            var configuredEnabledField = KeycloakAttributeSyncScheduler.class.getDeclaredField("configuredEnabled");
            configuredEnabledField.setAccessible(true);
            configuredEnabledField.setBoolean(scheduler, true);
            
            var batchSizeField = KeycloakAttributeSyncScheduler.class.getDeclaredField("batchSize");
            batchSizeField.setAccessible(true);
            batchSizeField.setInt(scheduler, 100);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    @Test
    void testSyncUserAttributesFromKeycloak_WithMultipleUsers_SyncsSuccessfully() {
        // Arrange
        List<UserRepresentation> users = createMockUsers(3);
        
        lenient().when(keycloakService.getUsers(0, 100)).thenReturn(users);
        lenient().when(keycloakService.getUsers(3, 100)).thenReturn(Collections.emptyList());
        
        // Act
        scheduler.syncUserAttributesFromKeycloak();
        
        // Assert
        verify(keycloakService).getUsers(0, 100);
        verify(attributeManagementService, times(3)).syncUserAttributesFromKeycloak(anyString(), anyMap());
        
        Map<String, Object> stats = scheduler.getLastSyncStats();
        assertEquals(3, stats.get("syncedUsers"));
        assertTrue((Integer) stats.get("syncedAttributes") > 0);
    }
    
    @Test
    void testSyncUserAttributesFromKeycloak_WithPagination_HandlesMultipleBatches() {
        // Arrange
        List<UserRepresentation> batch1 = createMockUsers(100);
        List<UserRepresentation> batch2 = createMockUsers(50);
        
        lenient().when(keycloakService.getUsers(0, 100)).thenReturn(batch1);
        lenient().when(keycloakService.getUsers(100, 100)).thenReturn(batch2);
        lenient().when(keycloakService.getUsers(150, 100)).thenReturn(Collections.emptyList());
        
        // Act
        scheduler.syncUserAttributesFromKeycloak();
        
        // Assert
        verify(keycloakService).getUsers(0, 100);
        verify(keycloakService).getUsers(100, 100);
        verify(attributeManagementService, times(150)).syncUserAttributesFromKeycloak(anyString(), anyMap());
    }
    
    @Test
    void testSyncUserAttributesFromKeycloak_WithNoAttributes_SkipsUser() {
        // Arrange
        UserRepresentation userWithoutAttrs = new UserRepresentation();
        userWithoutAttrs.setId("user-no-attrs");
        userWithoutAttrs.setUsername("noattrs");
        userWithoutAttrs.setAttributes(null);
        
        lenient().when(keycloakService.getUsers(0, 100)).thenReturn(List.of(userWithoutAttrs));
        lenient().when(keycloakService.getUsers(1, 100)).thenReturn(Collections.emptyList());
        
        // Act
        scheduler.syncUserAttributesFromKeycloak();
        
        // Assert
        verify(attributeManagementService, never()).syncUserAttributesFromKeycloak(anyString(), anyMap());
        
        Map<String, Object> stats = scheduler.getLastSyncStats();
        assertEquals(0, stats.get("syncedUsers"));
    }
    
    @Test
    void testSyncUserAttributesFromKeycloak_FiltersInternalAttributes() {
        // Arrange
        UserRepresentation user = new UserRepresentation();
        user.setId("user-123");
        user.setUsername("testuser");
        
        Map<String, List<String>> attributes = new HashMap<>();
        attributes.put("department", List.of("engineering"));
        attributes.put("LDAP_ID", List.of("ldap123")); // Should be filtered
        attributes.put("KC_INTERNAL", List.of("internal")); // Should be filtered
        attributes.put("locale", List.of("en_US")); // Should be filtered
        attributes.put("clearance_level", List.of("high"));
        
        user.setAttributes(attributes);
        
        lenient().when(keycloakService.getUsers(0, 100)).thenReturn(List.of(user));
        lenient().when(keycloakService.getUsers(1, 100)).thenReturn(Collections.emptyList());
        
        // Act
        scheduler.syncUserAttributesFromKeycloak();
        
        // Assert
        verify(attributeManagementService).syncUserAttributesFromKeycloak(eq("user-123"), argThat(map -> {
            // Should only contain department and clearance_level
            return map.size() == 2 && 
                   map.containsKey("department") && 
                   map.containsKey("clearance_level") &&
                   !map.containsKey("LDAP_ID") &&
                   !map.containsKey("KC_INTERNAL") &&
                   !map.containsKey("locale");
        }));
    }
    
    @Test
    void testSyncUserAttributesFromKeycloak_HandlesExceptionGracefully() {
        // Arrange
        List<UserRepresentation> users = createMockUsers(2);
        
        lenient().when(keycloakService.getUsers(0, 100)).thenReturn(users);
        lenient().when(keycloakService.getUsers(2, 100)).thenReturn(Collections.emptyList());
        
        // First user succeeds, second user throws exception
        doNothing().when(attributeManagementService).syncUserAttributesFromKeycloak(eq("user-0"), anyMap());
        doThrow(new RuntimeException("Sync failed")).when(attributeManagementService)
            .syncUserAttributesFromKeycloak(eq("user-1"), anyMap());
        
        // Act - should not throw exception
        assertDoesNotThrow(() -> scheduler.syncUserAttributesFromKeycloak());
        
        // Assert
        verify(attributeManagementService, times(2)).syncUserAttributesFromKeycloak(anyString(), anyMap());
    }
    
    @Test
    void testSyncUserFromKeycloak_WithValidUser_SyncsSuccessfully() {
        // Arrange
        String userId = "user-123";
        UserRepresentation user = createMockUser(userId, "testuser", "department", "engineering");
        
        when(keycloakService.getUser(userId)).thenReturn(user);
        
        // Act
        scheduler.syncUserFromKeycloak(userId);
        
        // Assert
        verify(keycloakService).getUser(userId);
        verify(attributeManagementService).syncUserAttributesFromKeycloak(eq(userId), anyMap());
    }
    
    @Test
    void testSyncUserFromKeycloak_WithNonExistentUser_HandlesGracefully() {
        // Arrange
        String userId = "non-existent-user";
        when(keycloakService.getUser(userId)).thenReturn(null);
        
        // Act
        scheduler.syncUserFromKeycloak(userId);
        
        // Assert
        verify(keycloakService).getUser(userId);
        verify(attributeManagementService, never()).syncUserAttributesFromKeycloak(anyString(), anyMap());
    }
    
    @Test
    void testSyncUserFromKeycloak_WithNoAttributes_HandlesGracefully() {
        // Arrange
        String userId = "user-no-attrs";
        UserRepresentation user = new UserRepresentation();
        user.setId(userId);
        user.setUsername("noattrs");
        user.setAttributes(null);
        
        when(keycloakService.getUser(userId)).thenReturn(user);
        
        // Act
        scheduler.syncUserFromKeycloak(userId);
        
        // Assert
        verify(keycloakService).getUser(userId);
        verify(attributeManagementService, never()).syncUserAttributesFromKeycloak(anyString(), anyMap());
    }
    
    @Test
    void testSyncUserFromKeycloak_WithException_HandlesGracefully() {
        // Arrange
        String userId = "user-123";
        when(keycloakService.getUser(userId)).thenThrow(new RuntimeException("Keycloak unavailable"));
        
        // Act - should not throw exception
        assertDoesNotThrow(() -> scheduler.syncUserFromKeycloak(userId));
        
        // Assert
        verify(attributeManagementService, never()).syncUserAttributesFromKeycloak(anyString(), anyMap());
    }
    
    @Test
    void testSyncUserAttributes_UpdatesAttributes() {
        // Arrange
        String userId = "user-123";
        Map<String, String> attributes = new HashMap<>();
        attributes.put("department", "engineering");
        attributes.put("clearance_level", "high");
        
        // Act
        scheduler.syncUserAttributes(userId, attributes);
        
        // Assert
        verify(attributeManagementService).syncUserAttributesFromKeycloak(userId, attributes);
        assertNotEquals("Never", scheduler.getLastSyncTime());
    }
    
    @Test
    void testSyncAllUsersFromKeycloak_CallsScheduledMethod() {
        // Arrange
        List<UserRepresentation> users = createMockUsers(2);
        lenient().when(keycloakService.getUsers(0, 100)).thenReturn(users);
        lenient().when(keycloakService.getUsers(2, 100)).thenReturn(Collections.emptyList());
        
        // Act
        scheduler.syncAllUsersFromKeycloak();
        
        // Assert
        verify(attributeManagementService, times(2)).syncUserAttributesFromKeycloak(anyString(), anyMap());
    }
    
    @Test
    void testIsSyncEnabled_ReturnsTrue() {
        // Act & Assert
        assertTrue(scheduler.isSyncEnabled());
    }
    
    @Test
    void testGetLastSyncTime_InitiallyNever() {
        // Act & Assert
        assertEquals("Never", scheduler.getLastSyncTime());
    }
    
    @Test
    void testGetLastSyncStats_ReturnsValidStats() {
        // Act
        Map<String, Object> stats = scheduler.getLastSyncStats();
        
        // Assert
        assertNotNull(stats);
        assertTrue(stats.containsKey("lastSyncTime"));
        assertTrue(stats.containsKey("syncedUsers"));
        assertTrue(stats.containsKey("syncedAttributes"));
        assertEquals("Never", stats.get("lastSyncTime"));
        assertEquals(0, stats.get("syncedUsers"));
        assertEquals(0, stats.get("syncedAttributes"));
    }
    
    // Helper methods
    
    private List<UserRepresentation> createMockUsers(int count) {
        List<UserRepresentation> users = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            users.add(createMockUser("user-" + i, "user" + i, "department", "dept" + i));
        }
        return users;
    }
    
    private UserRepresentation createMockUser(String id, String username, String attrKey, String attrValue) {
        UserRepresentation user = new UserRepresentation();
        user.setId(id);
        user.setUsername(username);
        
        Map<String, List<String>> attributes = new HashMap<>();
        attributes.put(attrKey, List.of(attrValue));
        user.setAttributes(attributes);
        
        return user;
    }
}
