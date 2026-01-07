package io.sentrius.sso.core.services.abac;

import io.sentrius.sso.core.model.abac.AttributeAssignment;
import io.sentrius.sso.core.model.abac.AttributeDefinition;
import io.sentrius.sso.core.model.users.UserAttribute;
import io.sentrius.sso.core.repository.UserAttributeRepository;
import io.sentrius.sso.core.repository.abac.AttributeAssignmentRepository;
import io.sentrius.sso.core.repository.abac.AttributeDefinitionRepository;
import io.sentrius.sso.core.services.security.KeycloakService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Integration test to verify that user attributes persist correctly 
 * to both AttributeAssignment and UserAttribute tables for access control.
 */
@ExtendWith(MockitoExtension.class)
class UserAttributeIntegrationTest {

    @Mock
    private AttributeDefinitionRepository definitionRepository;
    
    @Mock
    private AttributeAssignmentRepository assignmentRepository;
    
    @Mock
    private UserAttributeRepository userAttributeRepository;
    
    @Mock
    private KeycloakService keycloakService;
    
    private AttributeManagementService attributeManagementService;
    
    @BeforeEach
    void setUp() {
        attributeManagementService = new AttributeManagementService(
                definitionRepository,
                assignmentRepository,
                userAttributeRepository,
                keycloakService
        );
    }
    
    /**
     * Test that user attributes created through ABAC UI are accessible 
     * for document visibility expressions and access control checks.
     * 
     * This verifies the fix for the issue where attributes were stored in
     * AttributeAssignment table but not in UserAttribute table, causing
     * document access control to fail.
     */
    @Test
    void testUserAttributeCreation_PersistsToBothTables() {
        // Arrange - Setup attribute definition
        AttributeDefinition definition = AttributeDefinition.builder()
                .id(1L)
                .attributeName("clearance_level")
                .attributeScope(AttributeDefinition.AttributeScope.SUBJECT)
                .attributeType(AttributeDefinition.AttributeType.STRING)
                .isActive(true)
                .build();
        
        lenient().when(definitionRepository.findByAttributeNameAndAttributeScope(
                "clearance_level", AttributeDefinition.AttributeScope.SUBJECT))
                .thenReturn(Optional.of(definition));
        
        // Setup AttributeAssignment repository
        when(assignmentRepository.findByAttributeDefinitionAndTargetTypeAndTargetIdAndIsActiveTrue(
                any(), any(), anyString()))
                .thenReturn(Optional.empty());
        
        AttributeAssignment savedAssignment = AttributeAssignment.builder()
                .id(1L)
                .attributeDefinition(definition)
                .targetType(AttributeAssignment.TargetType.USER)
                .targetId("john@example.com")
                .attributeValue("high")
                .source(AttributeAssignment.AssignmentSource.SENTRIUS)
                .isActive(true)
                .build();
        
        when(assignmentRepository.save(any(AttributeAssignment.class)))
                .thenReturn(savedAssignment);
        
        // Setup UserAttribute repository
        when(userAttributeRepository.findByUserIdAndAttributeNameAndIsActiveTrue(
                "john@example.com", "clearance_level"))
                .thenReturn(Optional.empty());
        
        UserAttribute savedUserAttribute = UserAttribute.builder()
                .id(1L)
                .userId("john@example.com")
                .attributeName("clearance_level")
                .attributeValue("high")
                .attributeType("STRING")
                .source("SENTRIUS")
                .isActive(true)
                .syncedFromKeycloak(false)
                .build();
        
        when(userAttributeRepository.save(any(UserAttribute.class)))
                .thenReturn(savedUserAttribute);
        
        // Act - Assign attribute through ABAC service (as UI does)
        AttributeAssignment result = attributeManagementService.assignAttribute(
                definition,
                AttributeAssignment.TargetType.USER,
                "john@example.com",
                "high",
                AttributeAssignment.AssignmentSource.SENTRIUS,
                false
        );
        
        // Assert - Verify attribute was saved to both tables
        assertNotNull(result);
        assertEquals("john@example.com", result.getTargetId());
        assertEquals("high", result.getAttributeValue());
        
        // Verify AttributeAssignment was saved
        verify(assignmentRepository).save(any(AttributeAssignment.class));
        
        // CRITICAL: Verify UserAttribute was also saved for access control
        verify(userAttributeRepository).save(argThat(attr -> 
            attr.getUserId().equals("john@example.com") &&
            attr.getAttributeName().equals("clearance_level") &&
            attr.getAttributeValue().equals("high") &&
            attr.getIsActive() &&
            !attr.getSyncedFromKeycloak()
        ));
    }
    
    /**
     * Test that updating a user attribute updates both tables.
     */
    @Test
    void testUserAttributeUpdate_UpdatesBothTables() {
        // Arrange
        AttributeDefinition definition = AttributeDefinition.builder()
                .id(1L)
                .attributeName("department")
                .attributeScope(AttributeDefinition.AttributeScope.SUBJECT)
                .attributeType(AttributeDefinition.AttributeType.STRING)
                .isActive(true)
                .build();
        
        // Existing AttributeAssignment
        AttributeAssignment existingAssignment = AttributeAssignment.builder()
                .id(1L)
                .attributeDefinition(definition)
                .targetType(AttributeAssignment.TargetType.USER)
                .targetId("jane@example.com")
                .attributeValue("engineering")
                .source(AttributeAssignment.AssignmentSource.SENTRIUS)
                .isActive(true)
                .build();
        
        when(assignmentRepository.findByAttributeDefinitionAndTargetTypeAndTargetIdAndIsActiveTrue(
                any(), any(), anyString()))
                .thenReturn(Optional.of(existingAssignment));
        when(assignmentRepository.save(any(AttributeAssignment.class)))
                .thenReturn(existingAssignment);
        
        // Existing UserAttribute
        UserAttribute existingUserAttr = UserAttribute.builder()
                .id(1L)
                .userId("jane@example.com")
                .attributeName("department")
                .attributeValue("engineering")
                .attributeType("STRING")
                .isActive(true)
                .build();
        
        when(userAttributeRepository.findByUserIdAndAttributeNameAndIsActiveTrue(
                "jane@example.com", "department"))
                .thenReturn(Optional.of(existingUserAttr));
        when(userAttributeRepository.save(any(UserAttribute.class)))
                .thenReturn(existingUserAttr);
        
        // Act - Update attribute value
        AttributeAssignment result = attributeManagementService.assignAttribute(
                definition,
                AttributeAssignment.TargetType.USER,
                "jane@example.com",
                "sales",  // Changed from "engineering" to "sales"
                AttributeAssignment.AssignmentSource.SENTRIUS,
                false
        );
        
        // Assert
        assertNotNull(result);
        assertEquals("sales", result.getAttributeValue());
        
        // Verify both tables were updated
        verify(assignmentRepository).save(any(AttributeAssignment.class));
        verify(userAttributeRepository).save(argThat(attr ->
            attr.getAttributeValue().equals("sales")
        ));
    }
    
    /**
     * Test that deleting a user attribute deactivates it in both tables.
     */
    @Test
    void testUserAttributeDelete_DeactivatesBothTables() {
        // Arrange
        AttributeDefinition definition = AttributeDefinition.builder()
                .id(1L)
                .attributeName("temp_access")
                .attributeScope(AttributeDefinition.AttributeScope.SUBJECT)
                .attributeType(AttributeDefinition.AttributeType.STRING)
                .isActive(true)
                .build();
        
        AttributeAssignment assignment = AttributeAssignment.builder()
                .id(1L)
                .attributeDefinition(definition)
                .targetType(AttributeAssignment.TargetType.USER)
                .targetId("temp@example.com")
                .attributeValue("granted")
                .isActive(true)
                .build();
        
        UserAttribute userAttr = UserAttribute.builder()
                .id(1L)
                .userId("temp@example.com")
                .attributeName("temp_access")
                .attributeValue("granted")
                .isActive(true)
                .build();
        
        when(assignmentRepository.findById(1L))
                .thenReturn(Optional.of(assignment));
        when(assignmentRepository.save(any(AttributeAssignment.class)))
                .thenReturn(assignment);
        when(userAttributeRepository.findByUserIdAndAttributeNameAndIsActiveTrue(
                "temp@example.com", "temp_access"))
                .thenReturn(Optional.of(userAttr));
        when(userAttributeRepository.save(any(UserAttribute.class)))
                .thenReturn(userAttr);
        
        // Act - Delete attribute
        boolean result = attributeManagementService.removeAttributeAssignment(1L);
        
        // Assert
        assertTrue(result);
        assertFalse(assignment.getIsActive());
        assertFalse(userAttr.getIsActive());
        
        // Verify both tables were updated to deactivate
        verify(assignmentRepository).save(assignment);
        verify(userAttributeRepository).save(userAttr);
    }
    
    /**
     * Test that non-USER target types do not create UserAttribute entries.
     */
    @Test
    void testEndpointAttribute_OnlyCreatesAttributeAssignment() {
        // Arrange
        AttributeDefinition definition = AttributeDefinition.builder()
                .id(1L)
                .attributeName("data_classification")
                .attributeScope(AttributeDefinition.AttributeScope.RESOURCE)
                .attributeType(AttributeDefinition.AttributeType.STRING)
                .isActive(true)
                .build();
        
        when(assignmentRepository.findByAttributeDefinitionAndTargetTypeAndTargetIdAndIsActiveTrue(
                any(), any(), anyString()))
                .thenReturn(Optional.empty());
        
        AttributeAssignment savedAssignment = AttributeAssignment.builder()
                .id(1L)
                .attributeDefinition(definition)
                .targetType(AttributeAssignment.TargetType.ENDPOINT)
                .targetId("/api/sensitive")
                .attributeValue("confidential")
                .source(AttributeAssignment.AssignmentSource.SENTRIUS)
                .isActive(true)
                .build();
        
        when(assignmentRepository.save(any(AttributeAssignment.class)))
                .thenReturn(savedAssignment);
        
        // Act - Assign attribute to endpoint (not user)
        AttributeAssignment result = attributeManagementService.assignAttribute(
                definition,
                AttributeAssignment.TargetType.ENDPOINT,
                "/api/sensitive",
                "confidential",
                AttributeAssignment.AssignmentSource.SENTRIUS,
                false
        );
        
        // Assert
        assertNotNull(result);
        assertEquals(AttributeAssignment.TargetType.ENDPOINT, result.getTargetType());
        
        // Verify AttributeAssignment was saved
        verify(assignmentRepository).save(any(AttributeAssignment.class));
        
        // Verify UserAttribute was NOT created for non-user targets
        verify(userAttributeRepository, never()).save(any(UserAttribute.class));
    }
}
