package io.sentrius.sso.controllers.api;

import io.sentrius.sso.core.annotations.LimitAccess;
import io.sentrius.sso.core.config.SystemOptions;
import io.sentrius.sso.core.controllers.BaseController;
import io.sentrius.sso.core.model.security.enums.ApplicationAccessEnum;
import io.sentrius.sso.core.services.ErrorOutputService;
import io.sentrius.sso.core.services.PropertyOverrideService;
import io.sentrius.sso.core.services.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * REST API controller for managing application property overrides.
 * Provides endpoints to view, update, and delete property overrides stored in the database.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/properties")
public class PropertyOverrideController extends BaseController {

    private final PropertyOverrideService propertyOverrideService;

    public PropertyOverrideController(
        UserService userService,
        SystemOptions systemOptions,
        ErrorOutputService errorOutputService,
        PropertyOverrideService propertyOverrideService) {
        super(userService, systemOptions, errorOutputService);
        this.propertyOverrideService = propertyOverrideService;
    }

    /**
     * Get all properties with their current values and override status.
     * 
     * @return Map of property names to property information
     */
    @GetMapping
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public ResponseEntity<Map<String, PropertyOverrideService.PropertyInfo>> getAllProperties() {
        log.info("Fetching all properties");
        Map<String, PropertyOverrideService.PropertyInfo> properties = propertyOverrideService.getAllProperties();
        return ResponseEntity.ok(properties);
    }

    /**
     * Get a specific property value.
     * 
     * @param propertyName The property name
     * @return The property value
     */
    @GetMapping("/{propertyName}")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public ResponseEntity<Map<String, String>> getProperty(@PathVariable String propertyName) {
        log.info("Fetching property: {}", propertyName);
        String value = propertyOverrideService.getProperty(propertyName);
        
        if (value == null) {
            return ResponseEntity.notFound().build();
        }
        
        Map<String, String> response = new HashMap<>();
        response.put("propertyName", propertyName);
        response.put("value", value);
        return ResponseEntity.ok(response);
    }

    /**
     * Set or update a property override.
     * 
     * @param request The request containing property name and value
     * @return Success message
     */
    @PostMapping
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public ResponseEntity<Map<String, String>> setPropertyOverride(@RequestBody PropertyUpdateRequest request) {
        log.info("Setting property override: {} = {}", request.getPropertyName(), request.getValue());
        
        try {
            propertyOverrideService.setPropertyOverride(request.getPropertyName(), request.getValue());
            
            Map<String, String> response = new HashMap<>();
            response.put("message", "Property override saved successfully");
            response.put("propertyName", request.getPropertyName());
            return ResponseEntity.ok(response);
        } catch (SecurityException e) {
            log.warn("Security violation: {}", e.getMessage());
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to set property override", e);
            return ResponseEntity.status(500).body(Map.of("error", "Failed to save property override"));
        }
    }

    /**
     * Delete a property override (reverts to file value).
     * 
     * @param propertyName The property name
     * @return Success message
     */
    @DeleteMapping("/{propertyName}")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public ResponseEntity<Map<String, String>> deletePropertyOverride(@PathVariable String propertyName) {
        log.info("Deleting property override: {}", propertyName);
        
        try {
            propertyOverrideService.removePropertyOverride(propertyName);
            
            Map<String, String> response = new HashMap<>();
            response.put("message", "Property override removed successfully");
            response.put("propertyName", propertyName);
            return ResponseEntity.ok(response);
        } catch (SecurityException e) {
            log.warn("Security violation: {}", e.getMessage());
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to delete property override", e);
            return ResponseEntity.status(500).body(Map.of("error", "Failed to remove property override"));
        }
    }

    /**
     * Request body for property update operations.
     */
    @lombok.Getter
    @lombok.Setter
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class PropertyUpdateRequest {
        private String propertyName;
        private String value;
    }
}
