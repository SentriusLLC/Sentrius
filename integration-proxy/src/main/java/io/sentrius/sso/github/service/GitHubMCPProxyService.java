package io.sentrius.sso.github.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Service for proxying MCP requests to GitHub MCP servers
 * Enables agents to communicate with GitHub MCP servers through the integration proxy
 */
@Slf4j
@Service
public class GitHubMCPProxyService {

    private final GitHubMCPServerService githubMcpServerService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public GitHubMCPProxyService(GitHubMCPServerService githubMcpServerService, 
                                  RestTemplate restTemplate,
                                  ObjectMapper objectMapper) {
        this.githubMcpServerService = githubMcpServerService;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Forward an MCP request to the GitHub MCP server
     * 
     * @param tokenId The integration token ID identifying which MCP server to use
     * @param mcpRequest The MCP request payload
     * @return Response from the GitHub MCP server
     */
    public ResponseEntity<String> forwardMCPRequest(String tokenId, Map<String, Object> mcpRequest) {
        try {
            // Get the service URL for the MCP server
            String serviceUrl = githubMcpServerService.getServiceUrl(tokenId);
            
            log.debug("Forwarding MCP request to GitHub MCP server at: {}", serviceUrl);
            
            // Prepare headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            // Convert request to JSON
            String requestBody = objectMapper.writeValueAsString(mcpRequest);
            HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);
            
            // Forward request to MCP server
            ResponseEntity<String> response = restTemplate.exchange(
                serviceUrl + "/mcp",
                HttpMethod.POST,
                entity,
                String.class
            );
            
            log.debug("Received response from GitHub MCP server: status={}", response.getStatusCode());
            return response;
            
        } catch (Exception e) {
            log.error("Error forwarding request to GitHub MCP server for token {}", tokenId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("{\"error\": \"Failed to forward request to GitHub MCP server: " + e.getMessage() + "\"}");
        }
    }

    /**
     * Check if a GitHub MCP server is available for the given token
     */
    public boolean isServerAvailable(String tokenId) {
        try {
            String status = githubMcpServerService.getStatus(tokenId);
            return "Running".equals(status);
        } catch (Exception e) {
            log.error("Error checking GitHub MCP server status for token {}", tokenId, e);
            return false;
        }
    }

    /**
     * Get the service URL for a GitHub MCP server
     */
    public String getServiceUrl(String tokenId) {
        return githubMcpServerService.getServiceUrl(tokenId);
    }
}
