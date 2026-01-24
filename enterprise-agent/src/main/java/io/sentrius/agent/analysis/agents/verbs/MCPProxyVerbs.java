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

/**
 * Verbs for interacting with MCP (Model Context Protocol) proxy through the integration-proxy.
 * Provides AI agents with the ability to handle generic MCP requests, get capabilities,
 * and check MCP server health.
 */
@Slf4j
@Service
public class MCPProxyVerbs {

    private final ZeroTrustClientService zeroTrustClientService;

    public MCPProxyVerbs(ZeroTrustClientService zeroTrustClientService) {
        this.zeroTrustClientService = zeroTrustClientService;
    }

    /**
     * Handle a generic MCP request via HTTP.
     *
     * @param token The zero trust token
     * @param contextDTO The execution context containing the MCP request
     * @return The MCP response
     * @throws ZtatException If there is an error during the operation
     */
    @Verb(
        name = "mcp_proxy_request",
        description = "Handle a generic MCP request via HTTP. " +
                     "Requires 'mcpRequest' parameter containing the full MCP request as JSON.",
        returnType = JsonNode.class,
        returnName = "mcp_response",
        isAiCallable = true,
        requiresTokenManagement = true,
        paramDescriptions = {
            "mcpRequest: The MCP request as JSON object"
        }
    )
    public JsonNode mcpProxyRequest(TokenDTO token, AgentExecutionContextDTO contextDTO) 
            throws ZtatException {
        try {
            JsonNode mcpRequest = contextDTO.getExecutionArgumentScoped("mcpRequest", JsonNode.class)
                .orElseThrow(() -> new IllegalArgumentException("mcpRequest parameter is required"));
            
            log.info("Handling MCP proxy request");
            
            // The request body is the MCP request itself
            String response = zeroTrustClientService.callPostOnApi(token, 
                "/api/v1/mcp/", mcpRequest);
            
            if (response == null) {
                throw new RuntimeException("No response from MCP proxy endpoint");
            }
            
            JsonNode responseNode = JsonUtil.MAPPER.readTree(response);
            log.info("Successfully handled MCP proxy request");
            return responseNode;
            
        } catch (Exception e) {
            log.error("Failed to handle MCP proxy request", e);
            throw new RuntimeException("Failed to handle MCP proxy request: " + e.getMessage(), e);
        }
    }

    /**
     * Get capabilities of the MCP server.
     *
     * @param token The zero trust token
     * @param contextDTO The execution context
     * @return The MCP server capabilities
     * @throws ZtatException If there is an error during the operation
     */
    @Verb(
        name = "mcp_get_capabilities",
        description = "Get capabilities of the MCP server. " +
                     "Returns information about what operations the MCP server supports.",
        returnType = JsonNode.class,
        returnName = "mcp_capabilities",
        isAiCallable = true,
        requiresTokenManagement = true,
        skipMemoryStorage = true
    )
    public JsonNode mcpGetCapabilities(TokenDTO token, AgentExecutionContextDTO contextDTO) 
            throws ZtatException {
        try {
            log.info("Getting MCP server capabilities");
            
            // Call the integration-proxy MCP capabilities endpoint
            String response = zeroTrustClientService.callGetOnApi(token, "/api/v1/mcp/capabilities");
            
            if (response == null) {
                throw new RuntimeException("No response from MCP capabilities endpoint");
            }
            
            JsonNode responseNode = JsonUtil.MAPPER.readTree(response);
            log.info("Successfully retrieved MCP capabilities");
            return responseNode;
            
        } catch (Exception e) {
            log.error("Failed to get MCP capabilities", e);
            throw new RuntimeException("Failed to get MCP capabilities: " + e.getMessage(), e);
        }
    }

    /**
     * Check the health of the MCP server.
     *
     * @param token The zero trust token
     * @param contextDTO The execution context
     * @return The MCP server health status
     * @throws ZtatException If there is an error during the operation
     */
    @Verb(
        name = "mcp_health_check",
        description = "Check the health status of the MCP server. " +
                     "Returns whether the MCP server is operational.",
        returnType = JsonNode.class,
        returnName = "mcp_health",
        isAiCallable = true,
        requiresTokenManagement = true,
        skipMemoryStorage = true
    )
    public JsonNode mcpHealthCheck(TokenDTO token, AgentExecutionContextDTO contextDTO) 
            throws ZtatException {
        try {
            log.info("Checking MCP server health");
            
            // Call the integration-proxy MCP health endpoint
            String response = zeroTrustClientService.callGetOnApi(token, "/api/v1/mcp/health");
            
            if (response == null) {
                throw new RuntimeException("No response from MCP health endpoint");
            }
            
            JsonNode responseNode = JsonUtil.MAPPER.readTree(response);
            log.info("Successfully checked MCP health");
            return responseNode;
            
        } catch (Exception e) {
            log.error("Failed to check MCP health", e);
            throw new RuntimeException("Failed to check MCP health: " + e.getMessage(), e);
        }
    }
}
