package io.sentrius.sso.core.services.abac;

import io.sentrius.sso.core.model.abac.AttributeAssignment;
import io.sentrius.sso.core.model.abac.AttributeDefinition;
import io.sentrius.sso.core.repository.abac.AttributeAssignmentRepository;
import io.sentrius.sso.core.repository.abac.AttributeDefinitionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AttributeManagementServiceTest {

    @Mock
    private AttributeDefinitionRepository definitionRepository;
    
    @Mock
    private AttributeAssignmentRepository assignmentRepository;
    
    @Mock
    private io.sentrius.sso.core.repository.UserAttributeRepository userAttributeRepository;
    
    @Mock
    private io.sentrius.sso.core.services.security.KeycloakService keycloakService;
    
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
    
    @Test
    void testGetOrCreateAttributeDefinition_ExistingDefinition_ReturnsExisting() {
        // Arrange
        AttributeDefinition existing = AttributeDefinition.builder()
                .id(1L)
                .attributeName("department")
                .attributeScope(AttributeDefinition.AttributeScope.SUBJECT)
                .attributeType(AttributeDefinition.AttributeType.STRING)
                .build();
        
        when(definitionRepository.findByAttributeNameAndAttributeScope(
                "department", AttributeDefinition.AttributeScope.SUBJECT))
                .thenReturn(Optional.of(existing));
        
        // Act
        AttributeDefinition result = attributeManagementService.getOrCreateAttributeDefinition(
                "department", 
                AttributeDefinition.AttributeScope.SUBJECT,
                AttributeDefinition.AttributeType.STRING
        );
        
        // Assert
        assertEquals(existing.getId(), result.getId());
        verify(definitionRepository, never()).save(any());
    }
    
    @Test
    void testGetOrCreateAttributeDefinition_NewDefinition_CreatesNew() {
        // Arrange
        when(definitionRepository.findByAttributeNameAndAttributeScope(anyString(), any()))
                .thenReturn(Optional.empty());
        
        AttributeDefinition created = AttributeDefinition.builder()
                .id(1L)
                .attributeName("clearance_level")
                .attributeScope(AttributeDefinition.AttributeScope.SUBJECT)
                .attributeType(AttributeDefinition.AttributeType.STRING)
                .build();
        
        when(definitionRepository.save(any(AttributeDefinition.class)))
                .thenReturn(created);
        
        // Act
        AttributeDefinition result = attributeManagementService.getOrCreateAttributeDefinition(
                "clearance_level",
                AttributeDefinition.AttributeScope.SUBJECT,
                AttributeDefinition.AttributeType.STRING
        );
        
        // Assert
        assertNotNull(result);
        verify(definitionRepository).save(any(AttributeDefinition.class));
    }
    
    @Test
    void testAssignAttribute_NewAssignment_CreatesAssignment() {
        // Arrange
        AttributeDefinition definition = createAttributeDefinition();
        
        when(assignmentRepository.findByAttributeDefinitionAndTargetTypeAndTargetIdAndIsActiveTrue(
                any(), any(), anyString()))
                .thenReturn(Optional.empty());
        
        AttributeAssignment created = createAttributeAssignment(definition);
        when(assignmentRepository.save(any(AttributeAssignment.class)))
                .thenReturn(created);
        
        // Act
        AttributeAssignment result = attributeManagementService.assignAttribute(
                definition,
                AttributeAssignment.TargetType.USER,
                "user123",
                "engineering"
        );
        
        // Assert
        assertNotNull(result);
        verify(assignmentRepository).save(any(AttributeAssignment.class));
    }
    
    @Test
    void testAssignAttribute_ExistingAssignment_UpdatesValue() {
        // Arrange
        AttributeDefinition definition = createAttributeDefinition();
        AttributeAssignment existing = createAttributeAssignment(definition);
        existing.setAttributeValue("sales");
        
        when(assignmentRepository.findByAttributeDefinitionAndTargetTypeAndTargetIdAndIsActiveTrue(
                any(), any(), anyString()))
                .thenReturn(Optional.of(existing));
        when(assignmentRepository.save(any(AttributeAssignment.class)))
                .thenReturn(existing);
        
        // Act
        AttributeAssignment result = attributeManagementService.assignAttribute(
                definition,
                AttributeAssignment.TargetType.USER,
                "user123",
                "engineering"
        );
        
        // Assert
        assertEquals("engineering", result.getAttributeValue());
        verify(assignmentRepository).save(existing);
    }
    
    @Test
    void testSyncUserAttributesFromKeycloak_CreatesAttributesAndAssignments() {
        // Arrange
        String userId = "user123";
        Map<String, String> keycloakAttributes = new HashMap<>();
        keycloakAttributes.put("department", "engineering");
        keycloakAttributes.put("clearance_level", "high");
        
        when(definitionRepository.findByAttributeNameAndAttributeScope(anyString(), any()))
                .thenReturn(Optional.empty());
        
        AttributeDefinition mockDef = createAttributeDefinition();
        when(definitionRepository.save(any(AttributeDefinition.class)))
                .thenReturn(mockDef);
        when(assignmentRepository.findByAttributeDefinitionAndTargetTypeAndTargetIdAndIsActiveTrue(
                any(), any(), anyString()))
                .thenReturn(Optional.empty());
        when(assignmentRepository.save(any(AttributeAssignment.class)))
                .thenReturn(createAttributeAssignment(mockDef));
        
        // Act
        attributeManagementService.syncUserAttributesFromKeycloak(userId, keycloakAttributes);
        
        // Assert
        // Each attribute causes: 1 save for creation + 1 save for marking as synced = 4 total saves for 2 attributes
        verify(definitionRepository, atLeast(2)).save(any(AttributeDefinition.class));
        verify(assignmentRepository, times(2)).save(any(AttributeAssignment.class));
    }
    
    @Test
    void testGetUserAttributes_ReturnsActiveAssignments() {
        // Arrange
        String userId = "user123";
        List<AttributeAssignment> assignments = List.of(
                createAttributeAssignment(createAttributeDefinition()),
                createAttributeAssignment(createAttributeDefinition())
        );
        
        when(assignmentRepository.findCurrentlyValidAssignments(
                AttributeAssignment.TargetType.USER, userId))
                .thenReturn(assignments);
        
        // Act
        List<AttributeAssignment> result = attributeManagementService.getUserAttributes(userId);
        
        // Assert
        assertEquals(2, result.size());
    }
    
    @Test
    void testRemoveAttributeAssignment_DeactivatesAssignment() {
        // Arrange
        AttributeAssignment assignment = createAttributeAssignment(createAttributeDefinition());
        assignment.setIsActive(true);
        
        when(assignmentRepository.findById(1L))
                .thenReturn(Optional.of(assignment));
        when(assignmentRepository.save(any(AttributeAssignment.class)))
                .thenReturn(assignment);
        when(userAttributeRepository.findByUserIdAndAttributeNameAndIsActiveTrue(anyString(), anyString()))
                .thenReturn(Optional.empty());
        
        // Act
        boolean result = attributeManagementService.removeAttributeAssignment(1L);
        
        // Assert
        assertTrue(result);
        assertFalse(assignment.getIsActive());
        verify(assignmentRepository).save(assignment);
    }
    
    @Test
    void testAssignAttribute_UserTargetType_AlsoCreatesUserAttribute() {
        // Arrange
        AttributeDefinition definition = createAttributeDefinition();
        
        when(assignmentRepository.findByAttributeDefinitionAndTargetTypeAndTargetIdAndIsActiveTrue(
                any(), any(), anyString()))
                .thenReturn(Optional.empty());
        
        AttributeAssignment savedAssignment = createAttributeAssignment(definition);
        when(assignmentRepository.save(any(AttributeAssignment.class)))
                .thenReturn(savedAssignment);
        
        when(userAttributeRepository.findByUserIdAndAttributeNameAndIsActiveTrue(anyString(), anyString()))
                .thenReturn(Optional.empty());
        
        io.sentrius.sso.core.model.users.UserAttribute savedUserAttr = 
                io.sentrius.sso.core.model.users.UserAttribute.builder()
                .id(1L)
                .userId("user123")
                .attributeName("department")
                .attributeValue("engineering")
                .attributeType("STRING")
                .source("SENTRIUS")
                .isActive(true)
                .syncedFromKeycloak(false)
                .build();
        
        when(userAttributeRepository.save(any(io.sentrius.sso.core.model.users.UserAttribute.class)))
                .thenReturn(savedUserAttr);
        
        // Act
        AttributeAssignment result = attributeManagementService.assignAttribute(
                definition,
                AttributeAssignment.TargetType.USER,
                "user123",
                "engineering",
                AttributeAssignment.AssignmentSource.SENTRIUS,
                false
        );
        
        // Assert
        assertNotNull(result);
        verify(assignmentRepository).save(any(AttributeAssignment.class));
        // Verify that UserAttribute was also saved
        verify(userAttributeRepository).save(any(io.sentrius.sso.core.model.users.UserAttribute.class));
    }
    
    @Test
    void testAssignAttribute_NonUserTargetType_DoesNotCreateUserAttribute() {
        // Arrange
        AttributeDefinition definition = createAttributeDefinition();
        
        when(assignmentRepository.findByAttributeDefinitionAndTargetTypeAndTargetIdAndIsActiveTrue(
                any(), any(), anyString()))
                .thenReturn(Optional.empty());
        
        AttributeAssignment savedAssignment = AttributeAssignment.builder()
                .id(1L)
                .attributeDefinition(definition)
                .targetType(AttributeAssignment.TargetType.ENDPOINT)
                .targetId("/api/data")
                .attributeValue("high")
                .source(AttributeAssignment.AssignmentSource.SENTRIUS)
                .isActive(true)
                .build();
        
        when(assignmentRepository.save(any(AttributeAssignment.class)))
                .thenReturn(savedAssignment);
        
        // Act
        AttributeAssignment result = attributeManagementService.assignAttribute(
                definition,
                AttributeAssignment.TargetType.ENDPOINT,
                "/api/data",
                "high",
                AttributeAssignment.AssignmentSource.SENTRIUS,
                false
        );
        
        // Assert
        assertNotNull(result);
        verify(assignmentRepository).save(any(AttributeAssignment.class));
        // Verify that UserAttribute was NOT saved for non-USER target types
        verify(userAttributeRepository, never()).save(any());
    }
    
    @Test
    void testRemoveAttributeAssignment_UserTargetType_AlsoDeactivatesUserAttribute() {
        // Arrange
        AttributeAssignment assignment = createAttributeAssignment(createAttributeDefinition());
        assignment.setIsActive(true);
        
        when(assignmentRepository.findById(1L))
                .thenReturn(Optional.of(assignment));
        when(assignmentRepository.save(any(AttributeAssignment.class)))
                .thenReturn(assignment);
        
        io.sentrius.sso.core.model.users.UserAttribute existingUserAttr = 
                io.sentrius.sso.core.model.users.UserAttribute.builder()
                .id(1L)
                .userId("user123")
                .attributeName("department")
                .attributeValue("engineering")
                .isActive(true)
                .build();
        
        when(userAttributeRepository.findByUserIdAndAttributeNameAndIsActiveTrue("user123", "department"))
                .thenReturn(Optional.of(existingUserAttr));
        when(userAttributeRepository.save(any(io.sentrius.sso.core.model.users.UserAttribute.class)))
                .thenReturn(existingUserAttr);
        
        // Act
        boolean result = attributeManagementService.removeAttributeAssignment(1L);
        
        // Assert
        assertTrue(result);
        assertFalse(assignment.getIsActive());
        verify(assignmentRepository).save(assignment);
        // Verify that UserAttribute was also deactivated
        verify(userAttributeRepository).save(any(io.sentrius.sso.core.model.users.UserAttribute.class));
        assertFalse(existingUserAttr.getIsActive());
    }
    
    // Helper methods
    private AttributeDefinition createAttributeDefinition() {
        return AttributeDefinition.builder()
                .id(1L)
                .attributeName("department")
                .attributeScope(AttributeDefinition.AttributeScope.SUBJECT)
                .attributeType(AttributeDefinition.AttributeType.STRING)
                .syncedWithKeycloak(false)
                .isActive(true)
                .build();
    }
    
    private AttributeAssignment createAttributeAssignment(AttributeDefinition definition) {
        return AttributeAssignment.builder()
                .id(1L)
                .attributeDefinition(definition)
                .targetType(AttributeAssignment.TargetType.USER)
                .targetId("user123")
                .attributeValue("engineering")
                .source(AttributeAssignment.AssignmentSource.SENTRIUS)
                .isActive(true)
                .build();
    }
}
