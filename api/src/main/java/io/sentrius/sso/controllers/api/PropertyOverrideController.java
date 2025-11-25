package io.sentrius.sso.controllers.api;

import io.sentrius.sso.core.annotations.LimitAccess;
import io.sentrius.sso.core.config.SystemOptions;
import io.sentrius.sso.core.controllers.BaseController;
import io.sentrius.sso.core.model.ConfigurationOption;
import io.sentrius.sso.core.model.security.enums.ApplicationAccessEnum;
import io.sentrius.sso.core.services.ErrorOutputService;
import io.sentrius.sso.core.services.PropertyOverrideService;
import io.sentrius.sso.core.services.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST API controller for managing application property overrides.
 * Provides endpoints to view, update, and delete property overrides stored in the database.
 * Supports pod-specific and global property overrides.
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
     * Optionally filter by pod name.
     * 
     * @param podName The pod name to filter by, or null for global properties
     * @return Map of property names to property information
     */
    @GetMapping
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public ResponseEntity<Map<String, PropertyOverrideService.PropertyInfo>> getAllProperties(
            @RequestParam(required = false) String podName) {
        log.info("Fetching all properties for pod: {}", podName != null ? podName : "global");
        Map<String, PropertyOverrideService.PropertyInfo> properties = propertyOverrideService.getAllProperties(podName);
        return ResponseEntity.ok(properties);
    }

    /**
     * Get all configuration options for a specific pod.
     * 
     * @param podName The pod name
     * @return List of configuration options for the pod
     */
    @GetMapping("/pod/{podName}")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public ResponseEntity<List<ConfigurationOption>> getPropertiesForPod(@PathVariable String podName) {
        log.info("Fetching all configurations for pod: {}", podName);
        List<ConfigurationOption> configs = propertyOverrideService.getAllConfigurationsForPod(podName);
        return ResponseEntity.ok(configs);
    }

    /**
     * Get a specific property value.
     * Optionally specify a pod name to get pod-specific override.
     * 
     * @param propertyName The property name
     * @param podName The pod name, or null for global lookup
     * @return The property value
     */
    @GetMapping("/{propertyName}")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public ResponseEntity<Map<String, String>> getProperty(
            @PathVariable String propertyName,
            @RequestParam(required = false) String podName) {
        log.info("Fetching property: {} for pod: {}", propertyName, podName != null ? podName : "global");
        String value = propertyOverrideService.getProperty(podName, propertyName);
        
        if (value == null) {
            return ResponseEntity.notFound().build();
        }
        
        Map<String, String> response = new HashMap<>();
        response.put("propertyName", propertyName);
        response.put("value", value);
        if (podName != null) {
            response.put("podName", podName);
        }
        return ResponseEntity.ok(response);
    }

    /**
     * Set or update a property override.
     * Optionally specify a pod name for pod-specific override.
     * 
     * @param request The request containing property name, value, and optional pod name
     * @return Success message
     */
    @PostMapping
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public ResponseEntity<Map<String, String>> setPropertyOverride(@RequestBody PropertyUpdateRequest request) {
        log.info("Setting property override: {} = {} for pod: {}", 
            request.getPropertyName(), request.getValue(), 
            request.getPodName() != null ? request.getPodName() : "global");
        
        try {
            propertyOverrideService.setPropertyOverride(
                request.getPodName(), 
                request.getPropertyName(), 
                request.getValue());
            
            Map<String, String> response = new HashMap<>();
            response.put("message", "Property override saved successfully");
            response.put("propertyName", request.getPropertyName());
            if (request.getPodName() != null) {
                response.put("podName", request.getPodName());
            }
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
     * Optionally specify a pod name to delete pod-specific override.
     * 
     * @param propertyName The property name
     * @param podName The pod name, or null for global override
     * @return Success message
     */
    @DeleteMapping("/{propertyName}")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public ResponseEntity<Map<String, String>> deletePropertyOverride(
            @PathVariable String propertyName,
            @RequestParam(required = false) String podName) {
        log.info("Deleting property override: {} for pod: {}", propertyName, podName != null ? podName : "global");
        
        try {
            propertyOverrideService.removePropertyOverride(podName, propertyName);
            
            Map<String, String> response = new HashMap<>();
            response.put("message", "Property override removed successfully");
            response.put("propertyName", propertyName);
            if (podName != null) {
                response.put("podName", podName);
            }
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
        private String podName;
        
        public PropertyUpdateRequest(String propertyName, String value) {
            this.propertyName = propertyName;
            this.value = value;
        }
    }
}
