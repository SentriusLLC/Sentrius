package io.sentrius.sso.github.controller;

import io.sentrius.sso.core.annotations.LimitAccess;
import io.sentrius.sso.core.model.security.enums.ApplicationAccessEnum;
import io.sentrius.sso.core.services.security.KeycloakService;
import io.sentrius.sso.github.service.GitHubMCPServerService;
import io.sentrius.sso.github.service.GitHubMCPProxyService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Controller for managing GitHub MCP server integration
 * Provides endpoints to launch, monitor, terminate, and proxy to GitHub MCP server containers
 */
@RestController
@RequestMapping("/api/v1/github")
@Slf4j
public class GitHubIntegrationController {

    private final GitHubMCPServerService githubMcpServerService;
    private final GitHubMCPProxyService githubMcpProxyService;
    private final KeycloakService keycloakService;

    public GitHubIntegrationController(
        GitHubMCPServerService githubMcpServerService,
        GitHubMCPProxyService githubMcpProxyService,
        KeycloakService keycloakService
    ) {
        this.githubMcpServerService = githubMcpServerService;
        this.githubMcpProxyService = githubMcpProxyService;
        this.keycloakService = keycloakService;
    }

    /**
     * Launch a GitHub MCP server for a specific integration token
     */
    @PostMapping("/mcp/launch")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_LOG_IN})
    public ResponseEntity<?> launchGitHubMCPServer(
        @RequestHeader("Authorization") String token,
        @RequestParam String tokenId,
        HttpServletRequest request,
        HttpServletResponse response
    ) {
        log.info("Received request to launch GitHub MCP server for token ID: {}", tokenId);

        String compactJwt = token.startsWith("Bearer ") ? token.substring(7) : token;

        if (!keycloakService.validateJwt(compactJwt)) {
            log.warn("Invalid Keycloak token");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid Keycloak token");
        }

        try {
            var pod = githubMcpServerService.launchGitHubMCPServer(tokenId);
            String serviceUrl = githubMcpServerService.getServiceUrl(tokenId);

            return ResponseEntity.ok(Map.of(
                "status", "success",
                "podName", pod.getMetadata().getName(),
                "serviceUrl", serviceUrl,
                "message", "GitHub MCP server launched successfully"
            ));
        } catch (IllegalArgumentException e) {
            log.error("Invalid token ID or token type: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                "status", "error",
                "message", e.getMessage()
            ));
        } catch (Exception e) {
            log.error("Failed to launch GitHub MCP server", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "status", "error",
                "message", "Failed to launch GitHub MCP server: " + e.getMessage()
            ));
        }
    }

    /**
     * Get status of a GitHub MCP server
     */
    @GetMapping("/mcp/status")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_LOG_IN})
    public ResponseEntity<?> getGitHubMCPServerStatus(
        @RequestHeader("Authorization") String token,
        @RequestParam String tokenId,
        HttpServletRequest request,
        HttpServletResponse response
    ) {
        String compactJwt = token.startsWith("Bearer ") ? token.substring(7) : token;

        if (!keycloakService.validateJwt(compactJwt)) {
            log.warn("Invalid Keycloak token");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid Keycloak token");
        }

        try {
            String status = githubMcpServerService.getStatus(tokenId);
            String serviceUrl = githubMcpServerService.getServiceUrl(tokenId);

            return ResponseEntity.ok(Map.of(
                "status", status,
                "tokenId", tokenId,
                "serviceUrl", serviceUrl
            ));
        } catch (Exception e) {
            log.error("Failed to get GitHub MCP server status", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "status", "error",
                "message", "Failed to get status: " + e.getMessage()
            ));
        }
    }

    /**
     * Delete a GitHub MCP server
     */
    @DeleteMapping("/mcp/delete")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_LOG_IN})
    public ResponseEntity<?> deleteGitHubMCPServer(
        @RequestHeader("Authorization") String token,
        @RequestParam String tokenId,
        HttpServletRequest request,
        HttpServletResponse response
    ) {
        log.info("Received request to delete GitHub MCP server for token ID: {}", tokenId);

        String compactJwt = token.startsWith("Bearer ") ? token.substring(7) : token;

        if (!keycloakService.validateJwt(compactJwt)) {
            log.warn("Invalid Keycloak token");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid Keycloak token");
        }

        try {
            githubMcpServerService.deleteGitHubMCPServer(tokenId);

            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "GitHub MCP server deleted successfully"
            ));
        } catch (Exception e) {
            log.error("Failed to delete GitHub MCP server", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "status", "error",
                "message", "Failed to delete GitHub MCP server: " + e.getMessage()
            ));
        }
    }

    /**
     * List all GitHub MCP servers
     */
    @GetMapping("/mcp/list")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_LOG_IN})
    public ResponseEntity<?> listGitHubMCPServers(
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
            var pods = githubMcpServerService.listGitHubMCPServers();

            var serverList = pods.stream().map(pod -> {
                var labels = pod.getMetadata().getLabels();
                var tokenId = labels != null ? labels.get("token-id") : null;
                var status = pod.getStatus();
                var serviceUrl = tokenId != null ? githubMcpServerService.getServiceUrl(tokenId) : "unknown";

                return Map.of(
                    "podName", pod.getMetadata().getName(),
                    "tokenId", tokenId != null ? tokenId : "unknown",
                    "status", status != null && status.getPhase() != null ? status.getPhase() : "Unknown",
                    "serviceUrl", serviceUrl
                );
            }).toList();

            return ResponseEntity.ok(Map.of(
                "servers", serverList,
                "count", serverList.size()
            ));
        } catch (Exception e) {
            log.error("Failed to list GitHub MCP servers", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "status", "error",
                "message", "Failed to list GitHub MCP servers: " + e.getMessage()
            ));
        }
    }

    /**
     * Proxy MCP requests to a GitHub MCP server
     * This endpoint allows agents to communicate with the GitHub MCP server
     */
    @PostMapping("/mcp/proxy")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_LOG_IN})
    public ResponseEntity<String> proxyMCPRequest(
        @RequestHeader("Authorization") String token,
        @RequestParam String tokenId,
        @RequestBody Map<String, Object> mcpRequest,
        HttpServletRequest request,
        HttpServletResponse response
    ) {
        log.info("Proxying MCP request to GitHub MCP server for token ID: {}", tokenId);

        String compactJwt = token.startsWith("Bearer ") ? token.substring(7) : token;

        if (!keycloakService.validateJwt(compactJwt)) {
            log.warn("Invalid Keycloak token");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body("{\"error\": \"Invalid Keycloak token\"}");
        }

        // Check if server is available
        if (!githubMcpProxyService.isServerAvailable(tokenId)) {
            log.warn("GitHub MCP server not available for token ID: {}", tokenId);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body("{\"error\": \"GitHub MCP server not available. Please launch it first.\"}");
        }

        try {
            return githubMcpProxyService.forwardMCPRequest(tokenId, mcpRequest);
        } catch (Exception e) {
            log.error("Failed to proxy MCP request", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("{\"error\": \"Failed to proxy MCP request: " + e.getMessage() + "\"}");
        }
    }
}
