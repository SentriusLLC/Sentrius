package io.sentrius.sso.mcp.service;

import io.sentrius.sso.core.services.security.KeycloakService;
import io.sentrius.sso.core.services.security.ZeroTrustAccessTokenService;
import io.sentrius.sso.core.services.security.ZeroTrustRequestService;
import io.sentrius.sso.mcp.model.MCPRequest;
import io.sentrius.sso.mcp.model.MCPResponse;
import io.sentrius.sso.mcp.model.MCPError;
import io.sentrius.sso.provenance.ProvenanceEvent;
import io.sentrius.sso.provenance.kafka.ProvenanceKafkaProducer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

import java.time.Instant;
import java.util.Map;
import java.util.HashMap;
import java.util.List;

/**
 * Service for handling MCP (Model Context Protocol) requests with zero trust security
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MCPProxyService {
    
    private final KeycloakService keycloakService;
    private final ZeroTrustAccessTokenService ztatService;
    private final ZeroTrustRequestService ztrService;
    private final ProvenanceKafkaProducer provenanceKafkaProducer;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    
    /**
     * Process MCP request with full security validation
     */
    public MCPResponse processRequest(MCPRequest request, String jwtToken, String communicationId, String userId) {
        log.info("Processing MCP request: method={}, id={}, userId={}", request.getMethod(), request.getId(), userId);
        
        try {
            // Validate JWT token
            if (!keycloakService.validateJwt(jwtToken)) {
                log.warn("Invalid JWT token for MCP request");
                return MCPResponse.error(request.getId(), MCPError.unauthorized("Invalid JWT token"));
            }
            
            // Submit provenance event for the request
            submitProvenanceEvent(request, userId, communicationId, "ENDPOINT_ACCESS");
            
            // Route request based on method
            MCPResponse response = routeRequest(request, jwtToken, communicationId, userId);
            
            // Submit provenance event for the response
            submitProvenanceEvent(request, userId, communicationId, "ENDPOINT_ACCESS");
            
            return response;
            
        } catch (Exception e) {
            log.error("Error processing MCP request", e);
            submitProvenanceEvent(request, userId, communicationId, "UNKNOWN");
            return MCPResponse.error(request.getId(), MCPError.internalError("Internal server error"));
        }
    }
    
    /**
     * Route MCP request based on method
     */
    private MCPResponse routeRequest(MCPRequest request, String jwtToken, String communicationId, String userId) {
        String method = request.getMethod();
        
        switch (method) {
            case "initialize":
                return handleInitialize(request, userId);
            case "ping":
                return handlePing(request);
            case "tools/list":
                return handleToolsList(request, jwtToken, userId);
            case "tools/call":
                return handleToolsCall(request, jwtToken, communicationId, userId);
            case "resources/list":
                return handleResourcesList(request, jwtToken, userId);
            case "resources/read":
                return handleResourcesRead(request, jwtToken, userId);
            case "prompts/list":
                return handlePromptsList(request, jwtToken, userId);
            case "prompts/get":
                return handlePromptsGet(request, jwtToken, userId);
            case "completion":
                return handleCompletion(request, jwtToken, communicationId, userId);
            default:
                log.warn("Unknown MCP method: {}", method);
                return MCPResponse.error(request.getId(), MCPError.methodNotFound(method));
        }
    }
    
    /**
     * Handle MCP initialize request
     */
    private MCPResponse handleInitialize(MCPRequest request, String userId) {
        log.info("Handling MCP initialize for user: {}", userId);
        
        Map<String, Object> result = new HashMap<>();
        result.put("protocolVersion", "2024-11-05");
        result.put("capabilities", createCapabilities());
        result.put("serverInfo", createServerInfo());
        
        return MCPResponse.success(request.getId(), result);
    }
    
    /**
     * Handle ping request for connectivity check
     */
    private MCPResponse handlePing(MCPRequest request) {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "ok");
        result.put("timestamp", Instant.now().toString());
        
        return MCPResponse.success(request.getId(), result);
    }
    
    /**
     * Handle tools/list request
     */
    private MCPResponse handleToolsList(MCPRequest request, String jwtToken, String userId) {
        // This would typically fetch available tools based on user permissions
        Map<String, Object> result = new HashMap<>();
        result.put("tools", createAvailableTools(userId));
        
        return MCPResponse.success(request.getId(), result);
    }
    
    /**
     * Handle tools/call request - this requires ZTAT validation
     */
    private MCPResponse handleToolsCall(MCPRequest request, String jwtToken, String communicationId, String userId) {
        log.info("Handling tools/call for user: {}", userId);
        
        // Extract tool parameters
        Map<String, Object> params = request.getParams();
        if (params == null || !params.containsKey("name")) {
            return MCPResponse.error(request.getId(), MCPError.invalidParams("Tool name is required"));
        }
        
        String toolName = (String) params.get("name");
        Map<String, Object> arguments = (Map<String, Object>) params.get("arguments");
        
        // This is where you would validate ZTAT tokens for sensitive operations
        // For now, we'll simulate tool execution
        Map<String, Object> result = executeTool(toolName, arguments, userId);
        
        return MCPResponse.success(request.getId(), result);
    }
    
    /**
     * Handle resources/list request
     */
    private MCPResponse handleResourcesList(MCPRequest request, String jwtToken, String userId) {
        Map<String, Object> result = new HashMap<>();
        result.put("resources", createAvailableResources(userId));
        
        return MCPResponse.success(request.getId(), result);
    }
    
    /**
     * Handle resources/read request
     */
    private MCPResponse handleResourcesRead(MCPRequest request, String jwtToken, String userId) {
        Map<String, Object> params = request.getParams();
        if (params == null || !params.containsKey("uri")) {
            return MCPResponse.error(request.getId(), MCPError.invalidParams("Resource URI is required"));
        }
        
        String uri = (String) params.get("uri");
        Map<String, Object> result = readResource(uri, userId);
        
        return MCPResponse.success(request.getId(), result);
    }
    
    /**
     * Handle prompts/list request
     */
    private MCPResponse handlePromptsList(MCPRequest request, String jwtToken, String userId) {
        Map<String, Object> result = new HashMap<>();
        result.put("prompts", createAvailablePrompts(userId));
        
        return MCPResponse.success(request.getId(), result);
    }
    
    /**
     * Handle prompts/get request
     */
    private MCPResponse handlePromptsGet(MCPRequest request, String jwtToken, String userId) {
        Map<String, Object> params = request.getParams();
        if (params == null || !params.containsKey("name")) {
            return MCPResponse.error(request.getId(), MCPError.invalidParams("Prompt name is required"));
        }
        
        String promptName = (String) params.get("name");
        Map<String, Object> result = getPrompt(promptName, userId);
        
        return MCPResponse.success(request.getId(), result);
    }
    
    /**
     * Handle completion request - delegates to LLM services
     */
    private MCPResponse handleCompletion(MCPRequest request, String jwtToken, String communicationId, String userId) {
        log.info("Handling completion request for user: {}", userId);
        
        // This would delegate to existing LLM services
        // For now, return a placeholder response
        Map<String, Object> result = new HashMap<>();
        result.put("content", "This would be handled by the LLM service");
        result.put("model", "mcp-proxy");
        
        return MCPResponse.success(request.getId(), result);
    }
    
    /**
     * Submit provenance event for audit trail
     */
    private void submitProvenanceEvent(MCPRequest request, String userId, String communicationId, String eventType) {
        try {
            ProvenanceEvent event = ProvenanceEvent.builder()
                .eventType(ProvenanceEvent.EventType.valueOf(eventType))
                .sessionId(communicationId)
                .actor(userId)
                .triggeringUser(userId)
                .timestamp(Instant.now())
                .input("MCP " + request.getMethod() + " request")
                .outputSummary("MCP request processed")
                .build();
            
            provenanceKafkaProducer.send(event);
        } catch (Exception e) {
            log.warn("Failed to submit provenance event", e);
        }
    }
    
    // Helper methods for creating MCP-specific data structures
    
    private Map<String, Object> createCapabilities() {
        Map<String, Object> capabilities = new HashMap<>();
        capabilities.put("tools", Map.of("listChanged", true));
        capabilities.put("resources", Map.of("subscribe", true, "listChanged", true));
        capabilities.put("prompts", Map.of("listChanged", true));
        return capabilities;
    }
    
    private Map<String, Object> createServerInfo() {
        Map<String, Object> serverInfo = new HashMap<>();
        serverInfo.put("name", "Sentrius MCP Proxy");
        serverInfo.put("version", "1.0.0");
        return serverInfo;
    }
    
    private Object createAvailableTools(String userId) {
        // Return tools available to this user based on permissions
        return Map.of("tools", new Object[]{
            Map.of(
                "name", "secure_command",
                "description", "Execute secure commands with ZTAT validation",
                "inputSchema", Map.of("type", "object", "properties", Map.of(
                    "command", Map.of("type", "string", "description", "Command to execute")
                ))
            )
        });
    }
    
    private Object createAvailableResources(String userId) {
        // Return resources available to this user
        return new Object[]{
            Map.of(
                "uri", "sentrius://config/user-settings",
                "name", "User Settings",
                "description", "User configuration settings",
                "mimeType", "application/json"
            )
        };
    }
    
    private Object createAvailablePrompts(String userId) {
        // Return prompts available to this user
        return new Object[]{
            Map.of(
                "name", "security_analysis",
                "description", "Analyze security posture",
                "arguments", new Object[]{
                    Map.of("name", "target", "description", "Target to analyze", "required", true)
                }
            )
        };
    }
    
    private Map<String, Object> executeTool(String toolName, Map<String, Object> arguments, String userId) {
        // Tool execution logic would go here
        // This would integrate with existing Sentrius services
        Map<String, Object> result = new HashMap<>();
        result.put("content", List.of(Map.of(
            "type", "text",
            "text", "Tool '" + toolName + "' executed successfully"
        )));
        return result;
    }
    
    private Map<String, Object> readResource(String uri, String userId) {
        // Resource reading logic would go here
        Map<String, Object> result = new HashMap<>();
        result.put("contents", List.of(Map.of(
            "uri", uri,
            "mimeType", "application/json",
            "text", "{\"message\": \"Resource content\"}"
        )));
        return result;
    }
    
    private Map<String, Object> getPrompt(String promptName, String userId) {
        // Prompt retrieval logic would go here
        Map<String, Object> result = new HashMap<>();
        result.put("description", "Retrieved prompt: " + promptName);
        result.put("messages", List.of(Map.of(
            "role", "user",
            "content", Map.of("type", "text", "text", "Sample prompt content")
        )));
        return result;
    }
}