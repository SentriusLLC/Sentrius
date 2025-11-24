package io.sentrius.sso.controllers.api;

import io.sentrius.sso.core.config.SystemOptions;
import io.sentrius.sso.core.model.ConfigurationOption;
import io.sentrius.sso.core.services.ErrorOutputService;
import io.sentrius.sso.core.services.PropertyOverrideService;
import io.sentrius.sso.core.services.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PropertyOverrideControllerTest {

    @Mock
    private UserService userService;

    @Mock
    private SystemOptions systemOptions;

    @Mock
    private ErrorOutputService errorOutputService;

    @Mock
    private PropertyOverrideService propertyOverrideService;

    private PropertyOverrideController propertyOverrideController;

    @BeforeEach
    void setUp() {
        propertyOverrideController = new PropertyOverrideController(
            userService,
            systemOptions,
            errorOutputService,
            propertyOverrideService
        );
    }

    @Test
    void getAllProperties_returnsPropertiesMap() {
        // Given
        Map<String, PropertyOverrideService.PropertyInfo> mockProperties = new HashMap<>();
        PropertyOverrideService.PropertyInfo info = PropertyOverrideService.PropertyInfo.builder()
            .propertyName("test.property")
            .fileValue("file-value")
            .databaseValue("db-value")
            .currentValue("db-value")
            .hasOverride(true)
            .build();
        mockProperties.put("test.property", info);
        
        when(propertyOverrideService.getAllProperties()).thenReturn(mockProperties);
        
        // When
        ResponseEntity<Map<String, PropertyOverrideService.PropertyInfo>> response = 
            propertyOverrideController.getAllProperties();
        
        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().containsKey("test.property"));
        verify(propertyOverrideService).getAllProperties();
    }

    @Test
    void getProperty_whenPropertyExists_returnsValue() {
        // Given
        String propertyName = "test.property";
        String propertyValue = "test-value";
        
        when(propertyOverrideService.getProperty(propertyName)).thenReturn(propertyValue);
        
        // When
        ResponseEntity<Map<String, String>> response = 
            propertyOverrideController.getProperty(propertyName);
        
        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(propertyName, response.getBody().get("propertyName"));
        assertEquals(propertyValue, response.getBody().get("value"));
        verify(propertyOverrideService).getProperty(propertyName);
    }

    @Test
    void getProperty_whenPropertyNotFound_returnsNotFound() {
        // Given
        String propertyName = "nonexistent.property";
        
        when(propertyOverrideService.getProperty(propertyName)).thenReturn(null);
        
        // When
        ResponseEntity<Map<String, String>> response = 
            propertyOverrideController.getProperty(propertyName);
        
        // Then
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        verify(propertyOverrideService).getProperty(propertyName);
    }

    @Test
    void setPropertyOverride_whenValid_savesSuccessfully() {
        // Given
        PropertyOverrideController.PropertyUpdateRequest request = 
            new PropertyOverrideController.PropertyUpdateRequest("test.property", "new-value");
        
        ConfigurationOption savedOption = ConfigurationOption.builder()
            .id(1L)
            .configurationName("test.property")
            .configurationValue("new-value")
            .build();
        
        when(propertyOverrideService.setPropertyOverride("test.property", "new-value"))
            .thenReturn(savedOption);
        
        // When
        ResponseEntity<Map<String, String>> response = 
            propertyOverrideController.setPropertyOverride(request);
        
        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Property override saved successfully", response.getBody().get("message"));
        assertEquals("test.property", response.getBody().get("propertyName"));
        verify(propertyOverrideService).setPropertyOverride("test.property", "new-value");
    }

    @Test
    void setPropertyOverride_whenSecuritySensitive_returnsForbidden() {
        // Given
        PropertyOverrideController.PropertyUpdateRequest request = 
            new PropertyOverrideController.PropertyUpdateRequest("database.password", "secret");
        
        when(propertyOverrideService.setPropertyOverride("database.password", "secret"))
            .thenThrow(new SecurityException("Cannot override security-sensitive property: database.password"));
        
        // When
        ResponseEntity<Map<String, String>> response = 
            propertyOverrideController.setPropertyOverride(request);
        
        // Then
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().get("error").contains("security-sensitive"));
        verify(propertyOverrideService).setPropertyOverride("database.password", "secret");
    }

    @Test
    void deletePropertyOverride_whenValid_deletesSuccessfully() {
        // Given
        String propertyName = "test.property";
        
        doNothing().when(propertyOverrideService).removePropertyOverride(propertyName);
        
        // When
        ResponseEntity<Map<String, String>> response = 
            propertyOverrideController.deletePropertyOverride(propertyName);
        
        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Property override removed successfully", response.getBody().get("message"));
        assertEquals(propertyName, response.getBody().get("propertyName"));
        verify(propertyOverrideService).removePropertyOverride(propertyName);
    }

    @Test
    void deletePropertyOverride_whenSecuritySensitive_returnsForbidden() {
        // Given
        String propertyName = "keystore.password";
        
        doThrow(new SecurityException("Cannot remove security-sensitive property: keystore.password"))
            .when(propertyOverrideService).removePropertyOverride(propertyName);
        
        // When
        ResponseEntity<Map<String, String>> response = 
            propertyOverrideController.deletePropertyOverride(propertyName);
        
        // Then
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().get("error").contains("security-sensitive"));
        verify(propertyOverrideService).removePropertyOverride(propertyName);
    }
}
