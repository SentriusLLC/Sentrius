package io.sentrius.sso.coding.controller;

import io.sentrius.sso.coding.service.CodingMCPProxyService;
import io.sentrius.sso.coding.service.CodingMCPServerService;
import io.sentrius.sso.core.annotations.LimitAccess;
import io.sentrius.sso.core.model.security.enums.ApplicationAccessEnum;
import io.sentrius.sso.core.services.security.KeycloakService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Controller for managing Coding MCP server integration
 * Provides endpoints to launch, monitor, terminate, and proxy to Coding MCP server containers
 */
@RestController
@RequestMapping("/api/v1/coding")
@Slf4j
public class CodingMCPController {

    private final CodingMCPServerService codingMcpServerService;
    private final CodingMCPProxyService codingMcpProxyService;
    private final KeycloakService keycloakService;

    public CodingMCPController(
        CodingMCPServerService codingMcpServerService,
        CodingMCPProxyService codingMcpProxyService,
        KeycloakService keycloakService
    ) {
        this.codingMcpServerService = codingMcpServerService;
        this.codingMcpProxyService = codingMcpProxyService;
        this.keycloakService = keycloakService;
    }

    /**
     * Launch a Coding MCP server
     */
    @PostMapping("/mcp/launch")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_LOG_IN})
    public ResponseEntity<?> launchCodingMCPServer(
        @RequestHeader("Authorization") String token,
        @RequestParam(required = false, defaultValue = "default") String instanceId,
        @RequestParam(required = false) String githubTokenId,
        HttpServletRequest request,
        HttpServletResponse response
    ) {
        log.info("Received request to launch Coding MCP server with instance ID: {}", instanceId);

        String compactJwt = token.startsWith("Bearer ") ? token.substring(7) : token;

        if (!keycloakService.validateJwt(compactJwt)) {
            log.warn("Invalid Keycloak token");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid Keycloak token");
        }

        try {
            var pod = codingMcpServerService.launchCodingMCPServer(instanceId, githubTokenId, Map.of());
            String serviceUrl = codingMcpServerService.getServiceUrl(instanceId);

            return ResponseEntity.ok(Map.of(
                "status", "success",
                "podName", pod.getMetadata().getName(),
                "serviceUrl", serviceUrl,
                "instanceId", instanceId,
                "message", "Coding MCP server launched successfully"
            ));
        } catch (Exception e) {
            log.error("Failed to launch Coding MCP server", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "status", "error",
                "message", "Failed to launch Coding MCP server: " + e.getMessage()
            ));
        }
    }

    /**
     * Get status of a Coding MCP server
     */
    @GetMapping("/mcp/status")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_LOG_IN})
    public ResponseEntity<?> getCodingMCPServerStatus(
        @RequestHeader("Authorization") String token,
        @RequestParam(required = false, defaultValue = "default") String instanceId,
        HttpServletRequest request,
        HttpServletResponse response
    ) {
        String compactJwt = token.startsWith("Bearer ") ? token.substring(7) : token;

        if (!keycloakService.validateJwt(compactJwt)) {
            log.warn("Invalid Keycloak token");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid Keycloak token");
        }

        try {
            String status = codingMcpServerService.getStatus(instanceId);
            String serviceUrl = codingMcpServerService.getServiceUrl(instanceId);

            return ResponseEntity.ok(Map.of(
                "status", status,
                "instanceId", instanceId,
                "serviceUrl", serviceUrl
            ));
        } catch (Exception e) {
            log.error("Failed to get Coding MCP server status", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "status", "error",
                "message", "Failed to get status: " + e.getMessage()
            ));
        }
    }

    /**
     * Delete a Coding MCP server
     */
    @DeleteMapping("/mcp/delete")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_LOG_IN})
    public ResponseEntity<?> deleteCodingMCPServer(
        @RequestHeader("Authorization") String token,
        @RequestParam(required = false, defaultValue = "default") String instanceId,
        HttpServletRequest request,
        HttpServletResponse response
    ) {
        log.info("Received request to delete Coding MCP server with instance ID: {}", instanceId);

        String compactJwt = token.startsWith("Bearer ") ? token.substring(7) : token;

        if (!keycloakService.validateJwt(compactJwt)) {
            log.warn("Invalid Keycloak token");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid Keycloak token");
        }

        try {
            codingMcpServerService.deleteCodingMCPServer(instanceId);

            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Coding MCP server deleted successfully"
            ));
        } catch (Exception e) {
            log.error("Failed to delete Coding MCP server", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "status", "error",
                "message", "Failed to delete Coding MCP server: " + e.getMessage()
            ));
        }
    }

    /**
     * List all Coding MCP servers
     */
    @GetMapping("/mcp/list")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_LOG_IN})
    public ResponseEntity<?> listCodingMCPServers(
        @RequestHeader("Authorization") String token,
        HttpServletRequest request,
        HttpServletResponse response
    ) {
        String compactJwt = token.startsWith("Bearer ") ? token.substring(7) : token;

        if (!keycloakService.validateJwt(compactJwt)) {
            log.warn("Invalid Keycloak token");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid Keycloak token");
        }

        try {
            var pods = codingMcpServerService.listCodingMCPServers();

            var serverList = pods.stream().map(pod -> {
                var labels = pod.getMetadata().getLabels();
                var instanceId = labels != null ? labels.get("instance-id") : "unknown";
                var status = pod.getStatus();
                var serviceUrl = codingMcpServerService.getServiceUrl(instanceId);

                return Map.of(
                    "podName", pod.getMetadata().getName(),
                    "instanceId", instanceId,
                    "status", status != null && status.getPhase() != null ? status.getPhase() : "Unknown",
                    "serviceUrl", serviceUrl
                );
            }).toList();

            return ResponseEntity.ok(Map.of(
                "servers", serverList,
                "count", serverList.size()
            ));
        } catch (Exception e) {
            log.error("Failed to list Coding MCP servers", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "status", "error",
                "message", "Failed to list Coding MCP servers: " + e.getMessage()
            ));
        }
    }

    /**
     * Proxy MCP requests to a Coding MCP server
     * This endpoint allows agents to communicate with the Coding MCP server
     */
    @PostMapping("/mcp/proxy")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_LOG_IN})
    public ResponseEntity<String> proxyMCPRequest(
        @RequestHeader("Authorization") String token,
        @RequestParam(required = false, defaultValue = "default") String instanceId,
        @RequestBody Map<String, Object> mcpRequest,
        HttpServletRequest request,
        HttpServletResponse response
    ) {
        log.info("Proxying MCP request to Coding MCP server for instance ID: {}", instanceId);

        String compactJwt = token.startsWith("Bearer ") ? token.substring(7) : token;

        if (!keycloakService.validateJwt(compactJwt)) {
            log.warn("Invalid Keycloak token");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body("{\"error\": \"Invalid Keycloak token\"}");
        }

        // Check if server is available
        if (!codingMcpProxyService.isServerAvailable(instanceId)) {
            log.warn("Coding MCP server not available for instance ID: {}", instanceId);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body("{\"error\": \"Coding MCP server not available. Please launch it first.\"}");
        }

        try {
            return codingMcpProxyService.forwardMCPRequest(instanceId, mcpRequest);
        } catch (Exception e) {
            log.error("Failed to proxy MCP request", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("{\"error\": \"Failed to proxy MCP request: " + e.getMessage() + "\"}");
        }
    }
}
