package io.sentrius.sso.coding.service;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.*;
import io.sentrius.sso.integration.service.IntegrationServerManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Service for managing Coding MCP server containers
 * Extends IntegrationServerManager for reusable Kubernetes management
 * 
 * The Coding MCP Server provides AI-powered code generation and PR submission capabilities
 * through the Model Context Protocol, integrating with GitHub and JIRA.
 */
@Slf4j
@Service
public class CodingMCPServerService extends IntegrationServerManager {

    @Value("${sentrius.coding.mcp.image:coding-mcp-server:latest}")
    private String codingMcpImage;

    @Value("${sentrius.coding.mcp.registry:}")
    private String registry;

    @Value("${sentrius.coding.mcp.github.token.id:}")
    private String defaultGitHubTokenId;

    @Value("${sentrius.coding.mcp.llm.proxy.url:http://localhost:8084}")
    private String llmProxyUrl;

    @Value("${sentrius.coding.mcp.integration.proxy.url:http://localhost:8080}")
    private String integrationProxyUrl;

    private static final int MCP_SERVER_PORT = 3000;

    public CodingMCPServerService() throws IOException {
        super();
    }

    /**
     * Launch a Coding MCP server pod
     * 
     * @param instanceId Unique identifier for this MCP server instance
     * @param gitHubTokenId GitHub integration token ID for repository operations
     * @param config Additional configuration for the coding agent
     * @return The created pod
     */
    public V1Pod launchCodingMCPServer(String instanceId, String gitHubTokenId, Map<String, String> config) throws Exception {
        log.info("Launching Coding MCP server with instance ID: {}", instanceId);

        String podName = "coding-mcp-" + instanceId;
        String tokenId = gitHubTokenId != null ? gitHubTokenId : defaultGitHubTokenId;

        // Build the full image name
        String fullImage = registry != null && !registry.isEmpty() && !"local".equalsIgnoreCase(registry)
            ? registry + "/" + codingMcpImage
            : codingMcpImage;

        log.info("Creating Coding MCP server pod with image: {}", fullImage);

        // Create labels
        Map<String, String> labels = Map.of(
            "app", "coding-mcp-server",
            "integration-type", "coding",
            "instance-id", instanceId
        );

        // Create environment variables
        List<V1EnvVar> envVars = List.of(
            new V1EnvVar()
                .name("GITHUB_TOKEN_ID")
                .value(tokenId),
            new V1EnvVar()
                .name("LLM_PROXY_URL")
                .value(llmProxyUrl),
            new V1EnvVar()
                .name("INTEGRATION_PROXY_URL")
                .value(integrationProxyUrl),
            new V1EnvVar()
                .name("INSTANCE_ID")
                .value(instanceId),
            new V1EnvVar()
                .name("MCP_SERVER_MODE")
                .value("http")
        );

        // Launch pod using base class
        return launchPod(podName, labels, fullImage, envVars, MCP_SERVER_PORT);
    }

    /**
     * Launch a default Coding MCP server (singleton pattern)
     */
    public V1Pod launchCodingMCPServer() throws Exception {
        return launchCodingMCPServer("default", null, Map.of());
    }

    /**
     * Delete Coding MCP server pod by instance ID
     */
    public void deleteCodingMCPServer(String instanceId) throws ApiException {
        String podName = "coding-mcp-" + instanceId;
        String serviceName = "coding-mcp-svc-" + instanceId;
        deletePod(podName, serviceName);
    }

    /**
     * Get status of Coding MCP server by instance ID
     */
    public String getStatus(String instanceId) throws ApiException {
        String podName = "coding-mcp-" + instanceId;
        return getPodStatus(podName);
    }

    /**
     * List all Coding MCP server pods
     */
    public List<V1Pod> listCodingMCPServers() throws ApiException {
        return listPods(getLabelSelector());
    }

    /**
     * Get the service URL for a Coding MCP server
     */
    public String getServiceUrl(String instanceId) {
        String serviceName = "coding-mcp-svc-" + instanceId;
        return buildServiceUrl(serviceName, MCP_SERVER_PORT);
    }

    @Override
    protected String getContainerName() {
        return "coding-mcp-server";
    }

    @Override
    protected String getLabelSelector() {
        return "app=coding-mcp-server";
    }
}
