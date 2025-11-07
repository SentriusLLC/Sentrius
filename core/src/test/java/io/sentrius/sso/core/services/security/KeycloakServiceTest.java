package io.sentrius.sso.core.services.security;

import io.sentrius.sso.config.KeycloakConfig;
import io.sentrius.sso.config.KeycloakManager;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KeycloakServiceTest {

    @Mock
    private KeycloakManager keycloakManager;
    
    @Mock
    private KeycloakConfig keycloakConfig;
    
    @Mock
    private Keycloak keycloak;
    
    @Mock
    private RealmResource realmResource;
    
    @Mock
    private UsersResource usersResource;
    
    @Mock
    private UserResource userResource;
    
    @Mock
    private Response response;
    
    private KeycloakService keycloakService;
    
    private static final String REALM = "test-realm";
    
    @BeforeEach
    void setUp() {
        keycloakService = new KeycloakService(keycloakManager, keycloakConfig);
        
        // Set realm via reflection since it's @Value annotated
        try {
            var realmField = KeycloakService.class.getDeclaredField("realm");
            realmField.setAccessible(true);
            realmField.set(keycloakService, REALM);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        
        when(keycloakManager.getKeycloak()).thenReturn(keycloak);
        when(keycloak.realm(REALM)).thenReturn(realmResource);
        when(realmResource.users()).thenReturn(usersResource);
    }
    
    @Test
    void testGetAllUsers_ReturnsUserList() {
        // Arrange
        List<UserRepresentation> expectedUsers = createMockUsers(5);
        when(usersResource.list()).thenReturn(expectedUsers);
        
        // Act
        List<UserRepresentation> result = keycloakService.getAllUsers();
        
        // Assert
        assertNotNull(result);
        assertEquals(5, result.size());
        verify(usersResource).list();
    }
    
    @Test
    void testGetUsers_WithPagination_ReturnsPagedUsers() {
        // Arrange
        List<UserRepresentation> expectedUsers = createMockUsers(10);
        when(usersResource.list(0, 10)).thenReturn(expectedUsers);
        
        // Act
        List<UserRepresentation> result = keycloakService.getUsers(0, 10);
        
        // Assert
        assertNotNull(result);
        assertEquals(10, result.size());
        verify(usersResource).list(0, 10);
    }
    
    @Test
    void testGetUser_WithValidUserId_ReturnsUser() {
        // Arrange
        String userId = "user-123";
        UserRepresentation expectedUser = createMockUser(userId, "testuser");
        
        when(usersResource.get(userId)).thenReturn(userResource);
        when(userResource.toRepresentation()).thenReturn(expectedUser);
        
        // Act
        UserRepresentation result = keycloakService.getUser(userId);
        
        // Assert
        assertNotNull(result);
        assertEquals(userId, result.getId());
        assertEquals("testuser", result.getUsername());
    }
    
    @Test
    void testGetUser_WithInvalidUserId_ReturnsNull() {
        // Arrange
        String userId = "invalid-user";
        when(usersResource.get(userId)).thenThrow(new RuntimeException("User not found"));
        
        // Act
        UserRepresentation result = keycloakService.getUser(userId);
        
        // Assert
        assertNull(result);
    }
    
    @Test
    void testSearchUsersByUsername_ReturnsMatchingUsers() {
        // Arrange
        String username = "testuser";
        List<UserRepresentation> expectedUsers = List.of(createMockUser("user-123", username));
        
        when(usersResource.searchByUsername(username, true)).thenReturn(expectedUsers);
        
        // Act
        List<UserRepresentation> result = keycloakService.searchUsersByUsername(username);
        
        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(username, result.get(0).getUsername());
    }
    
    @Test
    void testCreateUser_WithValidData_ReturnsUserId() {
        // Arrange
        String username = "newuser";
        String email = "newuser@example.com";
        String firstName = "New";
        String lastName = "User";
        String userId = "user-new-123";
        
        when(response.getStatus()).thenReturn(201);
        when(response.getLocation()).thenReturn(URI.create("http://keycloak/users/" + userId));
        when(usersResource.create(any(UserRepresentation.class))).thenReturn(response);
        
        // Act
        String result = keycloakService.createUser(username, email, firstName, lastName, null);
        
        // Assert
        assertNotNull(result);
        assertEquals(userId, result);
        verify(usersResource).create(argThat(user -> 
            user.getUsername().equals(username) &&
            user.getEmail().equals(email) &&
            user.getFirstName().equals(firstName) &&
            user.getLastName().equals(lastName) &&
            user.isEnabled()
        ));
    }
    
    @Test
    void testCreateUser_WithAttributes_CreatesUserAndSetsAttributes() {
        // Arrange
        String username = "newuser";
        String email = "newuser@example.com";
        String userId = "user-123";
        Map<String, List<String>> attributes = new HashMap<>();
        attributes.put("department", List.of("engineering"));
        attributes.put("clearance_level", List.of("high"));
        
        when(response.getStatus()).thenReturn(201);
        when(response.getLocation()).thenReturn(URI.create("http://keycloak/users/" + userId));
        when(usersResource.create(any(UserRepresentation.class))).thenReturn(response);
        when(usersResource.get(userId)).thenReturn(userResource);
        
        UserRepresentation existingUser = createMockUser(userId, username);
        existingUser.setAttributes(new HashMap<>());
        when(userResource.toRepresentation()).thenReturn(existingUser);
        
        // Act
        String result = keycloakService.createUser(username, email, "First", "Last", attributes);
        
        // Assert
        assertNotNull(result);
        assertEquals(userId, result);
        
        // User is created with basic fields only
        verify(usersResource).create(argThat(user -> 
            user.getUsername().equals(username) &&
            user.getEmail().equals(email) &&
            (user.getAttributes() == null || user.getAttributes().isEmpty())
        ));
        
        // Attributes are set after creation via update
        verify(userResource).update(argThat(user -> 
            user.getAttributes() != null &&
            user.getAttributes().get("department").equals(List.of("engineering")) &&
            user.getAttributes().get("clearance_level").equals(List.of("high"))
        ));
    }
    
    @Test
    void testCreateUser_WithFailure_ReturnsNull() {
        // Arrange
        when(response.getStatus()).thenReturn(400);
        when(usersResource.create(any(UserRepresentation.class))).thenReturn(response);
        
        // Act
        String result = keycloakService.createUser("user", "email@test.com", "First", "Last", null);
        
        // Assert
        assertNull(result);
    }
    
    @Test
    void testCreateUser_WithException_ReturnsNull() {
        // Arrange
        when(usersResource.create(any(UserRepresentation.class)))
            .thenThrow(new RuntimeException("Keycloak error"));
        
        // Act
        String result = keycloakService.createUser("user", "email@test.com", "First", "Last", null);
        
        // Assert
        assertNull(result);
    }
    
    @Test
    void testCreateUser_WithPassword_SetsPasswordSuccessfully() {
        // Arrange
        String username = "newuser";
        String email = "newuser@example.com";
        String password = "SecurePassword123";
        String userId = "user-123";
        
        when(response.getStatus()).thenReturn(201);
        when(response.getLocation()).thenReturn(URI.create("http://keycloak/users/" + userId));
        when(usersResource.create(any(UserRepresentation.class))).thenReturn(response);
        when(usersResource.get(userId)).thenReturn(userResource);
        
        // Act
        String result = keycloakService.createUser(username, email, "First", "Last", null, password, false);
        
        // Assert
        assertNotNull(result);
        assertEquals(userId, result);
        verify(userResource).resetPassword(argThat(cred -> 
            cred.getType().equals(CredentialRepresentation.PASSWORD) &&
            cred.getValue().equals(password) &&
            !cred.isTemporary()
        ));
    }
    
    @Test
    void testCreateUser_WithTemporaryPassword_SetsTemporaryFlag() {
        // Arrange
        String username = "newuser";
        String email = "newuser@example.com";
        String password = "TempPassword123";
        String userId = "user-123";
        
        when(response.getStatus()).thenReturn(201);
        when(response.getLocation()).thenReturn(URI.create("http://keycloak/users/" + userId));
        when(usersResource.create(any(UserRepresentation.class))).thenReturn(response);
        when(usersResource.get(userId)).thenReturn(userResource);
        
        // Act
        String result = keycloakService.createUser(username, email, "First", "Last", null, password, true);
        
        // Assert
        assertNotNull(result);
        verify(userResource).resetPassword(argThat(cred -> 
            cred.isTemporary()
        ));
    }
    
    @Test
    void testSetUserPassword_WithValidData_UpdatesPassword() {
        // Arrange
        String userId = "user-123";
        String newPassword = "NewPassword123";
        
        when(usersResource.get(userId)).thenReturn(userResource);
        
        // Act
        keycloakService.setUserPassword(userId, newPassword, false);
        
        // Assert
        verify(userResource).resetPassword(argThat(cred -> 
            cred.getType().equals(CredentialRepresentation.PASSWORD) &&
            cred.getValue().equals(newPassword) &&
            !cred.isTemporary()
        ));
    }
    
    @Test
    void testSetUserPassword_WithException_HandlesGracefully() {
        // Arrange
        String userId = "user-123";
        when(usersResource.get(userId)).thenThrow(new RuntimeException("User not found"));
        
        // Act & Assert - should not throw exception
        assertDoesNotThrow(() -> 
            keycloakService.setUserPassword(userId, "password", false)
        );
    }
    
    @Test
    void testUpdateUserAttributes_WithValidUserId_UpdatesAttributes() {
        // Arrange
        String userId = "user-123";
        UserRepresentation user = createMockUser(userId, "testuser");
        user.setAttributes(new HashMap<>());
        user.getAttributes().put("old_attr", List.of("old_value"));
        
        Map<String, List<String>> newAttributes = new HashMap<>();
        newAttributes.put("new_attr", List.of("new_value"));
        
        when(usersResource.get(userId)).thenReturn(userResource);
        when(userResource.toRepresentation()).thenReturn(user);
        
        // Act
        keycloakService.updateUserAttributes(userId, newAttributes);
        
        // Assert
        verify(userResource).update(argThat(updatedUser -> 
            updatedUser.getAttributes().containsKey("old_attr") &&
            updatedUser.getAttributes().containsKey("new_attr")
        ));
    }
    
    @Test
    void testUpdateUserAttributes_WithNoExistingAttributes_CreatesNewAttributes() {
        // Arrange
        String userId = "user-123";
        UserRepresentation user = createMockUser(userId, "testuser");
        user.setAttributes(null);
        
        Map<String, List<String>> newAttributes = new HashMap<>();
        newAttributes.put("department", List.of("engineering"));
        
        when(usersResource.get(userId)).thenReturn(userResource);
        when(userResource.toRepresentation()).thenReturn(user);
        
        // Act
        keycloakService.updateUserAttributes(userId, newAttributes);
        
        // Assert
        verify(userResource).update(argThat(updatedUser -> 
            updatedUser.getAttributes() != null &&
            updatedUser.getAttributes().containsKey("department")
        ));
    }
    
    @Test
    void testUpdateUserAttributes_WithException_HandlesGracefully() {
        // Arrange
        String userId = "user-123";
        when(usersResource.get(userId)).thenThrow(new RuntimeException("User not found"));
        
        // Act & Assert - should not throw exception
        assertDoesNotThrow(() -> 
            keycloakService.updateUserAttributes(userId, new HashMap<>())
        );
    }
    
    @Test
    void testGetUserAttributes_ReturnsAttributes() {
        // Arrange
        String userId = "user-123";
        UserRepresentation user = createMockUser(userId, "testuser");
        Map<String, List<String>> attributes = new HashMap<>();
        attributes.put("department", List.of("engineering"));
        user.setAttributes(attributes);
        
        when(usersResource.get(userId)).thenReturn(userResource);
        when(userResource.toRepresentation()).thenReturn(user);
        
        // Act
        Map<String, List<String>> result = keycloakService.getUserAttributes(userId);
        
        // Assert
        assertNotNull(result);
        assertTrue(result.containsKey("department"));
        assertEquals(List.of("engineering"), result.get("department"));
    }
    
    // Helper methods
    
    private List<UserRepresentation> createMockUsers(int count) {
        List<UserRepresentation> users = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            users.add(createMockUser("user-" + i, "user" + i));
        }
        return users;
    }
    
    private UserRepresentation createMockUser(String id, String username) {
        UserRepresentation user = new UserRepresentation();
        user.setId(id);
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setFirstName("First" + id);
        user.setLastName("Last" + id);
        user.setEnabled(true);
        return user;
    }
}
