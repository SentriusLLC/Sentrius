package io.sentrius.sso.controllers.api.abac;

import io.sentrius.sso.core.annotations.LimitAccess;
import io.sentrius.sso.core.dto.abac.AttributeAssignmentDTO;
import io.sentrius.sso.core.dto.abac.AttributeDefinitionDTO;
import io.sentrius.sso.core.dto.abac.SyncStatusDTO;
import io.sentrius.sso.core.model.abac.AttributeAssignment;
import io.sentrius.sso.core.model.abac.AttributeDefinition;
import io.sentrius.sso.core.model.security.enums.ApplicationAccessEnum;
import io.sentrius.sso.core.services.abac.AttributeManagementService;
import io.sentrius.sso.core.services.abac.KeycloakAttributeSyncScheduler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/abac")
@Slf4j
public class AttributeManagementController {

    private final AttributeManagementService attributeManagementService;
    
    @Autowired(required = false)
    private KeycloakAttributeSyncScheduler syncScheduler;
    
    public AttributeManagementController(AttributeManagementService attributeManagementService) {
        this.attributeManagementService = attributeManagementService;
    }

    // ===== USER ATTRIBUTES ENDPOINTS =====

    @GetMapping("/user-attributes")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public ResponseEntity<List<AttributeAssignmentDTO>> getAllUserAttributes() {
        List<AttributeAssignment> assignments = attributeManagementService.getAllUserAttributes();
        List<AttributeAssignmentDTO> dtos = assignments.stream()
                .map(this::toAssignmentDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
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
        // Create or get attribute definition
        AttributeDefinition definition = attributeManagementService.getOrCreateAttributeDefinition(
                dto.getAttributeName(),
                AttributeDefinition.AttributeScope.valueOf(dto.getTargetType().equals("USER") ? "SUBJECT" : "RESOURCE"),
                "STRING"
        );

        // Assign attribute
        AttributeAssignment assignment = attributeManagementService.assignAttribute(
                dto.getTargetType(),
                dto.getTargetId(),
                definition,
                dto.getAttributeValue(),
                dto.getValidFrom(),
                dto.getValidUntil()
        );

        // Sync to Keycloak if requested
        if (dto.isSyncToKeycloak() && dto.getTargetType().equals("USER")) {
            Map<String, String> attrs = new HashMap<>();
            attrs.put(dto.getAttributeName(), dto.getAttributeValue());
            attributeManagementService.syncUserAttributesFromKeycloak(dto.getTargetId(), attrs);
        }

        return ResponseEntity.ok(toAssignmentDTO(assignment));
    }

    @PutMapping("/user-attributes/{id}")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public ResponseEntity<AttributeAssignmentDTO> updateUserAttribute(
            @PathVariable Long id,
            @RequestBody AttributeAssignmentDTO dto) {
        // For simplicity, delete old and create new
        attributeManagementService.removeAttributeAssignment(id);
        return createUserAttribute(dto);
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
                dto.getAttributeType(),
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
        dto.setAttributeType(definition.getAttributeType().toString());
        dto.setDescription(definition.getDescription());
        dto.setKeycloakAttributeName(definition.getKeycloakAttributeName());
        dto.setIsRequired(definition.getIsRequired());
        return dto;
    }
}
