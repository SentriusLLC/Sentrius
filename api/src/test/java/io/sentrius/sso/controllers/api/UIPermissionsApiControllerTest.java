package io.sentrius.sso.controllers.api;

import io.sentrius.sso.core.config.SystemOptions;
import io.sentrius.sso.core.model.users.User;
import io.sentrius.sso.core.model.security.UserType;
import io.sentrius.sso.core.services.UserService;
import io.sentrius.sso.core.services.abac.PolicyEvaluator;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for UIPermissionsApiController
 */
@ExtendWith(MockitoExtension.class)
class UIPermissionsApiControllerTest {

    @Mock
    private UserService userService;

    @Mock
    private SystemOptions systemOptions;

    @Mock
    private PolicyEvaluator policyEvaluator;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    private UIPermissionsApiController controller;

    @BeforeEach
    void setUp() {
        controller = new UIPermissionsApiController(userService, systemOptions);
    }

    @Test
    void testGetUserUIPermissions_WhenUserNotAuthenticated_Returns401() {
        // Given
        when(userService.getOperatingUser(any(), any(), any())).thenReturn(null);

        // When
        ResponseEntity<UIPermissionsApiController.UIPermissionsResponse> response = 
                controller.getUserUIPermissions(request, this.response);

        // Then
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNull(response.getBody());
    }

    @Test
    void testGetUserUIPermissions_WhenAbacDisabled_ReturnsBasicPermissions() {
        // Given
        User mockUser = createMockUser("testuser", Set.of("CAN_MANAGE_APPLICATION"));
        when(userService.getOperatingUser(any(), any(), any())).thenReturn(mockUser);
        when(systemOptions.getEnableAbacUiControl()).thenReturn(false);

        // When
        ResponseEntity<UIPermissionsApiController.UIPermissionsResponse> response = 
                controller.getUserUIPermissions(request, this.response);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("testuser", response.getBody().username());
        assertFalse(response.getBody().abacEnabled());
        assertNotNull(response.getBody().permissions());
        
        // User with CAN_MANAGE_APPLICATION should have access to certain resources
        assertTrue(response.getBody().permissions().containsKey("security.trust_policies"));
    }

    @Test
    void testGetUserUIPermissions_WhenAbacEnabled_ReturnsEnhancedPermissions() {
        // Given
        User mockUser = createMockUser("testuser", Set.of("CAN_LOG_IN"));
        when(userService.getOperatingUser(any(), any(), any())).thenReturn(mockUser);
        when(systemOptions.getEnableAbacUiControl()).thenReturn(true);

        // When
        ResponseEntity<UIPermissionsApiController.UIPermissionsResponse> response = 
                controller.getUserUIPermissions(request, this.response);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("testuser", response.getBody().username());
        assertTrue(response.getBody().abacEnabled());
        assertNotNull(response.getBody().permissions());
    }

    @Test
    void testCheckResourcePermission_WhenResourceNotFound_Returns404() {
        // Given
        User mockUser = createMockUser("testuser", Set.of());
        when(userService.getOperatingUser(any(), any(), any())).thenReturn(mockUser);

        // When
        ResponseEntity<UIPermissionsApiController.ResourcePermissionResponse> response = 
                controller.checkResourcePermission("non.existent.resource", request, this.response);

        // Then
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void testCheckResourcePermission_WhenUserHasAccessViaAccessSet_ReturnsAccessGranted() {
        // Given
        User mockUser = createMockUser("testuser", Set.of("CAN_MANAGE_APPLICATION"));
        when(userService.getOperatingUser(any(), any(), any())).thenReturn(mockUser);
        when(systemOptions.getEnableAbacUiControl()).thenReturn(false);

        // When
        ResponseEntity<UIPermissionsApiController.ResourcePermissionResponse> response = 
                controller.checkResourcePermission("security.trust_policies", request, this.response);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("security.trust_policies", response.getBody().resourceKey());
        assertTrue(response.getBody().hasAccess());
        assertEquals("access_set", response.getBody().grantedBy());
    }

    @Test
    void testCheckResourcePermission_WhenUserHasNoAccess_ReturnsAccessDenied() {
        // Given
        User mockUser = createMockUser("testuser", Set.of("CAN_LOG_IN"));
        when(userService.getOperatingUser(any(), any(), any())).thenReturn(mockUser);
        when(systemOptions.getEnableAbacUiControl()).thenReturn(false);

        // When
        ResponseEntity<UIPermissionsApiController.ResourcePermissionResponse> response = 
                controller.checkResourcePermission("system.settings", request, this.response);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("system.settings", response.getBody().resourceKey());
        assertFalse(response.getBody().hasAccess());
        assertEquals("none", response.getBody().grantedBy());
    }

    /**
     * Helper method to create a mock user with specified access set
     */
    private User createMockUser(String username, Set<String> accessSet) {
        User user = new User();
        user.setUsername(username);
        
        UserType userType;
        if (accessSet.contains("CAN_MANAGE_APPLICATION")) {
            userType = UserType.createSuperUser();
        } else if (accessSet.contains("CAN_MANAGE_USERS")) {
            userType = UserType.builder()
                .applicationAccess(io.sentrius.sso.core.model.security.enums.ApplicationAccessEnum.CAN_LOG_IN)
                .userAccess(io.sentrius.sso.core.model.security.enums.UserAccessEnum.CAN_MANAGE_USERS)
                .build();
        } else if (accessSet.contains("CAN_MANAGE_SYSTEMS")) {
            userType = UserType.builder()
                .applicationAccess(io.sentrius.sso.core.model.security.enums.ApplicationAccessEnum.CAN_LOG_IN)
                .systemAccess(io.sentrius.sso.core.model.security.enums.SSHAccessEnum.CAN_MANAGE_SYSTEMS)
                .build();
        } else if (accessSet.contains("CAN_LOG_IN")) {
            userType = UserType.createBaseUser();
        } else {
            userType = UserType.createUnknownUser();
        }
        
        user.setAuthorizationType(userType);
        
        return user;
    }
}
