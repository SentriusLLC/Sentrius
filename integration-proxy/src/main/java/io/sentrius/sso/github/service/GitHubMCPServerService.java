package io.sentrius.sso.github.service;

import com.fasterxml.jackson.databind.JsonNode;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.*;
import io.sentrius.sso.core.model.security.IntegrationSecurityToken;
import io.sentrius.sso.core.services.security.IntegrationSecurityTokenService;
import io.sentrius.sso.core.utils.JsonUtil;
import io.sentrius.sso.integration.service.IntegrationServerManager;
import io.sentrius.sso.k8s.service.KubernetesService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service for managing GitHub MCP server containers
 * Extends IntegrationServerManager for reusable Kubernetes management
 */
@Slf4j
@Service
public class GitHubMCPServerService extends IntegrationServerManager {

    private final IntegrationSecurityTokenService integrationSecurityTokenService;

    @Value("${sentrius.github.mcp.image:github-mcp-server:latest}")
    private String githubMcpImage;

    @Value("${sentrius.github.mcp.registry:ghcr.io/github}")
    private String registry;

    private static final int MCP_SERVER_PORT = 3000;

    public GitHubMCPServerService(IntegrationSecurityTokenService integrationSecurityTokenService, KubernetesService kubernetesService) throws IOException {
        super(kubernetesService);
        this.integrationSecurityTokenService = integrationSecurityTokenService;
    }

    /**
     * Launch a GitHub MCP server pod for a specific GitHub token
     */
    public V1Pod launchGitHubMCPServer(String tokenId) throws Exception {
        log.info("Launching GitHub MCP server for token ID: {}", tokenId);

        // Retrieve the GitHub token from integration security tokens
        Optional<IntegrationSecurityToken> tokenOpt = integrationSecurityTokenService.findById(Long.parseLong(tokenId));
        if (tokenOpt.isEmpty()) {
            throw new IllegalArgumentException("GitHub integration token not found: " + tokenId);
        }

        IntegrationSecurityToken token = tokenOpt.get();
        if (!"github".equals(token.getConnectionType())) {
            throw new IllegalArgumentException("Token is not a GitHub integration token");
        }

        String githubToken = token.getConnectionInfo();
        JsonNode connectionInfo = JsonUtil.MAPPER.readTree(githubToken);
        githubToken = connectionInfo.get("apiToken").asText();
        String podName = "github-mcp-" + tokenId;

        // Build the full image name
        String fullImage = registry != null && !registry.isEmpty() && !"local".equalsIgnoreCase(registry)
            ? registry + "/" + githubMcpImage
            : githubMcpImage;

        log.info("Creating GitHub MCP server pod with image: {}", fullImage);

        // Create labels
        Map<String, String> labels = Map.of(
            "app", "github-mcp-server",
            "integration-type", "github",
            "token-id", tokenId
        );

        // Create environment variables
        List<V1EnvVar> envVars = List.of(
            new V1EnvVar()
                .name("GITHUB_PERSONAL_ACCESS_TOKEN")
                .value(githubToken),
            new V1EnvVar()
                .name("GITHUB_TOOLSETS")
                .value("all")
        );

        // Launch pod using base class
        return launchPod(podName, labels, fullImage, envVars, MCP_SERVER_PORT);
    }

    /**
     * Delete GitHub MCP server pod by token ID
     */
    public void deleteGitHubMCPServer(String tokenId) throws ApiException {
        String podName = "github-mcp-" + tokenId;
        String serviceName = "github-mcp-" + tokenId;
        deletePod(podName, serviceName);
    }

    /**
     * Get status of GitHub MCP server by token ID
     */
    public String getStatus(String tokenId) throws ApiException {
        String podName = "github-mcp-" + tokenId;
        return getPodStatus(podName);
    }

    /**
     * List all GitHub MCP server pods
     */
    public List<V1Pod> listGitHubMCPServers() throws ApiException {
        return listPods(getLabelSelector());
    }

    /**
     * Get the service URL for a GitHub MCP server
     */
    public String getServiceUrl(String tokenId) {
        String serviceName = "github-mcp-" + tokenId;
        return buildServiceUrl(serviceName, MCP_SERVER_PORT);
    }

    @Override
    protected String getContainerName() {
        return "github-mcp-server";
    }

    @Override
    protected String getLabelSelector() {
        return "app=github-mcp-server";
    }
}
