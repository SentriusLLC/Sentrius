package io.sentrius.sso.core.services.abac;

import io.sentrius.sso.core.model.abac.AttributeAssignment;
import io.sentrius.sso.core.model.abac.AttributeDefinition;
import io.sentrius.sso.core.repository.abac.AttributeAssignmentRepository;
import io.sentrius.sso.core.repository.abac.AttributeDefinitionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service for managing attribute definitions and assignments.
 * Integrates with Keycloak for attribute synchronization.
 */
@Slf4j
@Service
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class AttributeManagementService {

    private final AttributeDefinitionRepository definitionRepository;
    private final AttributeAssignmentRepository assignmentRepository;

    public AttributeManagementService(
            AttributeDefinitionRepository definitionRepository,
            AttributeAssignmentRepository assignmentRepository) {
        this.definitionRepository = definitionRepository;
        this.assignmentRepository = assignmentRepository;
    }

    /**
     * Get or create an attribute definition
     */
    @Transactional
    public AttributeDefinition getOrCreateAttributeDefinition(
            String attributeName,
            AttributeDefinition.AttributeScope scope,
            AttributeDefinition.AttributeType type) {
        
        return definitionRepository.findByAttributeNameAndAttributeScope(attributeName, scope)
                .orElseGet(() -> {
                    log.info("Creating new attribute definition: {} ({})", attributeName, scope);
                    AttributeDefinition def = AttributeDefinition.builder()
                            .attributeName(attributeName)
                            .attributeScope(scope)
                            .attributeType(type)
                            .isActive(true)
                            .syncedWithKeycloak(false)
                            .build();
                    return definitionRepository.save(def);
                });
    }

    /**
     * Get or create an attribute definition with string type parameter
     */
    @Transactional
    public AttributeDefinition getOrCreateAttributeDefinition(
            String attributeName,
            AttributeDefinition.AttributeScope scope,
            String dataType) {
        AttributeDefinition.AttributeType type = AttributeDefinition.AttributeType.valueOf(dataType.toUpperCase());
        return getOrCreateAttributeDefinition(attributeName, scope, type);
    }

    /**
     * Assign an attribute value to a target
     */
    @Transactional
    public AttributeAssignment assignAttribute(
            AttributeDefinition definition,
            AttributeAssignment.TargetType targetType,
            String targetId,
            String value) {
        
        return assignAttribute(definition, targetType, targetId, value, 
                AttributeAssignment.AssignmentSource.SENTRIUS, false);
    }

    /**
     * Assign an attribute with temporal validity
     */
    @Transactional
    public AttributeAssignment assignAttribute(
            String targetType,
            String targetId,
            AttributeDefinition definition,
            String value,
            LocalDateTime validFrom,
            LocalDateTime validUntil) {
        
        AttributeAssignment.TargetType type = AttributeAssignment.TargetType.valueOf(targetType);
        AttributeAssignment assignment = assignAttribute(definition, type, targetId, value,
                AttributeAssignment.AssignmentSource.SENTRIUS, false);
        
        if (validFrom != null) {
            assignment.setValidFrom(validFrom.atZone(java.time.ZoneId.systemDefault()).toInstant());
        }
        if (validUntil != null) {
            assignment.setValidUntil(validUntil.atZone(java.time.ZoneId.systemDefault()).toInstant());
        }
        
        return assignmentRepository.save(assignment);
    }

    /**
     * Assign an attribute value to a target with full control
     */
    @Transactional
    public AttributeAssignment assignAttribute(
            AttributeDefinition definition,
            AttributeAssignment.TargetType targetType,
            String targetId,
            String value,
            AttributeAssignment.AssignmentSource source,
            boolean syncedFromKeycloak) {
        
        // Check if assignment already exists
        Optional<AttributeAssignment> existing = assignmentRepository
                .findByAttributeDefinitionAndTargetTypeAndTargetIdAndIsActiveTrue(
                        definition, targetType, targetId);
        
        if (existing.isPresent()) {
            // Update existing assignment
            AttributeAssignment assignment = existing.get();
            assignment.setAttributeValue(value);
            assignment.setSource(source);
            assignment.setSyncedFromKeycloak(syncedFromKeycloak);
            log.debug("Updated attribute assignment: {} for {}:{}", 
                    definition.getAttributeName(), targetType, targetId);
            return assignmentRepository.save(assignment);
        } else {
            // Create new assignment
            AttributeAssignment assignment = AttributeAssignment.builder()
                    .attributeDefinition(definition)
                    .targetType(targetType)
                    .targetId(targetId)
                    .attributeValue(value)
                    .source(source)
                    .syncedFromKeycloak(syncedFromKeycloak)
                    .isActive(true)
                    .priority(0)
                    .build();
            log.info("Created new attribute assignment: {} = {} for {}:{}", 
                    definition.getAttributeName(), value, targetType, targetId);
            return assignmentRepository.save(assignment);
        }
    }

    /**
     * Get all active attributes for a user
     */
    @Transactional(readOnly = true)
    public List<AttributeAssignment> getUserAttributes(String userId) {
        return assignmentRepository.findCurrentlyValidAssignments(
                AttributeAssignment.TargetType.USER, userId);
    }

    /**
     * Get all active user attributes as map
     */
    @Transactional(readOnly = true)
    public Map<String, String> getUserAttributesMap(String userId) {
        List<AttributeAssignment> assignments = getUserAttributes(userId);
        return assignments.stream()
                .collect(Collectors.toMap(
                        a -> a.getAttributeDefinition().getAttributeName(),
                        AttributeAssignment::getAttributeValue,
                        (v1, v2) -> v1 // Keep first value if duplicate
                ));
    }

    /**
     * Get all active attributes for an endpoint
     */
    @Transactional(readOnly = true)
    public List<AttributeAssignment> getEndpointAttributes(String endpoint) {
        return assignmentRepository.findCurrentlyValidAssignments(
                AttributeAssignment.TargetType.ENDPOINT, endpoint);
    }

    /**
     * Get all user attribute assignments (for management UI)
     */
    @Transactional(readOnly = true)
    public List<AttributeAssignment> getAllUserAttributes() {
        return assignmentRepository.findByTargetTypeAndIsActiveTrue(AttributeAssignment.TargetType.USER);
    }

    /**
     * Sync user attributes from Keycloak
     * This method should be called when user attributes are updated in Keycloak
     */
    @Transactional
    public void syncUserAttributesFromKeycloak(String userId, java.util.Map<String, String> keycloakAttributes) {
        log.info("Syncing {} attributes from Keycloak for user: {}", keycloakAttributes.size(), userId);
        
        for (java.util.Map.Entry<String, String> entry : keycloakAttributes.entrySet()) {
            String attributeName = entry.getKey();
            String attributeValue = entry.getValue();
            
            // Get or create attribute definition
            AttributeDefinition definition = getOrCreateAttributeDefinition(
                    attributeName,
                    AttributeDefinition.AttributeScope.SUBJECT,
                    AttributeDefinition.AttributeType.STRING);
            
            // Mark definition as synced with Keycloak
            if (!definition.getSyncedWithKeycloak()) {
                definition.setSyncedWithKeycloak(true);
                definitionRepository.save(definition);
            }
            
            // Assign the attribute
            assignAttribute(definition, AttributeAssignment.TargetType.USER, userId, 
                    attributeValue, AttributeAssignment.AssignmentSource.KEYCLOAK, true);
        }
    }

    /**
     * Remove an attribute assignment
     */
    @Transactional
    public boolean removeAttributeAssignment(Long assignmentId) {
        Optional<AttributeAssignment> assignment = assignmentRepository.findById(assignmentId);
        if (assignment.isPresent()) {
            AttributeAssignment a = assignment.get();
            a.setIsActive(false);
            assignmentRepository.save(a);
            log.info("Deactivated attribute assignment: {}", assignmentId);
            return true;
        }
        return false;
    }

    /**
     * Get all attribute definitions
     */
    @Transactional(readOnly = true)
    public List<AttributeDefinition> getAllAttributeDefinitions() {
        return definitionRepository.findAllActiveOrderedByScopeAndName();
    }

    /**
     * Get attribute definitions by scope
     */
    @Transactional(readOnly = true)
    public List<AttributeDefinition> getAttributeDefinitionsByScope(AttributeDefinition.AttributeScope scope) {
        return definitionRepository.findByAttributeScopeAndIsActiveTrue(scope);
    }

    /**
     * Get definitions by scope (string parameter)
     */
    @Transactional(readOnly = true)
    public List<AttributeDefinition> getDefinitionsByScope(AttributeDefinition.AttributeScope scope) {
        return getAttributeDefinitionsByScope(scope);
    }

    /**
     * Create attribute definition
     */
    @Transactional
    public AttributeDefinition createAttributeDefinition(
            String attributeName,
            AttributeDefinition.AttributeScope scope,
            String dataType,
            String description,
            String keycloakAttributeName) {
        
        AttributeDefinition def = AttributeDefinition.builder()
                .attributeName(attributeName)
                .attributeScope(scope)
                .attributeType(AttributeDefinition.AttributeType.valueOf(dataType.toUpperCase()))
                .description(description)
                .keycloakAttributeName(keycloakAttributeName)
                .isActive(true)
                .syncedWithKeycloak(keycloakAttributeName != null)
                .build();
        
        return definitionRepository.save(def);
    }

    /**
     * Update attribute definition
     */
    @Transactional
    public AttributeDefinition updateAttributeDefinition(
            Long id,
            String description,
            String keycloakAttributeName) {
        
        AttributeDefinition def = definitionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Definition not found: " + id));
        
        if (description != null) {
            def.setDescription(description);
        }
        if (keycloakAttributeName != null) {
            def.setKeycloakAttributeName(keycloakAttributeName);
            def.setSyncedWithKeycloak(true);
        }
        
        return definitionRepository.save(def);
    }

    /**
     * Delete attribute definition
     */
    @Transactional
    public void deleteAttributeDefinition(Long id) {
        AttributeDefinition def = definitionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Definition not found: " + id));
        def.setIsActive(false);
        definitionRepository.save(def);
    }

    /**
     * Get total user count (for dashboard)
     */
    @Transactional(readOnly = true)
    public long getTotalUserCount() {
        return assignmentRepository.findByTargetTypeAndIsActiveTrue(AttributeAssignment.TargetType.USER)
                .stream()
                .map(AttributeAssignment::getTargetId)
                .distinct()
                .count();
    }

    /**
     * Get total attribute count (for dashboard)
     */
    @Transactional(readOnly = true)
    public long getTotalAttributeCount() {
        return assignmentRepository.countByIsActiveTrue();
    }
}
