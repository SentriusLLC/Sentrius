package io.sentrius.sso.core.model.security;

import io.sentrius.sso.core.model.users.User;
import io.sentrius.sso.core.services.users.UserAttributeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccessControlAspectCustomAttributeTest {

    @Mock
    private UserAttributeService userAttributeService;

    private AccessControlAspectTestHelper aspectHelper;
    private User testUser;

    @BeforeEach
    void setUp() {
        aspectHelper = new AccessControlAspectTestHelper(userAttributeService);
        
        testUser = new User();
        testUser.setUserId("user-123");
        testUser.setUsername("testuser");
    }

    @Test
    void checkCustomAttribute_WithValidAttribute_ReturnsTrue() {
        // Arrange
        String customAttr = "department=engineering";
        when(userAttributeService.userHasAttributeValue("user-123", "department", "engineering"))
                .thenReturn(true);

        // Act
        boolean result = aspectHelper.checkCustomAttribute(testUser, customAttr);

        // Assert
        assertTrue(result);
        verify(userAttributeService).userHasAttributeValue("user-123", "department", "engineering");
    }

    @Test
    void checkCustomAttribute_WithMissingAttribute_ReturnsFalse() {
        // Arrange
        String customAttr = "department=engineering";
        when(userAttributeService.userHasAttributeValue("user-123", "department", "engineering"))
                .thenReturn(false);

        // Act
        boolean result = aspectHelper.checkCustomAttribute(testUser, customAttr);

        // Assert
        assertFalse(result);
        verify(userAttributeService).userHasAttributeValue("user-123", "department", "engineering");
    }

    @Test
    void checkCustomAttribute_WithInvalidFormat_ReturnsFalse() {
        // Arrange
        String customAttr = "invalid-format-no-equals";

        // Act
        boolean result = aspectHelper.checkCustomAttribute(testUser, customAttr);

        // Assert
        assertFalse(result);
        verify(userAttributeService, never()).userHasAttributeValue(anyString(), anyString(), anyString());
    }

    @Test
    void checkCustomAttribute_WithEmptyString_ReturnsTrue() {
        // Arrange
        String customAttr = "";

        // Act
        boolean result = aspectHelper.checkCustomAttribute(testUser, customAttr);

        // Assert
        assertTrue(result);
        verify(userAttributeService, never()).userHasAttributeValue(anyString(), anyString(), anyString());
    }

    @Test
    void checkCustomAttribute_WithNullString_ReturnsTrue() {
        // Arrange
        String customAttr = null;

        // Act
        boolean result = aspectHelper.checkCustomAttribute(testUser, customAttr);

        // Assert
        assertTrue(result);
        verify(userAttributeService, never()).userHasAttributeValue(anyString(), anyString(), anyString());
    }
    
    /**
     * Test helper class that mimics the checkCustomAttribute logic from AccessControlAspect
     */
    static class AccessControlAspectTestHelper {
        private final UserAttributeService userAttributeService;

        public AccessControlAspectTestHelper(UserAttributeService userAttributeService) {
            this.userAttributeService = userAttributeService;
        }

        public boolean checkCustomAttribute(User operatingUser, String customAttr) {
            if (customAttr == null || customAttr.isEmpty()) {
                return true;
            }

            String[] parts = customAttr.split("=", 2);
            if (parts.length != 2) {
                return false;
            }

            String attributeName = parts[0].trim();
            String requiredValue = parts[1].trim();

            try {
                return userAttributeService.userHasAttributeValue(
                        operatingUser.getUserId(),
                        attributeName,
                        requiredValue
                );
            } catch (Exception e) {
                return false;
            }
        }
    }

    @Test
    void checkCustomAttribute_WithWhitespace_TrimsCorrectly() {
        // Arrange
        String customAttr = " department = engineering ";
        when(userAttributeService.userHasAttributeValue("user-123", "department", "engineering"))
                .thenReturn(true);

        // Act
        boolean result = aspectHelper.checkCustomAttribute(testUser, customAttr);

        // Assert
        assertTrue(result);
        verify(userAttributeService).userHasAttributeValue("user-123", "department", "engineering");
    }

    @Test
    void checkCustomAttribute_WithMultipleEqualsSign_HandlesCorrectly() {
        // Arrange
        String customAttr = "key=value=with=equals";
        when(userAttributeService.userHasAttributeValue("user-123", "key", "value=with=equals"))
                .thenReturn(true);

        // Act
        boolean result = aspectHelper.checkCustomAttribute(testUser, customAttr);

        // Assert
        assertTrue(result);
        verify(userAttributeService).userHasAttributeValue("user-123", "key", "value=with=equals");
    }

    @Test
    void checkCustomAttribute_WithServiceException_ReturnsFalse() {
        // Arrange
        String customAttr = "department=engineering";
        when(userAttributeService.userHasAttributeValue("user-123", "department", "engineering"))
                .thenThrow(new RuntimeException("Database error"));

        // Act
        boolean result = aspectHelper.checkCustomAttribute(testUser, customAttr);

        // Assert
        assertFalse(result);
    }

    @Test
    void checkCustomAttribute_WithComplexAttributeName_WorksCorrectly() {
        // Arrange
        String customAttr = "clearance_level=top_secret";
        when(userAttributeService.userHasAttributeValue("user-123", "clearance_level", "top_secret"))
                .thenReturn(true);

        // Act
        boolean result = aspectHelper.checkCustomAttribute(testUser, customAttr);

        // Assert
        assertTrue(result);
        verify(userAttributeService).userHasAttributeValue("user-123", "clearance_level", "top_secret");
    }
}
