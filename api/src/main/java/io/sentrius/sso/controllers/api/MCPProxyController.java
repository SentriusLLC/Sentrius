package io.sentrius.sso.controllers.api;

import io.sentrius.sso.core.annotations.LimitAccess;
import io.sentrius.sso.core.exceptions.ZtatException;
import io.sentrius.sso.core.model.security.enums.ApplicationAccessEnum;
import io.sentrius.sso.core.services.security.KeycloakService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.Map;

/**
 * Proxy controller to forward MCP-related API calls from the frontend 
 * to the integration-proxy service using service principal authentication
 */
@RestController
@RequestMapping("/api/v1")
@Slf4j
public class MCPProxyController {

    @Value("${integration.proxy.url:http://sentrius-integrationproxy:8080}")
    private String integrationProxyUrl;

    private final KeycloakService keycloakService;
    private final RestTemplate restTemplate = new RestTemplate();

    public MCPProxyController(KeycloakService keycloakService) {
        this.keycloakService = keycloakService;
    }

    /**
     * Proxy GitHub MCP launch requests to integration-proxy
     */
    @PostMapping("/github/mcp/launch")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_LOG_IN})
    public ResponseEntity<?> launchGitHubMCP(
        @RequestParam String tokenId,
        HttpServletRequest request,
        HttpServletResponse response
    ) {
        try {
            String url = integrationProxyUrl + "/api/v1/github/mcp/launch?tokenId=" + tokenId;
            return forwardRequest(url, HttpMethod.POST, null);
        } catch (Exception e) {
            log.error("Error launching GitHub MCP server", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to launch GitHub MCP server: " + e.getMessage()));
        }
    }

    /**
     * Proxy GitHub MCP status requests to integration-proxy
     */
    @GetMapping("/github/mcp/status")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_LOG_IN})
    public ResponseEntity<?> getGitHubMCPStatus(
        @RequestParam String tokenId,
        HttpServletRequest request,
        HttpServletResponse response
    ) {
        try {
            String url = integrationProxyUrl + "/api/v1/github/mcp/status?tokenId=" + tokenId;
            return forwardRequest(url, HttpMethod.GET, null);
        } catch (Exception e) {
            log.error("Error getting GitHub MCP server status", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to get GitHub MCP server status: " + e.getMessage()));
        }
    }

    /**
     * Proxy GitHub MCP delete requests to integration-proxy
     */
    @DeleteMapping("/github/mcp/delete")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_LOG_IN})
    public ResponseEntity<?> deleteGitHubMCP(
        @RequestParam String tokenId,
        HttpServletRequest request,
        HttpServletResponse response
    ) {
        try {
            String url = integrationProxyUrl + "/api/v1/github/mcp/delete?tokenId=" + tokenId;
            return forwardRequest(url, HttpMethod.DELETE, null);
        } catch (Exception e) {
            log.error("Error deleting GitHub MCP server", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to delete GitHub MCP server: " + e.getMessage()));
        }
    }

    /**
     * Proxy GitHub MCP list requests to integration-proxy
     */
    @GetMapping("/github/mcp/list")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_LOG_IN})
    public ResponseEntity<?> listGitHubMCP(
        HttpServletRequest request,
        HttpServletResponse response
    ) {
        try {
            String url = integrationProxyUrl + "/api/v1/github/mcp/list";
            return forwardRequest(url, HttpMethod.GET, null);
        } catch (Exception e) {
            log.error("Error listing GitHub MCP servers", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to list GitHub MCP servers: " + e.getMessage()));
        }
    }

    /**
     * Proxy Coding MCP launch requests to integration-proxy
     */
    @PostMapping("/coding/mcp/launch")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_LOG_IN})
    public ResponseEntity<?> launchCodingMCP(
        @RequestParam(required = false, defaultValue = "default") String instanceId,
        @RequestParam(required = false) String githubTokenId,
        HttpServletRequest request,
        HttpServletResponse response
    ) {
        try {
            StringBuilder url = new StringBuilder(integrationProxyUrl + "/api/v1/coding/mcp/launch?instanceId=" + instanceId);
            if (githubTokenId != null && !githubTokenId.isEmpty()) {
                url.append("&githubTokenId=").append(githubTokenId);
            }
            return forwardRequest(url.toString(), HttpMethod.POST, null);
        } catch (Exception e) {
            log.error("Error launching Coding MCP server", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to launch Coding MCP server: " + e.getMessage()));
        }
    }

    /**
     * Proxy Coding MCP status requests to integration-proxy
     */
    @GetMapping("/coding/mcp/status")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_LOG_IN})
    public ResponseEntity<?> getCodingMCPStatus(
        @RequestParam(required = false, defaultValue = "default") String instanceId,
        HttpServletRequest request,
        HttpServletResponse response
    ) {
        try {
            String url = integrationProxyUrl + "/api/v1/coding/mcp/status?instanceId=" + instanceId;
            return forwardRequest(url, HttpMethod.GET, null);
        } catch (Exception e) {
            log.error("Error getting Coding MCP server status", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to get Coding MCP server status: " + e.getMessage()));
        }
    }

    /**
     * Proxy Coding MCP delete requests to integration-proxy
     */
    @DeleteMapping("/coding/mcp/delete")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_LOG_IN})
    public ResponseEntity<?> deleteCodingMCP(
        @RequestParam(required = false, defaultValue = "default") String instanceId,
        HttpServletRequest request,
        HttpServletResponse response
    ) {
        try {
            String url = integrationProxyUrl + "/api/v1/coding/mcp/delete?instanceId=" + instanceId;
            return forwardRequest(url, HttpMethod.DELETE, null);
        } catch (Exception e) {
            log.error("Error deleting Coding MCP server", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to delete Coding MCP server: " + e.getMessage()));
        }
    }

    /**
     * Proxy Coding MCP list requests to integration-proxy
     */
    @GetMapping("/coding/mcp/list")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_LOG_IN})
    public ResponseEntity<?> listCodingMCP(
        HttpServletRequest request,
        HttpServletResponse response
    ) {
        try {
            String url = integrationProxyUrl + "/api/v1/coding/mcp/list";
            return forwardRequest(url, HttpMethod.GET, null);
        } catch (Exception e) {
            log.error("Error listing Coding MCP servers", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to list Coding MCP servers: " + e.getMessage()));
        }
    }

    /**
     * Forward requests to integration-proxy using service principal authentication
     */
    private ResponseEntity<?> forwardRequest(String url, HttpMethod method, Object body) {
        try {
            // Get service principal JWT token from Keycloak
            String keycloakJwt = keycloakService.getKeycloakToken();
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(keycloakJwt);

            HttpEntity<?> entity = new HttpEntity<>(body, headers);

            log.info("Forwarding {} request to integration-proxy: {} with service principal auth", method, url);
            ResponseEntity<String> httpResponse = restTemplate.exchange(url, method, entity, String.class);
            
            return ResponseEntity.status(httpResponse.getStatusCode()).body(httpResponse.getBody());
        } catch (HttpClientErrorException e) {
            log.error("HTTP error forwarding request to integration-proxy: {} - {}", url, e.getMessage());
            return ResponseEntity.status(e.getStatusCode())
                .body(Map.of("error", "Integration proxy error: " + e.getResponseBodyAsString()));
        } catch (Exception e) {
            log.error("Error forwarding request to integration-proxy: {}", url, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to communicate with integration-proxy: " + e.getMessage()));
        }
    }
}
