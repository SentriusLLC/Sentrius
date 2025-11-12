package io.sentrius.sso.coding.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Service for proxying MCP requests to Coding MCP servers
 * Enables agents to communicate with Coding MCP servers through the integration proxy
 */
@Slf4j
@Service
public class CodingMCPProxyService {

    private final CodingMCPServerService codingMcpServerService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public CodingMCPProxyService(CodingMCPServerService codingMcpServerService,
                                  RestTemplate restTemplate,
                                  ObjectMapper objectMapper) {
        this.codingMcpServerService = codingMcpServerService;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Forward an MCP request to the Coding MCP server
     * 
     * @param instanceId The instance ID identifying which MCP server to use
     * @param mcpRequest The MCP request payload
     * @return Response from the Coding MCP server
     */
    public ResponseEntity<String> forwardMCPRequest(String instanceId, Map<String, Object> mcpRequest) {
        try {
            // Get the service URL for the MCP server
            String serviceUrl = codingMcpServerService.getServiceUrl(instanceId);
            
            log.debug("Forwarding MCP request to Coding MCP server at: {}", serviceUrl);
            
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
            
            log.debug("Received response from Coding MCP server: status={}", response.getStatusCode());
            return response;
            
        } catch (Exception e) {
            log.error("Error forwarding request to Coding MCP server for instance {}", instanceId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("{\"error\": \"Failed to forward request to Coding MCP server: " + e.getMessage() + "\"}");
        }
    }

    /**
     * Check if a Coding MCP server is available for the given instance
     */
    public boolean isServerAvailable(String instanceId) {
        try {
            String status = codingMcpServerService.getStatus(instanceId);
            return "Running".equals(status);
        } catch (Exception e) {
            log.error("Error checking Coding MCP server status for instance {}", instanceId, e);
            return false;
        }
    }

    /**
     * Get the service URL for a Coding MCP server
     */
    public String getServiceUrl(String instanceId) {
        return codingMcpServerService.getServiceUrl(instanceId);
    }
}
