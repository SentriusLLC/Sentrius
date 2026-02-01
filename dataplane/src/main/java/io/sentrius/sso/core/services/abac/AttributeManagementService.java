package io.sentrius.sso.core.services.abac;

import io.sentrius.sso.core.model.abac.AttributeAssignment;
import io.sentrius.sso.core.model.abac.AttributeDefinition;
import io.sentrius.sso.core.model.users.UserAttribute;
import io.sentrius.sso.core.repository.abac.AttributeAssignmentRepository;
import io.sentrius.sso.core.repository.abac.AttributeDefinitionRepository;
import io.sentrius.sso.core.repository.UserAttributeRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Lazy;
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
    private final UserAttributeRepository userAttributeRepository;
    private final io.sentrius.sso.core.services.security.KeycloakService keycloakService;

    // Use @Lazy and @Autowired to break circular dependency with UserService
    @Lazy
    @Autowired(required = false)
    private io.sentrius.sso.core.services.UserService userService;

    public AttributeManagementService(
            AttributeDefinitionRepository definitionRepository,
            AttributeAssignmentRepository assignmentRepository,
            UserAttributeRepository userAttributeRepository,
            io.sentrius.sso.core.services.security.KeycloakService keycloakService) {
        this.definitionRepository = definitionRepository;
        this.assignmentRepository = assignmentRepository;
        this.userAttributeRepository = userAttributeRepository;
        this.keycloakService = keycloakService;
    }

    /**
     * Resolve a user identifier (username or userId) to Keycloak UUID.
     * This ensures consistency between ABAC attribute storage and document access control.
     *
     * @param identifier Can be username (e.g., "marc@sentrius.io", "service-account-java-agents") or Keycloak UUID
     * @return Keycloak UUID (userId field) if user found, null otherwise
     */
    public String resolveUserIdentifierToKeycloakId(String identifier) {
        if (identifier == null || identifier.trim().isEmpty()) {
            return null;
        }

        if (userService == null) {
            log.warn("UserService not available, cannot resolve identifier {} to Keycloak UUID", identifier);
            return identifier; // Return as-is if service not available
        }

        // First, check if this is already a UUID by trying to look it up directly
        io.sentrius.sso.core.model.users.User userById = userService.getUserByUserid(identifier);
        if (userById != null) {
            log.debug("Identifier {} is already a Keycloak UUID", identifier);
            return userById.getUserId();
        }

        // Otherwise, treat it as a username and look up by username
        Optional<io.sentrius.sso.core.model.users.User> userByUsername = userService.findByUsername(identifier);
        if (userByUsername.isPresent()) {
            String keycloakId = userByUsername.get().getUserId();
            log.debug("Resolved username {} to Keycloak UUID: {}", identifier, keycloakId);
            return keycloakId;
        }

        log.warn("Could not resolve identifier {} to a Keycloak UUID - user not found in database", identifier);
        return null;
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
        
        AttributeAssignment assignment;
        if (existing.isPresent()) {
            // Update existing assignment
            assignment = existing.get();
            assignment.setAttributeValue(value);
            assignment.setSource(source);
            assignment.setSyncedFromKeycloak(syncedFromKeycloak);
            log.debug("Updated attribute assignment: {} for {}:{}", 
                    definition.getAttributeName(), targetType, targetId);
            assignment = assignmentRepository.save(assignment);
        } else {
            // Create new assignment
            assignment = AttributeAssignment.builder()
                    .attributeDefinition(definition)
                    .targetType(targetType)
                    .targetId(targetId)
                    .attributeValue(value)
                    .source(source)
                    .syncedFromKeycloak(syncedFromKeycloak)
                    .isActive(true)
                    .priority(0)
                    .build();
            log.info("Created new attribute assignment: {} = {} for {}:{} from {}",
                    definition.getAttributeName(), value, targetType, targetId, source);
            assignment = assignmentRepository.save(assignment);
        }
        
        // IMPORTANT: Also persist to UserAttribute table for USER and NON_PERSON_ENTITY target types
        // This ensures compatibility with DocumentAccessControlService and MemoryAccessControlService
        // NPE agents need attributes in user_attributes table to access documents with markings
        if (targetType == AttributeAssignment.TargetType.USER ||
            targetType == AttributeAssignment.TargetType.NON_PERSON_ENTITY) {
            syncToUserAttributeTable(targetId, definition.getAttributeName(), value,
                                    source.name(), syncedFromKeycloak);
        }
        
        return assignment;
    }
    
    /**
     * Sync an attribute to the UserAttribute table for backward compatibility
     * This ensures attributes work with DocumentAccessControlService and other services
     * that query the user_attributes table
     */
    private void syncToUserAttributeTable(String userId, String attributeName, String attributeValue,
                                          String source, boolean syncedFromKeycloak) {
        try {
            // Check if UserAttribute already exists (active OR inactive to avoid constraint violation)
            Optional<UserAttribute> existingUserAttr = userAttributeRepository
                    .findByUserIdAndAttributeNameAndIsActiveTrue(userId, attributeName);
            
            if (existingUserAttr.isPresent()) {
                // Update existing active UserAttribute
                UserAttribute userAttr = existingUserAttr.get();
                userAttr.setAttributeValue(attributeValue);
                userAttr.setSource(source);
                userAttr.setSyncedFromKeycloak(syncedFromKeycloak);
                userAttr.setIsActive(true); // Ensure it's active
                userAttributeRepository.save(userAttr);
                log.debug("Updated UserAttribute for user: {}, attribute: {}", userId, attributeName);
            } else {
                // Check for inactive attribute that might violate unique constraint
                Optional<UserAttribute> inactiveAttr = userAttributeRepository
                        .findByUserIdAndAttributeName(userId, attributeName);

                if (inactiveAttr.isPresent()) {
                    // Reactivate the inactive attribute
                    UserAttribute userAttr = inactiveAttr.get();
                    userAttr.setAttributeValue(attributeValue);
                    userAttr.setSource(source);
                    userAttr.setSyncedFromKeycloak(syncedFromKeycloak);
                    userAttr.setIsActive(true); // Reactivate
                    userAttributeRepository.save(userAttr);
                    log.info("Reactivated UserAttribute for user: {}, attribute: {} = {}",
                            userId, attributeName, attributeValue);
                } else {
                    // Create new UserAttribute - use STRING as default type for simplicity
                    // UserAttribute has basic type checking (STRING, INTEGER, BOOLEAN, etc.)
                    // while AttributeDefinition has more complex type system
                    UserAttribute userAttr = UserAttribute.builder()
                            .userId(userId)
                            .attributeName(attributeName)
                            .attributeValue(attributeValue)
                            .attributeType("STRING")
                            .source(source)
                            .isActive(true)
                            .syncedFromKeycloak(syncedFromKeycloak)
                            .build();
                    userAttributeRepository.save(userAttr);
                    log.info("Created UserAttribute for user: {}, attribute: {} = {}",
                            userId, attributeName, attributeValue);
                }
            }
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            log.error("Database constraint violation syncing attribute to UserAttribute table for user: {}, attribute: {}. " +
                     "This may indicate a race condition or the attribute exists but wasn't found.",
                     userId, attributeName, e);
            // Don't fail the main operation if UserAttribute sync fails
        } catch (org.springframework.dao.DataAccessException e) {
            log.error("Database error syncing attribute to UserAttribute table for user: {}, attribute: {}", 
                     userId, attributeName, e);
            // Don't fail the main operation if UserAttribute sync fails
        } catch (Exception e) {
            log.error("Unexpected error syncing attribute to UserAttribute table for user: {}, attribute: {}", 
                     userId, attributeName, e);
            // Don't fail the main operation if UserAttribute sync fails
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

        String attributeString = keycloakAttributes.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining(", "));

        String attributeName = "attributes";
        String attributeValue = attributeString;
            
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
        for(Map.Entry<String, String> entry : keycloakAttributes.entrySet()) {
            String attrName = entry.getKey();
            String attrValue = entry.getValue();

            AttributeDefinition attrDef = getOrCreateAttributeDefinition(
                    attrName,
                    AttributeDefinition.AttributeScope.SUBJECT,
                    AttributeDefinition.AttributeType.STRING);

            // Mark definition as synced with Keycloak
            if (!attrDef.getSyncedWithKeycloak()) {
                attrDef.setSyncedWithKeycloak(true);
                definitionRepository.save(attrDef);
            }

            log.info("Assigning attribute from Keycloak: {} = {} for user: {}", attrName, attrValue, userId);
            assignAttribute(attrDef, AttributeAssignment.TargetType.USER, userId,
                    attrValue, AttributeAssignment.AssignmentSource.KEYCLOAK, true);
        }
        //assignAttribute(definition, AttributeAssignment.TargetType.USER, userId,
          //      attributeValue, AttributeAssignment.AssignmentSource.KEYCLOAK, true);

    }

    /**
     * Sync user attributes TO Keycloak from Sentrius
     * This method pushes user attributes from Sentrius ABAC system to Keycloak
     * 
     * @param userId Keycloak user ID
     * @param sentriusUserId Sentrius user ID  
     */
    @Transactional
    public void syncUserAttributesToKeycloak(String userId, String sentriusUserId) {
        log.info("Syncing attributes to Keycloak for user: {}", userId);
        
        // Get all active attributes for this user
        List<AttributeAssignment> assignments = assignmentRepository
                .findByTargetTypeAndTargetIdAndIsActiveTrue(
                        AttributeAssignment.TargetType.USER, 
                        sentriusUserId);
        
        if (assignments.isEmpty()) {
            log.debug("No attributes to sync for user: {}", userId);
            return;
        }
        
        // Convert to Keycloak format (attribute -> List<value>)
        Map<String, List<String>> keycloakAttributes = new HashMap<>();
        for (AttributeAssignment assignment : assignments) {
            AttributeDefinition definition = assignment.getAttributeDefinition();
            
            // Only sync attributes that are marked for Keycloak sync
            if (definition.getSyncedWithKeycloak()) {
                String attrName = definition.getAttributeName();
                String attrValue = assignment.getAttributeValue();
                
                keycloakAttributes.computeIfAbsent(attrName, k -> new java.util.ArrayList<>())
                        .add(attrValue);
            }
        }
        
        if (!keycloakAttributes.isEmpty()) {
            // Update attributes in Keycloak
            keycloakService.updateUserAttributes(userId, keycloakAttributes);
            log.info("Synced {} attributes to Keycloak for user: {}", keycloakAttributes.size(), userId);
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
            
            // Also deactivate corresponding UserAttribute if this is a USER or NON_PERSON_ENTITY assignment
            if (a.getTargetType() == AttributeAssignment.TargetType.USER ||
                a.getTargetType() == AttributeAssignment.TargetType.NON_PERSON_ENTITY) {
                deactivateUserAttribute(a.getTargetId(), a.getAttributeDefinition().getAttributeName());
            }
            
            return true;
        }
        return false;
    }
    
    /**
     * Deactivate a UserAttribute for backward compatibility
     */
    private void deactivateUserAttribute(String userId, String attributeName) {
        try {
            Optional<UserAttribute> userAttr = userAttributeRepository
                    .findByUserIdAndAttributeNameAndIsActiveTrue(userId, attributeName);
            if (userAttr.isPresent()) {
                UserAttribute attr = userAttr.get();
                attr.setIsActive(false);
                userAttributeRepository.save(attr);
                log.debug("Deactivated UserAttribute for user: {}, attribute: {}", userId, attributeName);
            }
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            log.error("Database constraint violation deactivating UserAttribute for user: {}, attribute: {}", 
                     userId, attributeName, e);
            // Don't fail the main operation
        } catch (org.springframework.dao.DataAccessException e) {
            log.error("Database error deactivating UserAttribute for user: {}, attribute: {}", 
                     userId, attributeName, e);
            // Don't fail the main operation
        } catch (Exception e) {
            log.error("Unexpected error deactivating UserAttribute for user: {}, attribute: {}", 
                     userId, attributeName, e);
            // Don't fail the main operation
        }
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
