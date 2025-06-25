package io.sentrius.sso.controllers.api.mcp;

import io.sentrius.sso.core.controllers.BaseController;
import io.sentrius.sso.core.annotations.LimitAccess;
import io.sentrius.sso.core.model.security.enums.ApplicationAccessEnum;
import io.sentrius.sso.core.services.UserService;
import io.sentrius.sso.core.config.SystemOptions;
import io.sentrius.sso.core.services.ErrorOutputService;
import io.sentrius.sso.core.services.security.KeycloakService;
import io.sentrius.sso.mcp.model.MCPRequest;
import io.sentrius.sso.mcp.model.MCPResponse;
import io.sentrius.sso.mcp.model.MCPError;
import io.sentrius.sso.mcp.service.MCPProxyService;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;

/**
 * MCP (Model Context Protocol) Proxy Controller with Zero Trust Security
 * 
 * Provides secure MCP endpoints with the same security controls as other Sentrius services:
 * - JWT authentication via Keycloak
 * - Zero Trust Access Token (ZTAT) validation
 * - Access control via @LimitAccess annotations
 * - Provenance tracking for audit trails
 */
@RestController
@RequestMapping("/api/v1/mcp")
@Slf4j
public class MCPProxyController extends BaseController {

    private final KeycloakService keycloakService;
    private final MCPProxyService mcpProxyService;
    private final ObjectMapper objectMapper;

    public MCPProxyController(
        UserService userService, 
        SystemOptions systemOptions,
        ErrorOutputService errorOutputService,
        KeycloakService keycloakService,
        MCPProxyService mcpProxyService,
        ObjectMapper objectMapper
    ) {
        super(userService, systemOptions, errorOutputService);
        this.keycloakService = keycloakService;
        this.mcpProxyService = mcpProxyService;
        this.objectMapper = objectMapper;
    }

    /**
     * Handle MCP requests via HTTP POST
     */
    @PostMapping("/")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_LOG_IN})
    public ResponseEntity<?> handleMCPRequest(
            @RequestHeader("Authorization") String token,
            @RequestHeader("communication_id") String communicationId,
            HttpServletRequest request, 
            HttpServletResponse response,
            @RequestBody String rawBody) {
        
        log.info("Received MCP request with communication_id: {}", communicationId);
        
        String compactJwt = extractJwtToken(token);
        
        // Validate JWT token
        if (!keycloakService.validateJwt(compactJwt)) {
            log.warn("Invalid Keycloak token for MCP request");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(createErrorResponse(null, MCPError.unauthorized("Invalid Keycloak token")));
        }

        // Get operating user
        var operatingUser = getOperatingUser(request, response);
        if (operatingUser == null) {
            log.warn("No operating user found for MCP request");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(createErrorResponse(null, MCPError.unauthorized("No operating user found")));
        }

        try {
            // Parse MCP request
            MCPRequest mcpRequest = objectMapper.readValue(rawBody, MCPRequest.class);
            
            // Validate MCP request structure
            if (mcpRequest.getMethod() == null || mcpRequest.getId() == null) {
                return ResponseEntity.badRequest()
                    .body(createErrorResponse(mcpRequest.getId(), MCPError.invalidRequest("Missing required fields")));
            }
            
            // Process the request through the service layer
            MCPResponse mcpResponse = mcpProxyService.processRequest(
                mcpRequest, compactJwt, communicationId, operatingUser.getUsername()
            );
            
            return ResponseEntity.ok(mcpResponse);
            
        } catch (JsonProcessingException e) {
            log.error("Failed to parse MCP request", e);
            return ResponseEntity.badRequest()
                .body(createErrorResponse(null, MCPError.parseError("Invalid JSON format")));
        } catch (Exception e) {
            log.error("Unexpected error processing MCP request", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(createErrorResponse(null, MCPError.internalError("Internal server error")));
        }
    }

    /**
     * Handle MCP capability discovery
     */
    @GetMapping("/capabilities")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_LOG_IN})
    public ResponseEntity<?> getCapabilities(
            @RequestHeader("Authorization") String token,
            HttpServletRequest request, 
            HttpServletResponse response) {
        
        String compactJwt = extractJwtToken(token);
        
        if (!keycloakService.validateJwt(compactJwt)) {
            log.warn("Invalid Keycloak token for MCP capabilities request");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(MCPError.unauthorized("Invalid Keycloak token"));
        }

        var operatingUser = getOperatingUser(request, response);
        if (operatingUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(MCPError.unauthorized("No operating user found"));
        }

        try {
            // Create an initialize request to get capabilities
            MCPRequest initRequest = MCPRequest.create("capabilities", "initialize", null);
            MCPResponse mcpResponse = mcpProxyService.processRequest(
                initRequest, compactJwt, "capabilities", operatingUser.getUsername()
            );
            
            return ResponseEntity.ok(mcpResponse.getResult());
            
        } catch (Exception e) {
            log.error("Error getting MCP capabilities", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(MCPError.internalError("Failed to get capabilities"));
        }
    }

    /**
     * Health check endpoint for MCP proxy
     */
    @GetMapping("/health")
    public ResponseEntity<?> health() {
        return ResponseEntity.ok(Map.of(
            "status", "healthy",
            "service", "mcp-proxy",
            "timestamp", java.time.Instant.now().toString()
        ));
    }

    /**
     * Extract JWT token from Authorization header
     */
    private String extractJwtToken(String authHeader) {
        return authHeader != null && authHeader.startsWith("Bearer ") ? 
            authHeader.substring(7) : authHeader;
    }

    /**
     * Create error response in MCP format
     */
    private MCPResponse createErrorResponse(String id, MCPError error) {
        return MCPResponse.error(id, error);
    }
}