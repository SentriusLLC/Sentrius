package io.sentrius.agent.analysis.agents.verbs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.sentrius.sso.core.dto.agents.AgentExecutionContextDTO;
import io.sentrius.sso.core.dto.ztat.TokenDTO;
import io.sentrius.sso.core.exceptions.ZtatException;
import io.sentrius.sso.core.model.verbs.Verb;
import io.sentrius.sso.core.services.agents.ZeroTrustClientService;
import io.sentrius.sso.core.utils.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Verbs for interacting with Coding MCP through the integration-proxy.
 * Provides AI agents with the ability to launch and manage Coding MCP servers
 * for code generation and manipulation.
 */
@Slf4j
@Service
public class CodingMCPVerbs {

    private final ZeroTrustClientService zeroTrustClientService;

    public CodingMCPVerbs(ZeroTrustClientService zeroTrustClientService) {
        this.zeroTrustClientService = zeroTrustClientService;
    }

    /**
     * Launch a Coding MCP server.
     *
     * @param token The zero trust token
     * @param contextDTO The execution context containing configuration
     * @return The launch result with instance details
     * @throws ZtatException If there is an error during the operation
     */
    @Verb(
        name = "coding_mcp_launch",
        description = "Launch a Coding MCP server instance for code generation. " +
                     "Optional: 'workspace', 'config'.",
        returnType = JsonNode.class,
        returnName = "coding_instance",
        isAiCallable = true,
        requiresTokenManagement = true,
        paramDescriptions = {
            "workspace: Workspace path for the coding instance - optional",
            "config: Additional configuration as JSON - optional"
        }
    )
    public JsonNode codingMcpLaunch(TokenDTO token, AgentExecutionContextDTO contextDTO) 
            throws ZtatException {
        try {
            log.info("Launching Coding MCP server");
            
            // Build request body
            ObjectNode requestBody = JsonUtil.MAPPER.createObjectNode();
            
            // Add optional parameters
            contextDTO.getExecutionArgumentScoped("workspace", String.class)
                .ifPresent(workspace -> requestBody.put("workspace", workspace));
            contextDTO.getExecutionArgumentScoped("config", JsonNode.class)
                .ifPresent(config -> requestBody.set("config", config));
            
            // Call the integration-proxy Coding MCP launch endpoint
            String response = zeroTrustClientService.callPostOnApi(token, 
                "/api/v1/coding/mcp/launch", requestBody);
            
            if (response == null) {
                throw new RuntimeException("No response from Coding MCP launch endpoint");
            }
            
            JsonNode responseNode = JsonUtil.MAPPER.readTree(response);
            log.info("Successfully launched Coding MCP server");
            return responseNode;
            
        } catch (Exception e) {
            log.error("Failed to launch Coding MCP server", e);
            throw new RuntimeException("Failed to launch Coding MCP server: " + e.getMessage(), e);
        }
    }

    /**
     * Get status of a Coding MCP server instance.
     *
     * @param token The zero trust token
     * @param contextDTO The execution context containing instanceId
     * @return The instance status
     * @throws ZtatException If there is an error during the operation
     */
    @Verb(
        name = "coding_mcp_status",
        description = "Get status of a Coding MCP server instance. " +
                     "Optional: 'instanceId'.",
        returnType = JsonNode.class,
        returnName = "coding_status",
        isAiCallable = true,
        requiresTokenManagement = true,
        skipMemoryStorage = true,
        paramDescriptions = {
            "instanceId: The Coding MCP instance ID - optional"
        }
    )
    public JsonNode codingMcpStatus(TokenDTO token, AgentExecutionContextDTO contextDTO) 
            throws ZtatException {
        try {
            log.info("Getting Coding MCP server status");
            
            // Build query parameters if instanceId is provided
            var instanceId = contextDTO.getExecutionArgumentScoped("instanceId", String.class);
            String response;
            
            if (instanceId.isPresent()) {
                response = zeroTrustClientService.callGetOnApi(token, "/api/v1/coding/mcp/status",
                    Map.entry("instanceId", java.util.List.of(instanceId.get())));
            } else {
                response = zeroTrustClientService.callGetOnApi(token, "/api/v1/coding/mcp/status");
            }
            
            if (response == null) {
                throw new RuntimeException("No response from Coding MCP status endpoint");
            }
            
            JsonNode responseNode = JsonUtil.MAPPER.readTree(response);
            log.info("Successfully retrieved Coding MCP status");
            return responseNode;
            
        } catch (Exception e) {
            log.error("Failed to get Coding MCP status", e);
            throw new RuntimeException("Failed to get Coding MCP status: " + e.getMessage(), e);
        }
    }

    /**
     * Delete a Coding MCP server instance.
     *
     * @param token The zero trust token
     * @param contextDTO The execution context containing instanceId
     * @return The deletion result
     * @throws ZtatException If there is an error during the operation
     */
    @Verb(
        name = "coding_mcp_delete",
        description = "Delete a Coding MCP server instance. " +
                     "Requires 'instanceId' parameter.",
        returnType = JsonNode.class,
        returnName = "delete_result",
        isAiCallable = false,  // Disabled for AI due to destructive nature
        requiresTokenManagement = true,
        paramDescriptions = {
            "instanceId: The Coding MCP instance ID to delete"
        }
    )
    public JsonNode codingMcpDelete(TokenDTO token, AgentExecutionContextDTO contextDTO) 
            throws ZtatException {
        try {
            String instanceId = contextDTO.getExecutionArgumentScoped("instanceId", String.class)
                .orElseThrow(() -> new IllegalArgumentException("instanceId parameter is required"));
            
            log.warn("Deleting Coding MCP server instance: {}", instanceId);
            
            // Call the integration-proxy Coding MCP delete endpoint
            String response = zeroTrustClientService.callDeleteOnApi(token, 
                String.format("/api/v1/coding/mcp/delete?instanceId=%s", instanceId));
            
            if (response == null) {
                throw new RuntimeException("No response from Coding MCP delete endpoint");
            }
            
            JsonNode responseNode = JsonUtil.MAPPER.readTree(response);
            log.info("Successfully deleted Coding MCP server instance");
            return responseNode;
            
        } catch (Exception e) {
            log.error("Failed to delete Coding MCP server", e);
            throw new RuntimeException("Failed to delete Coding MCP server: " + e.getMessage(), e);
        }
    }

    /**
     * List all Coding MCP server instances.
     *
     * @param token The zero trust token
     * @param contextDTO The execution context
     * @return List of Coding MCP instances
     * @throws ZtatException If there is an error during the operation
     */
    @Verb(
        name = "coding_mcp_list",
        description = "List all Coding MCP server instances.",
        returnType = JsonNode.class,
        returnName = "coding_instances",
        isAiCallable = true,
        requiresTokenManagement = true,
        skipMemoryStorage = true
    )
    public JsonNode codingMcpList(TokenDTO token, AgentExecutionContextDTO contextDTO) 
            throws ZtatException {
        try {
            log.info("Listing Coding MCP server instances");
            
            // Call the integration-proxy Coding MCP list endpoint
            String response = zeroTrustClientService.callGetOnApi(token, "/api/v1/coding/mcp/list");
            
            if (response == null) {
                throw new RuntimeException("No response from Coding MCP list endpoint");
            }
            
            JsonNode responseNode = JsonUtil.MAPPER.readTree(response);
            log.info("Successfully listed Coding MCP server instances");
            return responseNode;
            
        } catch (Exception e) {
            log.error("Failed to list Coding MCP servers", e);
            throw new RuntimeException("Failed to list Coding MCP servers: " + e.getMessage(), e);
        }
    }

    /**
     * Proxy a request to a Coding MCP server.
     *
     * @param token The zero trust token
     * @param contextDTO The execution context containing instanceId and request
     * @return The proxy response
     * @throws ZtatException If there is an error during the operation
     */
    @Verb(
        name = "coding_mcp_proxy",
        description = "Proxy a request to a Coding MCP server. " +
                     "Requires 'instanceId' and 'request' parameters.",
        returnType = JsonNode.class,
        returnName = "proxy_response",
        isAiCallable = true,
        requiresTokenManagement = true,
        paramDescriptions = {
            "instanceId: The Coding MCP instance ID",
            "request: The MCP request as JSON"
        }
    )
    public JsonNode codingMcpProxy(TokenDTO token, AgentExecutionContextDTO contextDTO) 
            throws ZtatException {
        try {
            String instanceId = contextDTO.getExecutionArgumentScoped("instanceId", String.class)
                .orElseThrow(() -> new IllegalArgumentException("instanceId parameter is required"));
            JsonNode request = contextDTO.getExecutionArgumentScoped("request", JsonNode.class)
                .orElseThrow(() -> new IllegalArgumentException("request parameter is required"));
            
            log.info("Proxying request to Coding MCP instance: {}", instanceId);
            
            // Build request body
            ObjectNode requestBody = JsonUtil.MAPPER.createObjectNode();
            requestBody.put("instanceId", instanceId);
            requestBody.set("request", request);
            
            // Call the integration-proxy Coding MCP proxy endpoint
            String response = zeroTrustClientService.callPostOnApi(token, 
                "/api/v1/coding/mcp/proxy", requestBody);
            
            if (response == null) {
                throw new RuntimeException("No response from Coding MCP proxy endpoint");
            }
            
            JsonNode responseNode = JsonUtil.MAPPER.readTree(response);
            log.info("Successfully proxied request to Coding MCP");
            return responseNode;
            
        } catch (Exception e) {
            log.error("Failed to proxy Coding MCP request", e);
            throw new RuntimeException("Failed to proxy Coding MCP request: " + e.getMessage(), e);
        }
    }
}
