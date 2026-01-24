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
 * Verbs for interacting with MCP (Model Context Protocol) integrations through the integration-proxy.
 * Provides AI agents with the ability to execute MCP operations for various services
 * (filesystem, PostgreSQL, Slack, Playwright, fetch).
 */
@Slf4j
@Service
public class MCPIntegrationVerbs {

    private final ZeroTrustClientService zeroTrustClientService;

    public MCPIntegrationVerbs(ZeroTrustClientService zeroTrustClientService) {
        this.zeroTrustClientService = zeroTrustClientService;
    }

    /**
     * Execute filesystem operations through MCP.
     *
     * @param token The zero trust token
     * @param contextDTO The execution context containing operation details
     * @return The MCP operation result
     * @throws ZtatException If there is an error during the operation
     */
    @Verb(
        name = "mcp_filesystem_execute",
        description = "Execute filesystem operations through MCP. " +
                     "Requires 'operation' parameter (e.g., read, write, list). " +
                     "Additional parameters depend on the operation.",
        returnType = JsonNode.class,
        returnName = "filesystem_result",
        isAiCallable = true,
        requiresTokenManagement = true,
        paramDescriptions = {
            "operation: The filesystem operation to execute",
            "path: File or directory path - optional depending on operation",
            "content: Content for write operations - optional"
        }
    )
    public JsonNode mcpFilesystemExecute(TokenDTO token, AgentExecutionContextDTO contextDTO) 
            throws ZtatException {
        try {
            String operation = contextDTO.getExecutionArgumentScoped("operation", String.class)
                .orElseThrow(() -> new IllegalArgumentException("operation parameter is required"));
            
            log.info("Executing MCP filesystem operation: {}", operation);
            
            // Build request body with all parameters
            ObjectNode requestBody = JsonUtil.MAPPER.createObjectNode();
            requestBody.put("operation", operation);
            
            // Add optional parameters
            contextDTO.getExecutionArgumentScoped("path", String.class)
                .ifPresent(path -> requestBody.put("path", path));
            contextDTO.getExecutionArgumentScoped("content", String.class)
                .ifPresent(content -> requestBody.put("content", content));
            
            // Call the integration-proxy MCP filesystem endpoint
            String response = zeroTrustClientService.callPostOnApi(token, 
                "/api/v1/mcp-integrations/filesystem/execute", requestBody);
            
            if (response == null) {
                throw new RuntimeException("No response from MCP filesystem endpoint");
            }
            
            JsonNode responseNode = JsonUtil.MAPPER.readTree(response);
            log.info("Successfully executed MCP filesystem operation");
            return responseNode;
            
        } catch (Exception e) {
            log.error("Failed to execute MCP filesystem operation", e);
            throw new RuntimeException("Failed to execute MCP filesystem operation: " + e.getMessage(), e);
        }
    }

    /**
     * Execute PostgreSQL operations through MCP.
     *
     * @param token The zero trust token
     * @param contextDTO The execution context containing query details
     * @return The MCP operation result
     * @throws ZtatException If there is an error during the operation
     */
    @Verb(
        name = "mcp_postgresql_execute",
        description = "Execute PostgreSQL operations through MCP. " +
                     "Requires 'query' parameter. Optional: 'database', 'params'.",
        returnType = JsonNode.class,
        returnName = "postgresql_result",
        isAiCallable = true,
        requiresTokenManagement = true,
        paramDescriptions = {
            "query: The SQL query to execute",
            "database: Target database name - optional",
            "params: Query parameters - optional"
        }
    )
    public JsonNode mcpPostgresqlExecute(TokenDTO token, AgentExecutionContextDTO contextDTO) 
            throws ZtatException {
        try {
            String query = contextDTO.getExecutionArgumentScoped("query", String.class)
                .orElseThrow(() -> new IllegalArgumentException("query parameter is required"));
            
            log.info("Executing MCP PostgreSQL query");
            
            // Build request body
            ObjectNode requestBody = JsonUtil.MAPPER.createObjectNode();
            requestBody.put("query", query);
            
            // Add optional parameters
            contextDTO.getExecutionArgumentScoped("database", String.class)
                .ifPresent(database -> requestBody.put("database", database));
            contextDTO.getExecutionArgumentScoped("params", JsonNode.class)
                .ifPresent(params -> requestBody.set("params", params));
            
            // Call the integration-proxy MCP PostgreSQL endpoint
            String response = zeroTrustClientService.callPostOnApi(token, 
                "/api/v1/mcp-integrations/postgresql/execute", requestBody);
            
            if (response == null) {
                throw new RuntimeException("No response from MCP PostgreSQL endpoint");
            }
            
            JsonNode responseNode = JsonUtil.MAPPER.readTree(response);
            log.info("Successfully executed MCP PostgreSQL query");
            return responseNode;
            
        } catch (Exception e) {
            log.error("Failed to execute MCP PostgreSQL query", e);
            throw new RuntimeException("Failed to execute MCP PostgreSQL query: " + e.getMessage(), e);
        }
    }

    /**
     * Execute Slack operations through MCP.
     *
     * @param token The zero trust token
     * @param contextDTO The execution context containing Slack operation details
     * @return The MCP operation result
     * @throws ZtatException If there is an error during the operation
     */
    @Verb(
        name = "mcp_slack_execute",
        description = "Execute Slack operations through MCP. " +
                     "Requires 'operation' parameter (e.g., send_message, list_channels).",
        returnType = JsonNode.class,
        returnName = "slack_mcp_result",
        isAiCallable = true,
        requiresTokenManagement = true,
        paramDescriptions = {
            "operation: The Slack operation to execute",
            "channel: Slack channel - optional depending on operation",
            "message: Message text - optional depending on operation"
        }
    )
    public JsonNode mcpSlackExecute(TokenDTO token, AgentExecutionContextDTO contextDTO) 
            throws ZtatException {
        try {
            String operation = contextDTO.getExecutionArgumentScoped("operation", String.class)
                .orElseThrow(() -> new IllegalArgumentException("operation parameter is required"));
            
            log.info("Executing MCP Slack operation: {}", operation);
            
            // Build request body
            ObjectNode requestBody = JsonUtil.MAPPER.createObjectNode();
            requestBody.put("operation", operation);
            
            // Add optional parameters
            contextDTO.getExecutionArgumentScoped("channel", String.class)
                .ifPresent(channel -> requestBody.put("channel", channel));
            contextDTO.getExecutionArgumentScoped("message", String.class)
                .ifPresent(message -> requestBody.put("message", message));
            
            // Call the integration-proxy MCP Slack endpoint
            String response = zeroTrustClientService.callPostOnApi(token, 
                "/api/v1/mcp-integrations/slack/execute", requestBody);
            
            if (response == null) {
                throw new RuntimeException("No response from MCP Slack endpoint");
            }
            
            JsonNode responseNode = JsonUtil.MAPPER.readTree(response);
            log.info("Successfully executed MCP Slack operation");
            return responseNode;
            
        } catch (Exception e) {
            log.error("Failed to execute MCP Slack operation", e);
            throw new RuntimeException("Failed to execute MCP Slack operation: " + e.getMessage(), e);
        }
    }

    /**
     * Execute browser automation operations through MCP Playwright.
     *
     * @param token The zero trust token
     * @param contextDTO The execution context containing Playwright operation details
     * @return The MCP operation result
     * @throws ZtatException If there is an error during the operation
     */
    @Verb(
        name = "mcp_playwright_execute",
        description = "Execute browser automation through MCP Playwright. " +
                     "Requires 'operation' parameter (e.g., navigate, click, type). " +
                     "Additional parameters depend on the operation.",
        returnType = JsonNode.class,
        returnName = "playwright_result",
        isAiCallable = true,
        requiresTokenManagement = true,
        paramDescriptions = {
            "operation: The Playwright operation to execute",
            "url: Target URL - optional depending on operation",
            "selector: CSS selector - optional depending on operation",
            "text: Text content - optional depending on operation"
        }
    )
    public JsonNode mcpPlaywrightExecute(TokenDTO token, AgentExecutionContextDTO contextDTO) 
            throws ZtatException {
        try {
            String operation = contextDTO.getExecutionArgumentScoped("operation", String.class)
                .orElseThrow(() -> new IllegalArgumentException("operation parameter is required"));
            
            log.info("Executing MCP Playwright operation: {}", operation);
            
            // Build request body
            ObjectNode requestBody = JsonUtil.MAPPER.createObjectNode();
            requestBody.put("operation", operation);
            
            // Add optional parameters
            contextDTO.getExecutionArgumentScoped("url", String.class)
                .ifPresent(url -> requestBody.put("url", url));
            contextDTO.getExecutionArgumentScoped("selector", String.class)
                .ifPresent(selector -> requestBody.put("selector", selector));
            contextDTO.getExecutionArgumentScoped("text", String.class)
                .ifPresent(text -> requestBody.put("text", text));
            
            // Call the integration-proxy MCP Playwright endpoint
            String response = zeroTrustClientService.callPostOnApi(token, 
                "/api/v1/mcp-integrations/playwright/execute", requestBody);
            
            if (response == null) {
                throw new RuntimeException("No response from MCP Playwright endpoint");
            }
            
            JsonNode responseNode = JsonUtil.MAPPER.readTree(response);
            log.info("Successfully executed MCP Playwright operation");
            return responseNode;
            
        } catch (Exception e) {
            log.error("Failed to execute MCP Playwright operation", e);
            throw new RuntimeException("Failed to execute MCP Playwright operation: " + e.getMessage(), e);
        }
    }

    /**
     * Execute HTTP fetch operations through MCP.
     *
     * @param token The zero trust token
     * @param contextDTO The execution context containing fetch details
     * @return The MCP operation result
     * @throws ZtatException If there is an error during the operation
     */
    @Verb(
        name = "mcp_fetch_execute",
        description = "Execute HTTP fetch operations through MCP. " +
                     "Requires 'url' parameter. Optional: 'method', 'headers', 'body'.",
        returnType = JsonNode.class,
        returnName = "fetch_result",
        isAiCallable = true,
        requiresTokenManagement = true,
        paramDescriptions = {
            "url: The URL to fetch",
            "method: HTTP method (GET, POST, etc.) - optional, defaults to GET",
            "headers: Request headers as JSON object - optional",
            "body: Request body - optional"
        }
    )
    public JsonNode mcpFetchExecute(TokenDTO token, AgentExecutionContextDTO contextDTO) 
            throws ZtatException {
        try {
            String url = contextDTO.getExecutionArgumentScoped("url", String.class)
                .orElseThrow(() -> new IllegalArgumentException("url parameter is required"));
            
            log.info("Executing MCP fetch for URL: {}", url);
            
            // Build request body
            ObjectNode requestBody = JsonUtil.MAPPER.createObjectNode();
            requestBody.put("url", url);
            
            // Add optional parameters
            contextDTO.getExecutionArgumentScoped("method", String.class)
                .ifPresent(method -> requestBody.put("method", method));
            contextDTO.getExecutionArgumentScoped("headers", JsonNode.class)
                .ifPresent(headers -> requestBody.set("headers", headers));
            contextDTO.getExecutionArgumentScoped("body", String.class)
                .ifPresent(body -> requestBody.put("body", body));
            
            // Call the integration-proxy MCP fetch endpoint
            String response = zeroTrustClientService.callPostOnApi(token, 
                "/api/v1/mcp-integrations/fetch/execute", requestBody);
            
            if (response == null) {
                throw new RuntimeException("No response from MCP fetch endpoint");
            }
            
            JsonNode responseNode = JsonUtil.MAPPER.readTree(response);
            log.info("Successfully executed MCP fetch");
            return responseNode;
            
        } catch (Exception e) {
            log.error("Failed to execute MCP fetch", e);
            throw new RuntimeException("Failed to execute MCP fetch: " + e.getMessage(), e);
        }
    }
}
