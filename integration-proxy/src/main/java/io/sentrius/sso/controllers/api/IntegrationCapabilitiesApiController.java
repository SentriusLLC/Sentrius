package io.sentrius.sso.controllers.api;

import java.util.List;
import java.util.stream.Collectors;
import io.sentrius.sso.core.annotations.LimitAccess;
import io.sentrius.sso.core.config.SystemOptions;
import io.sentrius.sso.core.controllers.BaseController;
import io.sentrius.sso.core.dto.capabilities.EndpointDescriptor;
import io.sentrius.sso.core.model.security.enums.ApplicationAccessEnum;
import io.sentrius.sso.core.services.ErrorOutputService;
import io.sentrius.sso.core.services.UserService;
import io.sentrius.sso.core.services.capabilities.EndpointScanningService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * API controller for exposing endpoint capabilities across the system.
 * This provides a unified view of all REST endpoints and Verb methods available.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/capabilities")
public class IntegrationCapabilitiesApiController extends BaseController {

    private final EndpointScanningService endpointScanningService;

    public IntegrationCapabilitiesApiController(
            UserService userService,
            SystemOptions systemOptions,
            ErrorOutputService errorOutputService,
            EndpointScanningService endpointScanningService) {
        super(userService, systemOptions, errorOutputService);
        this.endpointScanningService = endpointScanningService;
    }

    /**
     * Returns all available endpoints (REST and Verb) in the system.
     * This can be used by AI agents and other systems to understand what capabilities are available.
     */
    @GetMapping("/endpoints")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_LOG_IN})
    public ResponseEntity<List<EndpointDescriptor>> getAllEndpoints(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Boolean requiresAuth,
            HttpServletRequest request,
            HttpServletResponse response) {
        
        log.info("Retrieving all endpoints with filters - type: {}, requiresAuth: {}", type, requiresAuth);

        endpointScanningService.disableVerbScanning();

        List<EndpointDescriptor> endpoints = endpointScanningService.getAllEndpoints();
        
        // Apply filters if provided
        if (type != null) {
            endpoints = endpoints.stream()
                    .filter(endpoint -> type.equalsIgnoreCase(endpoint.getType()))
                    .collect(Collectors.toList());
        }
        
        if (requiresAuth != null) {
            endpoints = endpoints.stream()
                    .filter(endpoint -> endpoint.isRequiresAuthentication() == requiresAuth)
                    .collect(Collectors.toList());
        }
        
        log.info("Returning {} endpoints", endpoints.size());
        return ResponseEntity.ok(endpoints);
    }

    /**
     * Returns only REST API endpoints.
     */
    @GetMapping("/rest")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_LOG_IN})
    public ResponseEntity<List<EndpointDescriptor>> getRestEndpoints(
            HttpServletRequest request,
            HttpServletResponse response) {
        
        log.info("Retrieving REST endpoints");
        
        List<EndpointDescriptor> endpoints = endpointScanningService.getAllEndpoints()
                .stream()
                .filter(endpoint -> "REST".equals(endpoint.getType()))
                .collect(Collectors.toList());
        
        log.info("Returning {} REST endpoints", endpoints.size());
        return ResponseEntity.ok(endpoints);
    }

    /**
     * Returns only Verb methods (for AI agents).
     */
    @GetMapping("/verbs")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_LOG_IN})
    public ResponseEntity<List<EndpointDescriptor>> getVerbEndpoints(
            HttpServletRequest request,
            HttpServletResponse response) {
        
        log.info("Retrieving Verb endpoints");
        
        List<EndpointDescriptor> endpoints = endpointScanningService.getAllEndpoints()
                .stream()
                .filter(endpoint -> "VERB".equals(endpoint.getType()))
                .collect(Collectors.toList());
        
        log.info("Returning {} Verb endpoints", endpoints.size());
        return ResponseEntity.ok(endpoints);
    }

    /**
     * Forces a refresh of the endpoint cache.
     * This can be useful during development or after deploying new capabilities.
     */
    @GetMapping("/refresh")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public ResponseEntity<String> refreshEndpoints(
            HttpServletRequest request,
            HttpServletResponse response) {
        
        log.info("Refreshing endpoint cache");
        endpointScanningService.refreshEndpoints();
        
        int count = endpointScanningService.getAllEndpoints().size();
        String message = String.format("Endpoint cache refreshed. Found %d endpoints.", count);
        
        log.info(message);
        return ResponseEntity.ok(message);
    }
}