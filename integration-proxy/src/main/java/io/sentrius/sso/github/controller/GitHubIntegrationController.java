package io.sentrius.sso.github.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.sentrius.sso.core.annotations.LimitAccess;
import io.sentrius.sso.core.config.SystemOptions;
import io.sentrius.sso.core.model.security.IntegrationSecurityToken;
import io.sentrius.sso.core.model.security.enums.ApplicationAccessEnum;
import io.sentrius.sso.core.model.verbs.Endpoint;
import io.sentrius.sso.core.services.security.IntegrationSecurityTokenService;
import io.sentrius.sso.core.services.security.KeycloakService;
import io.sentrius.sso.github.service.GitHubMCPAdapter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Controller for GitHub integration via native API implementation
 * The integration proxy acts as the direct API handler, making GitHub API calls
 * No external pods or containers are needed - all operations are handled directly
 */
@RestController
@RequestMapping("/api/v1/github")
@Slf4j
public class GitHubIntegrationController {

    private final GitHubMCPAdapter githubMcpAdapter;
    private final IntegrationSecurityTokenService tokenService;
    private final KeycloakService keycloakService;
    private final ObjectMapper objectMapper;
    private final SystemOptions systemOptions;

    public GitHubIntegrationController(
        GitHubMCPAdapter githubMcpAdapter,
        IntegrationSecurityTokenService tokenService,
        KeycloakService keycloakService,
        ObjectMapper objectMapper,
        SystemOptions systemOptions
    ) {
        this.githubMcpAdapter = githubMcpAdapter;
        this.tokenService = tokenService;
        this.keycloakService = keycloakService;
        this.objectMapper = objectMapper;
        this.systemOptions = systemOptions;
    }

    /**
     * Enable GitHub integration for a specific token
     * Saves the token name to SystemOptions for persistent use
     */
    @PostMapping("/mcp/launch")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_LOG_IN})
    public ResponseEntity<?> enableGitHubIntegration(
        @RequestHeader("Authorization") String token,
        @RequestParam String tokenId,
        HttpServletRequest request,
        HttpServletResponse response
    ) {
        log.info("Enabling GitHub integration for token ID: {}", tokenId);

        String compactJwt = token.startsWith("Bearer ") ? token.substring(7) : token;

        if (!keycloakService.validateJwt(compactJwt)) {
            log.warn("Invalid Keycloak token");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid Keycloak token");
        }

        try {
            // Validate the token exists and is a GitHub token
            Optional<IntegrationSecurityToken> tokenOpt = tokenService.findById(Long.parseLong(tokenId));
            if (tokenOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "status", "error",
                    "message", "GitHub integration token not found: " + tokenId
                ));
            }

            IntegrationSecurityToken integrationToken = tokenOpt.get();
            if (!"github".equals(integrationToken.getConnectionType())) {
                return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", "Token is not a GitHub integration token"
                ));
            }

            // Save the token name to SystemOptions for future use
            String tokenName = integrationToken.getName();
            systemOptions.setValue("githubAgentTokenName", tokenName);
            log.info("Saved GitHub agent token name to SystemOptions: {}", tokenName);

            // Token is valid and ready to use
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "tokenId", tokenId,
                "tokenName", tokenName,
                "message", "GitHub integration enabled successfully - ready to use"
            ));
        } catch (NumberFormatException e) {
            log.error("Invalid token ID format: {}", tokenId, e);
            return ResponseEntity.badRequest().body(Map.of(
                "status", "error",
                "message", "Invalid token ID format"
            ));
        } catch (Exception e) {
            log.error("Failed to enable GitHub integration", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "status", "error",
                "message", "Failed to enable GitHub integration: " + e.getMessage()
            ));
        }
    }

    /**
     * Get status of GitHub integration for a token
     * Always returns active since direct API calls are always available
     */
    @GetMapping("/mcp/status")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_LOG_IN})
    public ResponseEntity<?> getGitHubIntegrationStatus(
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
            // Validate the token exists and is a GitHub token
            Optional<IntegrationSecurityToken> tokenOpt = tokenService.findById(Long.parseLong(tokenId));
            if (tokenOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "status", "not_found",
                    "message", "Token not found"
                ));
            }

            IntegrationSecurityToken integrationToken = tokenOpt.get();
            if (!"github".equals(integrationToken.getConnectionType())) {
                return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", "Token is not a GitHub integration token"
                ));
            }

            return ResponseEntity.ok(Map.of(
                "status", "active",
                "tokenId", tokenId,
                "message", "GitHub integration is ready"
            ));
        } catch (Exception e) {
            log.error("Failed to get GitHub integration status", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "status", "error",
                "message", "Failed to get status: " + e.getMessage()
            ));
        }
    }

    /**
     * Disable GitHub integration for a token (no-op since always available)
     */
    @DeleteMapping("/mcp/delete")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_LOG_IN})
    public ResponseEntity<?> disableGitHubIntegration(
        @RequestHeader("Authorization") String token,
        @RequestParam String tokenId,
        HttpServletRequest request,
        HttpServletResponse response
    ) {
        log.info("Disabling GitHub integration for token ID: {}", tokenId);

        String compactJwt = token.startsWith("Bearer ") ? token.substring(7) : token;

        if (!keycloakService.validateJwt(compactJwt)) {
            log.warn("Invalid Keycloak token");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid Keycloak token");
        }

        // Direct API is always available when token exists
        return ResponseEntity.ok(Map.of(
            "status", "success",
            "message", "GitHub integration disabled (direct API always available)"
        ));
    }

    /**
     * List GitHub integration tokens
     */
    @GetMapping("/mcp/list")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_LOG_IN})
    public ResponseEntity<?> listGitHubIntegrations(
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
            var githubTokens = tokenService.findByConnectionType("github");

            var integrationList = githubTokens.stream().map(integrationToken -> Map.of(
                "tokenId", integrationToken.getId().toString(),
                "name", integrationToken.getName(),
                "status", "active",
                "message", "Ready to use"
            )).toList();

            return ResponseEntity.ok(Map.of(
                "integrations", integrationList,
                "count", integrationList.size()
            ));
        } catch (Exception e) {
            log.error("Failed to list GitHub integrations", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "status", "error",
                "message", "Failed to list GitHub integrations: " + e.getMessage()
            ));
        }
    }

    /**
     * Proxy API requests directly to GitHub API
     * The integration proxy processes requests using GitHubMCPAdapter
     * Uses the token name saved in SystemOptions from the Launch Agent UI
     * Returns 404 if no token has been configured
     */
    @PostMapping("/mcp/proxy")
    @Endpoint(description = "MCP proxy for github requests. Most queries for tickets, pull requests, etc should occur" +
        " through this endpoint. The configured GitHub token will be used to make API calls.")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_LOG_IN})
    public ResponseEntity<String> proxyMCPRequest(
        @RequestHeader("Authorization") String token,
        @RequestBody Map<String, Object> mcpRequest,
        HttpServletRequest request,
        HttpServletResponse response
    ) {
        String compactJwt = token.startsWith("Bearer ") ? token.substring(7) : token;

        if (!keycloakService.validateJwt(compactJwt)) {
            log.warn("Invalid Keycloak token");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body("{\"error\": \"Invalid Keycloak token\"}");
        }

        // Get the configured GitHub token name from SystemOptions
        String configuredTokenName = systemOptions.getGithubAgentTokenName();
        if (configuredTokenName == null || configuredTokenName.trim().isEmpty()) {
            log.warn("No GitHub token configured in SystemOptions");
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body("{\"error\": \"No GitHub token configured. Please select a token from the Launch Agent UI or configure it in System Settings.\"}");
        }

        try {
            // Find the token by name
            List<IntegrationSecurityToken> githubTokens = tokenService.findByConnectionType("github");
            Optional<IntegrationSecurityToken> configuredToken = githubTokens.stream()
                .filter(t -> configuredTokenName.equals(t.getName()))
                .findFirst();

            if (configuredToken.isEmpty()) {
                log.warn("Configured GitHub token '{}' not found", configuredTokenName);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("{\"error\": \"Configured GitHub token '" + configuredTokenName + "' not found. Please reconfigure in Launch Agent UI.\"}");
            }

            String tokenId = String.valueOf(configuredToken.get().getId());
            log.info("Proxying MCP request to GitHub API using configured token: {} (ID: {})", configuredTokenName, tokenId);

            // Convert request map to JsonNode
            JsonNode requestNode = objectMapper.valueToTree(mcpRequest);
            
            // Process request through MCP adapter
            JsonNode mcpResponse = githubMcpAdapter.processRequest(tokenId, requestNode);
            
            // Return response
            return ResponseEntity.ok()
                .header("Content-Type", "application/json")
                .body(objectMapper.writeValueAsString(mcpResponse));
                
        } catch (Exception e) {
            log.error("Failed to proxy MCP request", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("{\"error\": \"Failed to proxy MCP request: " + e.getMessage() + "\"}");
        }
    }
}
