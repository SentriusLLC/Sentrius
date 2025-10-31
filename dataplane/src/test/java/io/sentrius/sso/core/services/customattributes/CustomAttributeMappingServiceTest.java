package io.sentrius.sso.core.services.customattributes;

import io.sentrius.sso.core.model.customattributes.CustomAttributeMapping;
import io.sentrius.sso.core.repository.customattributes.CustomAttributeMappingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomAttributeMappingServiceTest {

    @Mock
    private CustomAttributeMappingRepository repository;

    @InjectMocks
    private CustomAttributeMappingService service;

    private CustomAttributeMapping testMapping;

    @BeforeEach
    void setUp() {
        testMapping = CustomAttributeMapping.builder()
                .id(1L)
                .endpoint("/api/v1/chat/**")
                .attributeName("department")
                .requiredValue("engineering")
                .description("Limit chat access to engineering department")
                .isActive(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Test
    void getAllMappings_ReturnsActiveMappings() {
        // Arrange
        List<CustomAttributeMapping> mappings = Arrays.asList(testMapping);
        when(repository.findByIsActiveTrue()).thenReturn(mappings);

        // Act
        List<CustomAttributeMapping> result = service.getAllMappings();

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(testMapping, result.get(0));
        verify(repository).findByIsActiveTrue();
    }

    @Test
    void getMappingsByEndpoint_ReturnsMatchingMappings() {
        // Arrange
        String endpoint = "/api/v1/chat/**";
        List<CustomAttributeMapping> mappings = Arrays.asList(testMapping);
        when(repository.findByEndpointAndIsActiveTrue(endpoint)).thenReturn(mappings);

        // Act
        List<CustomAttributeMapping> result = service.getMappingsByEndpoint(endpoint);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(testMapping, result.get(0));
        verify(repository).findByEndpointAndIsActiveTrue(endpoint);
    }

    @Test
    void createMapping_WithValidData_CreatesMapping() {
        // Arrange
        when(repository.save(any(CustomAttributeMapping.class))).thenReturn(testMapping);

        // Act
        CustomAttributeMapping result = service.createMapping(
                "/api/v1/chat/**",
                "department",
                "engineering",
                "Limit chat access to engineering department"
        );

        // Assert
        assertNotNull(result);
        assertEquals(testMapping.getEndpoint(), result.getEndpoint());
        assertEquals(testMapping.getAttributeName(), result.getAttributeName());
        assertEquals(testMapping.getRequiredValue(), result.getRequiredValue());
        verify(repository).save(any(CustomAttributeMapping.class));
    }

    @Test
    void createMapping_WithEmptyEndpoint_ThrowsException() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            service.createMapping("", "department", "engineering", "Test");
        });
        
        verify(repository, never()).save(any());
    }

    @Test
    void createMapping_WithEmptyAttributeName_ThrowsException() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            service.createMapping("/api/v1/test", "", "engineering", "Test");
        });
        
        verify(repository, never()).save(any());
    }

    @Test
    void createMapping_WithEmptyRequiredValue_ThrowsException() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            service.createMapping("/api/v1/test", "department", "", "Test");
        });
        
        verify(repository, never()).save(any());
    }

    @Test
    void updateMapping_WithExistingId_UpdatesMapping() {
        // Arrange
        when(repository.findById(1L)).thenReturn(Optional.of(testMapping));
        when(repository.save(any(CustomAttributeMapping.class))).thenReturn(testMapping);

        // Act
        CustomAttributeMapping result = service.updateMapping(
                1L,
                "/api/v1/new-endpoint/**",
                "newAttribute",
                "newValue",
                "Updated description",
                true
        );

        // Assert
        assertNotNull(result);
        verify(repository).findById(1L);
        verify(repository).save(any(CustomAttributeMapping.class));
    }

    @Test
    void updateMapping_WithNonExistingId_ReturnsNull() {
        // Arrange
        when(repository.findById(999L)).thenReturn(Optional.empty());

        // Act
        CustomAttributeMapping result = service.updateMapping(
                999L,
                "/api/v1/test",
                "attr",
                "val",
                "desc",
                true
        );

        // Assert
        assertNull(result);
        verify(repository).findById(999L);
        verify(repository, never()).save(any());
    }

    @Test
    void deleteMapping_WithExistingId_DeactivatesMapping() {
        // Arrange
        when(repository.findById(1L)).thenReturn(Optional.of(testMapping));
        when(repository.save(any(CustomAttributeMapping.class))).thenReturn(testMapping);

        // Act
        boolean result = service.deleteMapping(1L);

        // Assert
        assertTrue(result);
        verify(repository).findById(1L);
        verify(repository).save(any(CustomAttributeMapping.class));
    }

    @Test
    void deleteMapping_WithNonExistingId_ReturnsFalse() {
        // Arrange
        when(repository.findById(999L)).thenReturn(Optional.empty());

        // Act
        boolean result = service.deleteMapping(999L);

        // Assert
        assertFalse(result);
        verify(repository).findById(999L);
        verify(repository, never()).save(any());
    }

    @Test
    void getAllEndpoints_ReturnsUniqueEndpoints() {
        // Arrange
        List<String> endpoints = Arrays.asList("/api/v1/chat/**", "/api/v1/agents/**");
        when(repository.findAllUniqueEndpoints()).thenReturn(endpoints);

        // Act
        List<String> result = service.getAllEndpoints();

        // Assert
        assertNotNull(result);
        assertEquals(2, result.size());
        verify(repository).findAllUniqueEndpoints();
    }

    @Test
    void getAllAttributeNames_ReturnsUniqueNames() {
        // Arrange
        List<String> names = Arrays.asList("department", "clearance_level");
        when(repository.findAllUniqueAttributeNames()).thenReturn(names);

        // Act
        List<String> result = service.getAllAttributeNames();

        // Assert
        assertNotNull(result);
        assertEquals(2, result.size());
        verify(repository).findAllUniqueAttributeNames();
    }

    @Test
    void getCustomAttributeStringsForEndpoint_ReturnsFormattedStrings() {
        // Arrange
        String endpoint = "/api/v1/chat/**";
        CustomAttributeMapping mapping1 = CustomAttributeMapping.builder()
                .endpoint(endpoint)
                .attributeName("department")
                .requiredValue("engineering")
                .isActive(true)
                .build();
        CustomAttributeMapping mapping2 = CustomAttributeMapping.builder()
                .endpoint(endpoint)
                .attributeName("clearance_level")
                .requiredValue("high")
                .isActive(true)
                .build();
        
        when(repository.findByEndpointAndIsActiveTrue(endpoint))
                .thenReturn(Arrays.asList(mapping1, mapping2));

        // Act
        List<String> result = service.getCustomAttributeStringsForEndpoint(endpoint);

        // Assert
        assertNotNull(result);
        assertEquals(2, result.size());
        assertTrue(result.contains("department=engineering"));
        assertTrue(result.contains("clearance_level=high"));
    }

    @Test
    void toCustomAttributeString_ReturnsCorrectFormat() {
        // Act
        String result = testMapping.toCustomAttributeString();

        // Assert
        assertEquals("department=engineering", result);
    }
}
