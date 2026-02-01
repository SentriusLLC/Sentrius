package io.sentrius.sso.controllers.api.abac;

import io.sentrius.sso.core.annotations.LimitAccess;
import io.sentrius.sso.core.dto.abac.AttributeAssignmentDTO;
import io.sentrius.sso.core.dto.abac.AttributeDefinitionDTO;
import io.sentrius.sso.core.dto.abac.SyncStatusDTO;
import io.sentrius.sso.core.model.abac.AttributeAssignment;
import io.sentrius.sso.core.model.abac.AttributeDefinition;
import io.sentrius.sso.core.model.security.enums.ApplicationAccessEnum;
import io.sentrius.sso.core.model.users.UserAttribute;
import io.sentrius.sso.core.services.abac.AttributeManagementService;
import io.sentrius.sso.core.services.abac.KeycloakAttributeSyncScheduler;
import io.sentrius.sso.core.services.users.UserAttributeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/abac")
@Slf4j
public class AttributeManagementController {

    private final AttributeManagementService attributeManagementService;
    private final UserAttributeService userAttributeService;

    @Autowired(required = false)
    private KeycloakAttributeSyncScheduler syncScheduler;
    
    public AttributeManagementController(AttributeManagementService attributeManagementService,
                                         UserAttributeService userAttributeService) {
        this.attributeManagementService = attributeManagementService;
        this.userAttributeService = userAttributeService;
    }

    // ===== USER ATTRIBUTES ENDPOINTS =====

    @GetMapping("/user-attributes")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public ResponseEntity<List<AttributeAssignmentDTO>> getAllUserAttributes() {
        List<AttributeAssignmentDTO> allDtos = new ArrayList<>();

        // Get ABAC attribute assignments
        List<AttributeAssignment> assignments = attributeManagementService.getAllUserAttributes();
        List<AttributeAssignmentDTO> abacDtos = assignments.stream()
                .map(this::toAssignmentDTO)
                .collect(Collectors.toList());
        allDtos.addAll(abacDtos);

        // Also include legacy user attributes from user_attributes table
        List<UserAttribute> legacyAttributes = userAttributeService.getAllActiveAttributes();
        for (UserAttribute attr : legacyAttributes) {
            // Check if this attribute is already in ABAC assignments (avoid duplicates)
            boolean alreadyExists = abacDtos.stream()
                    .anyMatch(dto -> dto.getTargetId() != null
                            && dto.getTargetId().equals(attr.getUserId())
                            && dto.getAttributeName() != null
                            && dto.getAttributeName().equals(attr.getAttributeName()));

            if (!alreadyExists) {
                AttributeAssignmentDTO dto = new AttributeAssignmentDTO();
                dto.setId(attr.getId());
                dto.setTargetId(attr.getUserId());
                dto.setTargetType("USER");
                dto.setAttributeName(attr.getAttributeName());
                dto.setAttributeValue(attr.getAttributeValue());
                dto.setSyncedFromKeycloak(attr.getSyncedFromKeycloak() != null && attr.getSyncedFromKeycloak());
                dto.setActive(attr.getIsActive() != null && attr.getIsActive());
                // Legacy attributes don't have validity periods, use createdAt for display
                if (attr.getCreatedAt() != null) {
                    dto.setCreatedAt(LocalDateTime.ofInstant(attr.getCreatedAt(), ZoneOffset.UTC));
                }
                if (attr.getUpdatedAt() != null) {
                    dto.setUpdatedAt(LocalDateTime.ofInstant(attr.getUpdatedAt(), ZoneOffset.UTC));
                }
                // Mark as legacy source for UI distinction
                dto.setSource("LEGACY");
                allDtos.add(dto);
            }
        }

        return ResponseEntity.ok(allDtos);
    }

    @GetMapping("/user-attributes/user/{userId}")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public ResponseEntity<List<AttributeAssignmentDTO>> getUserAttributes(@PathVariable String userId) {
        Map<String, String> attributes = attributeManagementService.getUserAttributesMap(userId);
        // Convert to assignments for display
        List<AttributeAssignmentDTO> dtos = attributes.entrySet().stream()
                .map(entry -> {
                    AttributeAssignmentDTO dto = new AttributeAssignmentDTO();
                    dto.setTargetId(userId);
                    dto.setTargetType("USER");
                    dto.setAttributeName(entry.getKey());
                    dto.setAttributeValue(entry.getValue());
                    return dto;
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @PostMapping("/user-attributes")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public ResponseEntity<AttributeAssignmentDTO> createUserAttribute(@RequestBody AttributeAssignmentDTO dto) {
        log.debug("Creating user attribute: targetType={}, targetId={}, attributeName={}",
                dto.getTargetType(), dto.getTargetId(), dto.getAttributeName());

        // Create or get attribute definition
        AttributeDefinition definition = attributeManagementService.getOrCreateAttributeDefinition(
                dto.getAttributeName(),
                AttributeDefinition.AttributeScope.valueOf(dto.getTargetType().equals("USER") ? "SUBJECT" : "RESOURCE"),
                "STRING"
        );

        // IMPORTANT: Resolve targetId to Keycloak userId for consistency
        // The UI may send username (e.g., "marc@sentrius.io" or "service-account-java-agents")
        // but we need to store the Keycloak UUID for consistency with document access control
        String resolvedTargetId = dto.getTargetId();
        if ("USER".equals(dto.getTargetType()) || "NON_PERSON_ENTITY".equals(dto.getTargetType())) {
            resolvedTargetId = attributeManagementService.resolveUserIdentifierToKeycloakId(dto.getTargetId());
            if (resolvedTargetId == null) {
                log.warn("Could not resolve targetId {} to Keycloak UUID, using as-is", dto.getTargetId());
                resolvedTargetId = dto.getTargetId();
            } else {
                log.debug("Resolved targetId {} to Keycloak UUID: {}", dto.getTargetId(), resolvedTargetId);
            }
        }

        // Assign attribute with resolved ID
        AttributeAssignment assignment = attributeManagementService.assignAttribute(
                dto.getTargetType(),
                resolvedTargetId,
                definition,
                dto.getAttributeValue(),
                dto.getValidFrom(),
                dto.getValidUntil()
        );

        // Sync to Keycloak if requested
        if (dto.isSyncToKeycloak() && dto.getTargetType().equals("USER")) {
            Map<String, String> attrs = new HashMap<>();
            attrs.put(dto.getAttributeName(), dto.getAttributeValue());
            //attributeManagementService.syncUserAttributesToKeycloak(resolvedTargetId, attrs);
        }

        return ResponseEntity.ok(toAssignmentDTO(assignment));
    }

    @PutMapping("/user-attributes/{id}")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public ResponseEntity<AttributeAssignmentDTO> updateUserAttribute(
            @PathVariable Long id,
            @RequestBody AttributeAssignmentDTO dto) {
        log.info("PUT /api/v1/abac/user-attributes/{}: Updating attribute assignment", id);
        log.debug("Update request: id={}, targetType={}, targetId={}, attributeName={}, value={}",
                id, dto.getTargetType(), dto.getTargetId(), dto.getAttributeName(), dto.getAttributeValue());

        try {
            // Remove old assignment
            boolean removed = attributeManagementService.removeAttributeAssignment(id);
            if (!removed) {
                log.warn("Attribute assignment {} not found for update", id);
                return ResponseEntity.notFound().build();
            }

            // Create new assignment with updated values
            return createUserAttribute(dto);
        } catch (Exception e) {
            log.error("Error updating attribute assignment {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @DeleteMapping("/user-attributes/{id}")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public ResponseEntity<Void> deleteUserAttribute(@PathVariable Long id) {
        attributeManagementService.removeAttributeAssignment(id);
        return ResponseEntity.noContent().build();
    }

    // ===== ATTRIBUTE DEFINITIONS ENDPOINTS =====

    @GetMapping("/attribute-definitions")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public ResponseEntity<List<AttributeDefinitionDTO>> getAllDefinitions() {
        List<AttributeDefinition> definitions = attributeManagementService.getAllAttributeDefinitions();
        List<AttributeDefinitionDTO> dtos = definitions.stream()
                .map(this::toDefinitionDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/attribute-definitions/scope/{scope}")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public ResponseEntity<List<AttributeDefinitionDTO>> getDefinitionsByScope(@PathVariable String scope) {
        AttributeDefinition.AttributeScope attributeScope = AttributeDefinition.AttributeScope.valueOf(scope.toUpperCase());
        List<AttributeDefinition> definitions = attributeManagementService.getDefinitionsByScope(attributeScope);
        List<AttributeDefinitionDTO> dtos = definitions.stream()
                .map(this::toDefinitionDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @PostMapping("/attribute-definitions")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public ResponseEntity<AttributeDefinitionDTO> createDefinition(@RequestBody AttributeDefinitionDTO dto) {
        AttributeDefinition definition = attributeManagementService.createAttributeDefinition(
                dto.getAttributeName(),
                AttributeDefinition.AttributeScope.valueOf(dto.getAttributeScope()),
                dto.getDataType(),
                dto.getDescription(),
                dto.getKeycloakAttributeName()
        );
        return ResponseEntity.ok(toDefinitionDTO(definition));
    }

    @PutMapping("/attribute-definitions/{id}")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public ResponseEntity<AttributeDefinitionDTO> updateDefinition(
            @PathVariable Long id,
            @RequestBody AttributeDefinitionDTO dto) {
        AttributeDefinition definition = attributeManagementService.updateAttributeDefinition(
                id,
                dto.getDescription(),
                dto.getKeycloakAttributeName()
        );
        return ResponseEntity.ok(toDefinitionDTO(definition));
    }

    @DeleteMapping("/attribute-definitions/{id}")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public ResponseEntity<Void> deleteDefinition(@PathVariable Long id) {
        attributeManagementService.deleteAttributeDefinition(id);
        return ResponseEntity.noContent().build();
    }

    // ===== SYNC ENDPOINTS =====

    @PostMapping("/sync/all")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public ResponseEntity<Map<String, Object>> syncAllFromKeycloak() {
        log.info("Manual sync all from Keycloak triggered");
        
        if (syncScheduler == null) {
            Map<String, Object> response = new HashMap<>();
            response.put("status", "disabled");
            response.put("timestamp", LocalDateTime.now());
            response.put("message", "Keycloak sync is not enabled. Set sentrius.abac.keycloak-sync.enabled=true in configuration.");
            return ResponseEntity.ok(response);
        }
        
        syncScheduler.syncAllUsersFromKeycloak();
        
        Map<String, Object> response = new HashMap<>();
        response.put("status", "synced");
        response.put("timestamp", LocalDateTime.now());
        response.put("message", "Sync completed successfully");
        
        return ResponseEntity.ok(response);
    }

    @PostMapping("/sync/user/{userId}")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public ResponseEntity<Map<String, Object>> syncUserFromKeycloak(@PathVariable String userId) {
        log.info("Manual sync user {} from Keycloak triggered", userId);
        
        if (syncScheduler == null) {
            Map<String, Object> response = new HashMap<>();
            response.put("status", "disabled");
            response.put("userId", userId);
            response.put("timestamp", LocalDateTime.now());
            response.put("message", "Keycloak sync is not enabled. Set sentrius.abac.keycloak-sync.enabled=true in configuration.");
            return ResponseEntity.ok(response);
        }
        
        syncScheduler.syncUserFromKeycloak(userId);
        
        Map<String, Object> response = new HashMap<>();
        response.put("status", "synced");
        response.put("userId", userId);
        response.put("timestamp", LocalDateTime.now());
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/sync-status")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public ResponseEntity<SyncStatusDTO> getSyncStatus() {
        SyncStatusDTO status = new SyncStatusDTO();
        
        if (syncScheduler == null) {
            status.setLastSyncTime("Sync disabled");
            status.setSyncEnabled(false);
        } else {
            status.setLastSyncTime(syncScheduler.getLastSyncTime());
            status.setSyncEnabled(syncScheduler.isSyncEnabled());
        }
        
        status.setTotalUsers(attributeManagementService.getTotalUserCount());
        status.setTotalAttributes(attributeManagementService.getTotalAttributeCount());
        return ResponseEntity.ok(status);
    }

    // ===== HELPER METHODS =====

    private AttributeAssignmentDTO toAssignmentDTO(AttributeAssignment assignment) {
        AttributeAssignmentDTO dto = new AttributeAssignmentDTO();
        dto.setId(assignment.getId());
        dto.setTargetType(assignment.getTargetType().name());
        dto.setTargetId(assignment.getTargetId());
        dto.setAttributeName(assignment.getAttributeDefinition().getAttributeName());
        dto.setAttributeValue(assignment.getAttributeValue());
        dto.setSource(assignment.getSource().name());
        if (assignment.getValidFrom() != null) {
            dto.setValidFrom(LocalDateTime.ofInstant(assignment.getValidFrom(), ZoneOffset.UTC));
        }
        if (assignment.getValidUntil() != null) {
            dto.setValidUntil(LocalDateTime.ofInstant(assignment.getValidUntil(), ZoneOffset.UTC));
        }

        dto.setSyncedFromKeycloak(assignment.getSyncedFromKeycloak());
        dto.setActive(assignment.getIsActive());
        return dto;
    }

    private AttributeDefinitionDTO toDefinitionDTO(AttributeDefinition definition) {
        AttributeDefinitionDTO dto = new AttributeDefinitionDTO();
        dto.setId(definition.getId());
        dto.setAttributeName(definition.getAttributeName());
        dto.setAttributeScope(definition.getAttributeScope().name());
        dto.setDataType(definition.getAttributeType().toString());
        dto.setDescription(definition.getDescription());
        dto.setKeycloakAttributeName(definition.getKeycloakAttributeName());
        dto.setIsRequired(definition.getIsRequired());
        return dto;
    }
}
