package io.sentrius.sso.controllers.api;

import io.sentrius.sso.core.annotations.LimitAccess;
import io.sentrius.sso.core.config.SystemOptions;
import io.sentrius.sso.core.controllers.BaseController;
import io.sentrius.sso.core.dto.CustomAttributeMappingDTO;
import io.sentrius.sso.core.model.security.enums.ApplicationAccessEnum;
import io.sentrius.sso.core.model.customattributes.CustomAttributeMapping;
import io.sentrius.sso.core.services.ErrorOutputService;
import io.sentrius.sso.core.services.UserService;
import io.sentrius.sso.core.services.customattributes.CustomAttributeMappingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/v1/custom-attribute-mappings")
public class CustomAttributeMappingController extends BaseController {

    private final CustomAttributeMappingService customAttributeMappingService;

    public CustomAttributeMappingController(
            CustomAttributeMappingService customAttributeMappingService,
            UserService userService,
            SystemOptions systemOptions,
            ErrorOutputService errorOutputService) {
        super(userService, systemOptions, errorOutputService);
        this.customAttributeMappingService = customAttributeMappingService;
    }

    /**
     * Get all custom attribute mappings
     */
    @GetMapping
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public ResponseEntity<List<CustomAttributeMappingDTO>> getAllMappings(
            HttpServletRequest request, 
            HttpServletResponse response) {
        
        log.debug("Getting all custom attribute mappings");
        
        try {
            List<CustomAttributeMapping> mappings = customAttributeMappingService.getAllMappings();
            List<CustomAttributeMappingDTO> dtos = mappings.stream()
                    .map(this::convertToDTO)
                    .collect(Collectors.toList());
            
            return ResponseEntity.ok(dtos);
            
        } catch (Exception e) {
            log.error("Error getting custom attribute mappings", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get mappings for a specific endpoint
     */
    @GetMapping("/endpoint")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public ResponseEntity<List<CustomAttributeMappingDTO>> getMappingsByEndpoint(
            @RequestParam String endpoint,
            HttpServletRequest request, 
            HttpServletResponse response) {
        
        log.debug("Getting custom attribute mappings for endpoint: {}", endpoint);
        
        try {
            List<CustomAttributeMapping> mappings = customAttributeMappingService.getMappingsByEndpoint(endpoint);
            List<CustomAttributeMappingDTO> dtos = mappings.stream()
                    .map(this::convertToDTO)
                    .collect(Collectors.toList());
            
            return ResponseEntity.ok(dtos);
            
        } catch (Exception e) {
            log.error("Error getting custom attribute mappings for endpoint: {}", endpoint, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Create a new custom attribute mapping
     */
    @PostMapping
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public ResponseEntity<CustomAttributeMappingDTO> createMapping(
            @RequestBody CustomAttributeMappingDTO dto,
            HttpServletRequest request, 
            HttpServletResponse response) {
        
        log.info("Creating custom attribute mapping for endpoint: {}", dto.getEndpoint());
        
        try {
            CustomAttributeMapping mapping = customAttributeMappingService.createMapping(
                    dto.getEndpoint(),
                    dto.getAttributeName(),
                    dto.getRequiredValue(),
                    dto.getDescription()
            );
            
            CustomAttributeMappingDTO responseDTO = convertToDTO(mapping);
            return ResponseEntity.ok(responseDTO);
            
        } catch (IllegalArgumentException e) {
            log.warn("Invalid mapping data: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Error creating custom attribute mapping", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Update an existing custom attribute mapping
     */
    @PutMapping("/{id}")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public ResponseEntity<CustomAttributeMappingDTO> updateMapping(
            @PathVariable Long id,
            @RequestBody CustomAttributeMappingDTO dto,
            HttpServletRequest request, 
            HttpServletResponse response) {
        
        log.info("Updating custom attribute mapping: {}", id);
        
        try {
            CustomAttributeMapping mapping = customAttributeMappingService.updateMapping(
                    id,
                    dto.getEndpoint(),
                    dto.getAttributeName(),
                    dto.getRequiredValue(),
                    dto.getDescription(),
                    dto.getIsActive()
            );
            
            if (mapping == null) {
                return ResponseEntity.notFound().build();
            }
            
            CustomAttributeMappingDTO responseDTO = convertToDTO(mapping);
            return ResponseEntity.ok(responseDTO);
            
        } catch (IllegalArgumentException e) {
            log.warn("Invalid mapping data: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Error updating custom attribute mapping: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Delete a custom attribute mapping
     */
    @DeleteMapping("/{id}")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public ResponseEntity<Void> deleteMapping(
            @PathVariable Long id,
            HttpServletRequest request, 
            HttpServletResponse response) {
        
        log.info("Deleting custom attribute mapping: {}", id);
        
        try {
            boolean deleted = customAttributeMappingService.deleteMapping(id);
            
            if (deleted) {
                return ResponseEntity.ok().build();
            } else {
                return ResponseEntity.notFound().build();
            }
            
        } catch (Exception e) {
            log.error("Error deleting custom attribute mapping: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get all unique endpoints that have custom attribute mappings
     */
    @GetMapping("/endpoints")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public ResponseEntity<List<String>> getAllEndpoints(
            HttpServletRequest request, 
            HttpServletResponse response) {
        
        log.debug("Getting all endpoints with custom attribute mappings");
        
        try {
            List<String> endpoints = customAttributeMappingService.getAllEndpoints();
            return ResponseEntity.ok(endpoints);
            
        } catch (Exception e) {
            log.error("Error getting endpoints", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get all unique attribute names used in mappings
     */
    @GetMapping("/attribute-names")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public ResponseEntity<List<String>> getAllAttributeNames(
            HttpServletRequest request, 
            HttpServletResponse response) {
        
        log.debug("Getting all attribute names");
        
        try {
            List<String> attributeNames = customAttributeMappingService.getAllAttributeNames();
            return ResponseEntity.ok(attributeNames);
            
        } catch (Exception e) {
            log.error("Error getting attribute names", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Convert entity to DTO
     */
    private CustomAttributeMappingDTO convertToDTO(CustomAttributeMapping mapping) {
        return CustomAttributeMappingDTO.builder()
                .id(mapping.getId())
                .endpoint(mapping.getEndpoint())
                .attributeName(mapping.getAttributeName())
                .requiredValue(mapping.getRequiredValue())
                .description(mapping.getDescription())
                .isActive(mapping.getIsActive())
                .createdAt(mapping.getCreatedAt())
                .updatedAt(mapping.getUpdatedAt())
                .build();
    }
}
