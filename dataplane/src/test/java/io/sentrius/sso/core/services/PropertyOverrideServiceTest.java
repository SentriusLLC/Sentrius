package io.sentrius.sso.core.services;

import io.sentrius.sso.core.model.ConfigurationOption;
import io.sentrius.sso.core.repository.ConfigurationOptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PropertyOverrideServiceTest {

    @Mock
    private ConfigurationOptionRepository configurationOptionRepository;

    @Mock
    private ConfigurableEnvironment environment;

    private PropertyOverrideService propertyOverrideService;

    @BeforeEach
    void setUp() {
        propertyOverrideService = new PropertyOverrideService(
            configurationOptionRepository,
            environment
        );
    }

    @Test
    void getProperty_whenDatabaseOverrideExists_returnsDbValue() {
        // Given
        String propertyName = "test.property";
        String dbValue = "database-value";
        
        ConfigurationOption option = ConfigurationOption.builder()
            .configurationName(propertyName)
            .configurationValue(dbValue)
            .build();
        
        when(configurationOptionRepository.findLatestByConfigurationName(propertyName))
            .thenReturn(Optional.of(option));
        
        // When
        String result = propertyOverrideService.getProperty(propertyName);
        
        // Then
        assertEquals(dbValue, result);
        verify(configurationOptionRepository).findLatestByConfigurationName(propertyName);
    }

    @Test
    void getProperty_whenNoDatabaseOverride_returnsFileValue() {
        // Given
        String propertyName = "test.property";
        String fileValue = "file-value";
        
        when(configurationOptionRepository.findLatestByConfigurationName(propertyName))
            .thenReturn(Optional.empty());
        when(environment.getProperty(propertyName)).thenReturn(fileValue);
        
        // When
        String result = propertyOverrideService.getProperty(propertyName);
        
        // Then
        assertEquals(fileValue, result);
        verify(environment).getProperty(propertyName);
    }

    @Test
    void getProperty_whenSecuritySensitive_returnsNull() {
        // Given
        String propertyName = "database.password";
        
        // When
        String result = propertyOverrideService.getProperty(propertyName);
        
        // Then
        assertNull(result);
        verify(configurationOptionRepository, never()).findLatestByConfigurationName(any());
    }

    @Test
    void setPropertyOverride_whenValidProperty_savesToDatabase() {
        // Given
        String propertyName = "test.property";
        String value = "new-value";
        
        ConfigurationOption savedOption = ConfigurationOption.builder()
            .id(1L)
            .configurationName(propertyName)
            .configurationValue(value)
            .build();
        
        when(configurationOptionRepository.save(any(ConfigurationOption.class)))
            .thenReturn(savedOption);
        
        // When
        ConfigurationOption result = propertyOverrideService.setPropertyOverride(propertyName, value);
        
        // Then
        assertNotNull(result);
        assertEquals(propertyName, result.getConfigurationName());
        assertEquals(value, result.getConfigurationValue());
        verify(configurationOptionRepository).save(any(ConfigurationOption.class));
    }

    @Test
    void setPropertyOverride_whenSecuritySensitive_throwsException() {
        // Given
        String propertyName = "spring.security.oauth2.client.registration.keycloak.client-secret";
        String value = "secret-value";
        
        // When/Then
        assertThrows(SecurityException.class, () -> {
            propertyOverrideService.setPropertyOverride(propertyName, value);
        });
        
        verify(configurationOptionRepository, never()).save(any());
    }

    @Test
    void removePropertyOverride_whenOverrideExists_deletesIt() {
        // Given
        String propertyName = "test.property";
        
        ConfigurationOption option = ConfigurationOption.builder()
            .id(1L)
            .configurationName(propertyName)
            .configurationValue("value")
            .build();
        
        when(configurationOptionRepository.findLatestByConfigurationName(propertyName))
            .thenReturn(Optional.of(option));
        
        // When
        propertyOverrideService.removePropertyOverride(propertyName);
        
        // Then
        verify(configurationOptionRepository).findLatestByConfigurationName(propertyName);
        verify(configurationOptionRepository).delete(option);
    }

    @Test
    void removePropertyOverride_whenNoOverride_doesNothing() {
        // Given
        String propertyName = "test.property";
        
        when(configurationOptionRepository.findLatestByConfigurationName(propertyName))
            .thenReturn(Optional.empty());
        
        // When
        propertyOverrideService.removePropertyOverride(propertyName);
        
        // Then
        verify(configurationOptionRepository).findLatestByConfigurationName(propertyName);
        verify(configurationOptionRepository, never()).delete(any());
    }

    @Test
    void removePropertyOverride_whenSecuritySensitive_throwsException() {
        // Given
        String propertyName = "keystore.password";
        
        // When/Then
        assertThrows(SecurityException.class, () -> {
            propertyOverrideService.removePropertyOverride(propertyName);
        });
        
        verify(configurationOptionRepository, never()).findLatestByConfigurationName(any());
        verify(configurationOptionRepository, never()).delete(any());
    }

    @Test
    void getAllProperties_includesPropertiesFromEnvironment() {
        // Given
        Map<String, Object> properties = new HashMap<>();
        properties.put("test.property1", "value1");
        properties.put("test.property2", "value2");
        
        MapPropertySource propertySource = new MapPropertySource("test", properties);
        
        when(environment.getPropertySources()).thenReturn(
            new org.springframework.core.env.MutablePropertySources() {{
                addFirst(propertySource);
            }}
        );
        
        when(environment.getProperty("test.property1")).thenReturn("value1");
        when(environment.getProperty("test.property2")).thenReturn("value2");
        when(configurationOptionRepository.findLatestByConfigurationName(any()))
            .thenReturn(Optional.empty());
        when(configurationOptionRepository.findAll()).thenReturn(Collections.emptyList());
        
        // When
        Map<String, PropertyOverrideService.PropertyInfo> result = 
            propertyOverrideService.getAllProperties();
        
        // Then
        assertNotNull(result);
        assertTrue(result.containsKey("test.property1"));
        assertTrue(result.containsKey("test.property2"));
    }

    @Test
    void getAllProperties_excludesSecuritySensitiveProperties() {
        // Given
        Map<String, Object> properties = new HashMap<>();
        properties.put("test.property", "value");
        properties.put("database.password", "secret");
        properties.put("keystore.file", "path");
        
        MapPropertySource propertySource = new MapPropertySource("test", properties);
        
        when(environment.getPropertySources()).thenReturn(
            new org.springframework.core.env.MutablePropertySources() {{
                addFirst(propertySource);
            }}
        );
        
        when(environment.getProperty("test.property")).thenReturn("value");
        when(configurationOptionRepository.findLatestByConfigurationName(any()))
            .thenReturn(Optional.empty());
        when(configurationOptionRepository.findAll()).thenReturn(Collections.emptyList());
        
        // When
        Map<String, PropertyOverrideService.PropertyInfo> result = 
            propertyOverrideService.getAllProperties();
        
        // Then
        assertNotNull(result);
        assertTrue(result.containsKey("test.property"));
        assertFalse(result.containsKey("database.password"));
        assertFalse(result.containsKey("keystore.file"));
    }

    @Test
    void getAllProperties_marksOverriddenProperties() {
        // Given
        String propertyName = "test.property";
        Map<String, Object> properties = new HashMap<>();
        properties.put(propertyName, "file-value");
        
        MapPropertySource propertySource = new MapPropertySource("test", properties);
        
        ConfigurationOption dbOption = ConfigurationOption.builder()
            .configurationName(propertyName)
            .configurationValue("db-value")
            .build();
        
        when(environment.getPropertySources()).thenReturn(
            new org.springframework.core.env.MutablePropertySources() {{
                addFirst(propertySource);
            }}
        );
        
        when(environment.getProperty(propertyName)).thenReturn("file-value");
        when(configurationOptionRepository.findLatestByConfigurationName(propertyName))
            .thenReturn(Optional.of(dbOption));
        when(configurationOptionRepository.findAll()).thenReturn(Collections.emptyList());
        
        // When
        Map<String, PropertyOverrideService.PropertyInfo> result = 
            propertyOverrideService.getAllProperties();
        
        // Then
        assertNotNull(result);
        assertTrue(result.containsKey(propertyName));
        PropertyOverrideService.PropertyInfo info = result.get(propertyName);
        assertTrue(info.isHasOverride());
        assertEquals("db-value", info.getCurrentValue());
        assertEquals("file-value", info.getFileValue());
        assertEquals("db-value", info.getDatabaseValue());
    }
}
