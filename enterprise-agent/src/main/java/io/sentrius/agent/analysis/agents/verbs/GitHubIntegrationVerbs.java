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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Verbs for interacting with GitHub integration through the integration-proxy.
 * Provides AI agents with comprehensive GitHub API access.
 */
@Slf4j
@Service
public class GitHubIntegrationVerbs {


    @Value("${agent.open.ai.endpoint:http://sentrius-integrationproxy:8080/}")
    String integrationProxy;
    private final ZeroTrustClientService zeroTrustClientService;

    public GitHubIntegrationVerbs(ZeroTrustClientService zeroTrustClientService) {
        this.zeroTrustClientService = zeroTrustClientService;
    }

    /**
     * Launch a GitHub integration.
     *
     * @param token The zero trust token
     * @param contextDTO The execution context containing configuration
     * @return The launch result with instance details
     * @throws ZtatException If there is an error during the operation
     */
    @Verb(
        name = "github_mcp_launch",
        description = "Launch a GitHub integration instance. " +
                     "Optional: 'repositoryUrl', 'accessToken', 'config'.",
        returnType = JsonNode.class,
        returnName = "github_instance",
        isAiCallable = true,
        requiresTokenManagement = true,
        paramDescriptions = {
            "repositoryUrl: GitHub repository URL - optional",
            "accessToken: GitHub access token - optional",
            "config: Additional configuration as JSON - optional"
        }
    )
    public JsonNode githubMcpLaunch(TokenDTO token, AgentExecutionContextDTO contextDTO) 
            throws ZtatException {
        try {
            log.info("Launching GitHub integration");
            
            // Build request body
            ObjectNode requestBody = JsonUtil.MAPPER.createObjectNode();
            
            // Add optional parameters
            contextDTO.getExecutionArgumentScoped("repositoryUrl", String.class)
                .ifPresent(url -> requestBody.put("repositoryUrl", url));
            contextDTO.getExecutionArgumentScoped("accessToken", String.class)
                .ifPresent(token1 -> requestBody.put("accessToken", token1));
            contextDTO.getExecutionArgumentScoped("config", JsonNode.class)
                .ifPresent(config -> requestBody.set("config", config));
            
            // Call the integration-proxy GitHub launch endpoint
            String response = zeroTrustClientService.callPostOnApi(token, 
                "/api/v1/github/mcp/launch", requestBody);
            
            if (response == null) {
                throw new RuntimeException("No response from GitHub launch endpoint");
            }
            
            JsonNode responseNode = JsonUtil.MAPPER.readTree(response);
            log.info("Successfully launched GitHub integration");
            return responseNode;
            
        } catch (ZtatException e) {
            // Let ZtatException propagate without wrapping so VerbRegistry can handle ZTAT approval retry
            log.info("ZTAT token required for GitHub launch - propagating for approval");
            throw e;
        } catch (Exception e) {
            log.error("Failed to launch GitHub integration", e);
            throw new RuntimeException("Failed to launch GitHub integration: " + e.getMessage(), e);
        }
    }

    /**
     * Get status of a GitHub integration instance.
     *
     * @param token The zero trust token
     * @param contextDTO The execution context containing instanceId
     * @return The instance status
     * @throws ZtatException If there is an error during the operation
     */
    @Verb(
        name = "github_mcp_status",
        description = "Get status of a GitHub integration instance. " +
                     "Optional: 'instanceId'.",
        returnType = JsonNode.class,
        returnName = "github_status",
        isAiCallable = true,
        requiresTokenManagement = true,
        skipMemoryStorage = true,
        paramDescriptions = {
            "instanceId: The GitHub instance ID - optional"
        }
    )
    public JsonNode githubMcpStatus(TokenDTO token, AgentExecutionContextDTO contextDTO) 
            throws ZtatException {
        try {
            log.info("Getting GitHub integration status");
            
            // Build query parameters if instanceId is provided
            var instanceId = contextDTO.getExecutionArgumentScoped("instanceId", String.class);
            String response;
            
            if (instanceId.isPresent()) {
                response = zeroTrustClientService.callGetOnApi(token, integrationProxy,"/api/v1/github/mcp/status",
                    Map.entry("instanceId", java.util.List.of(instanceId.get())));
            } else {
                response = zeroTrustClientService.callGetOnApi(token, integrationProxy,"/api/v1/github/mcp/status",
                    Map.entry("instanceId", java.util.List.of()));
            }
            
            if (response == null) {
                throw new RuntimeException("No response from GitHub status endpoint");
            }
            
            JsonNode responseNode = JsonUtil.MAPPER.readTree(response);
            log.info("Successfully retrieved GitHub status");
            return responseNode;
            
        } catch (ZtatException e) {
            // Let ZtatException propagate without wrapping so VerbRegistry can handle ZTAT approval retry
            log.info("ZTAT token required for GitHub status check - propagating for approval");
            throw e;
        } catch (Exception e) {
            log.error("Failed to get GitHub status", e);
            throw new RuntimeException("Failed to get GitHub status: " + e.getMessage(), e);
        }
    }


    /**
     * List all GitHub integration instances.
     *
     * @param token The zero trust token
     * @param contextDTO The execution context
     * @return List of GitHub instances
     * @throws ZtatException If there is an error during the operation
     */
    @Verb(
        name = "github_mcp_list",
        description = "List all GitHub integration instances.",
        returnType = JsonNode.class,
        returnName = "github_instances",
        isAiCallable = true,
        requiresTokenManagement = true,
        skipMemoryStorage = true
    )
    public JsonNode githubMcpList(TokenDTO token, AgentExecutionContextDTO contextDTO) 
            throws ZtatException {
        try {
            log.info("Listing GitHub integration instances");
            
            // Call the integration-proxy GitHub list endpoint
            String response = zeroTrustClientService.callGetOnApi(token, integrationProxy,"/api/v1/github/mcp/list");
            
            if (response == null) {
                throw new RuntimeException("No response from GitHub list endpoint");
            }
            
            JsonNode responseNode = JsonUtil.MAPPER.readTree(response);
            log.info("Successfully listed GitHub integration instances");
            return responseNode;
            
        } catch (ZtatException e) {
            // Let ZtatException propagate without wrapping so VerbRegistry can handle ZTAT approval retry
            log.info("ZTAT token required for GitHub list - propagating for approval");
            throw e;
        } catch (Exception e) {
            log.error("Failed to list GitHub integrations", e);
            throw new RuntimeException("Failed to list GitHub integrations: " + e.getMessage(), e);
        }
    }

    /**
     * Proxy a request to a GitHub server.
     *
     * @param token The zero trust token
     * @param contextDTO The execution context containing instanceId and request
     * @return The proxy response
     * @throws ZtatException If there is an error during the operation
     */
    @Verb(
        name = "github_mcp_proxy",
        description = "Proxy a request to a GitHub server. " +
                     "Requires 'instanceId' and 'request' parameters.",
        returnType = JsonNode.class,
        returnName = "proxy_response",
        isAiCallable = true,
        requiresTokenManagement = true,
        paramDescriptions = {
            "instanceId: The GitHub instance ID",
            "request: The request as JSON"
        }
    )
    public JsonNode githubMcpProxy(TokenDTO token, AgentExecutionContextDTO contextDTO) 
            throws ZtatException {
        try {
            String instanceId = contextDTO.getExecutionArgumentScoped("instanceId", String.class)
                .orElseThrow(() -> new IllegalArgumentException("instanceId parameter is required"));
            JsonNode request = contextDTO.getExecutionArgumentScoped("request", JsonNode.class)
                .orElseThrow(() -> new IllegalArgumentException("request parameter is required"));
            
            log.info("Proxying request to GitHub instance: {}", instanceId);
            
            // Build request body
            ObjectNode requestBody = JsonUtil.MAPPER.createObjectNode();
            requestBody.put("instanceId", instanceId);
            requestBody.set("request", request);
            
            // Call the integration-proxy GitHub proxy endpoint
            String response = zeroTrustClientService.callPostOnApi(token, integrationProxy,
                "/api/v1/github/mcp/proxy", requestBody);
            
            if (response == null) {
                throw new RuntimeException("No response from GitHub proxy endpoint");
            }
            
            JsonNode responseNode = JsonUtil.MAPPER.readTree(response);
            log.info("Successfully proxied request to GitHub");
            return responseNode;
            
        } catch (ZtatException e) {
            // Let ZtatException propagate without wrapping so VerbRegistry can handle ZTAT approval retry
            log.info("ZTAT token required for GitHub proxy - propagating for approval");
            throw e;
        } catch (Exception e) {
            log.error("Failed to proxy GitHub request", e);
            throw new RuntimeException("Failed to proxy GitHub request: " + e.getMessage(), e);
        }
    }

    /**
     * Helper method to call GitHub proxy with a method and parameters
     */
    private JsonNode callGitHubMethod(TokenDTO token, String method, ObjectNode params) throws ZtatException {
        try {
            ObjectNode mcpRequest = JsonUtil.MAPPER.createObjectNode();
            mcpRequest.put("jsonrpc", "2.0");
            mcpRequest.put("method", method);
            mcpRequest.set("params", params);
            mcpRequest.put("id", System.currentTimeMillis());

            log.info("Calling GitHub method: {} against {}/api/v1/github/mcp/proxy", method, integrationProxy);
            
            String response = zeroTrustClientService.callPostOnApi(token, integrationProxy,"/api/v1/github/mcp/proxy", mcpRequest);
            
            if (response == null) {
                throw new RuntimeException("No response from GitHub API");
            }
            
            JsonNode responseNode = JsonUtil.MAPPER.readTree(response);
            if (responseNode.has("error")) {
                throw new RuntimeException("GitHub API error: " + responseNode.get("error").get("message").asText());
            }
            
            return responseNode.has("result") ? responseNode.get("result") : responseNode;
        } catch (ZtatException e) {
            // Let ZtatException propagate without wrapping so VerbRegistry can handle ZTAT approval retry
            log.info("ZTAT token required for GitHub method: {} - propagating for approval", method);
            throw e;
        } catch (Exception e) {
            log.error("Failed to call GitHub method: {}", method, e);
            throw new RuntimeException("Failed to call GitHub method: " + method + " - " + e.getMessage(), e);
        }
    }

    // Repository Verbs

    @Verb(
        name = "github_get_repository",
        description = "Get details of a specific GitHub repository. Requires 'owner' and 'repo' parameters.",
        returnType = JsonNode.class,
        returnName = "repository",
        isAiCallable = true,
        requiresTokenManagement = true,
        paramDescriptions = {"owner: Repository owner", "repo: Repository name"}
    )
    public JsonNode githubGetRepository(TokenDTO token, AgentExecutionContextDTO contextDTO) throws ZtatException {
        String owner = contextDTO.getExecutionArgumentScoped("owner", String.class)
            .orElseThrow(() -> new IllegalArgumentException("owner parameter is required"));
        String repo = contextDTO.getExecutionArgumentScoped("repo", String.class)
            .orElseThrow(() -> new IllegalArgumentException("repo parameter is required"));
        
        ObjectNode params = JsonUtil.MAPPER.createObjectNode();
        params.put("owner", owner);
        params.put("repo", repo);
        
        return callGitHubMethod(token, "get_repository", params);
    }

    @Verb(
        name = "github_list_repositories",
        description = "List repositories for the authenticated user. Optional: 'visibility', 'sort', 'direction', 'page', 'perPage'.",
        returnType = JsonNode.class,
        returnName = "repositories",
        isAiCallable = true,
        requiresTokenManagement = true,
        skipMemoryStorage = true,
        paramDescriptions = {
            "visibility: Repository visibility (all, public, private)",
            "sort: Sort by (created, updated, pushed, full_name)",
            "direction: Sort direction (asc, desc)"
        }
    )
    public JsonNode githubListRepositories(TokenDTO token, AgentExecutionContextDTO contextDTO) throws ZtatException {
        ObjectNode params = JsonUtil.MAPPER.createObjectNode();
        contextDTO.getExecutionArgumentScoped("visibility", String.class).ifPresent(v -> params.put("visibility", v));
        contextDTO.getExecutionArgumentScoped("sort", String.class).ifPresent(v -> params.put("sort", v));
        contextDTO.getExecutionArgumentScoped("direction", String.class).ifPresent(v -> params.put("direction", v));
        contextDTO.getExecutionArgumentScoped("page", Integer.class).ifPresent(v -> params.put("page", v));
        contextDTO.getExecutionArgumentScoped("perPage", Integer.class).ifPresent(v -> params.put("perPage", v));
        
        return callGitHubMethod(token, "list_user_repositories", params);
    }

    @Verb(
        name = "github_create_repository",
        description = "Create a new repository. Requires 'name'. Optional: 'description', 'private', 'has_issues', 'has_projects', 'has_wiki'.",
        returnType = JsonNode.class,
        returnName = "repository",
        isAiCallable = false,
        requiresTokenManagement = true,
        paramDescriptions = {"name: Repository name", "description: Repository description", "private: Is repository private"}
    )
    public JsonNode githubCreateRepository(TokenDTO token, AgentExecutionContextDTO contextDTO) throws ZtatException {
        String name = contextDTO.getExecutionArgumentScoped("name", String.class)
            .orElseThrow(() -> new IllegalArgumentException("name parameter is required"));
        
        ObjectNode params = JsonUtil.MAPPER.createObjectNode();
        params.put("name", name);
        contextDTO.getExecutionArgumentScoped("description", String.class).ifPresent(v -> params.put("description", v));
        contextDTO.getExecutionArgumentScoped("private", Boolean.class).ifPresent(v -> params.put("private", v));
        contextDTO.getExecutionArgumentScoped("has_issues", Boolean.class).ifPresent(v -> params.put("has_issues", v));
        contextDTO.getExecutionArgumentScoped("has_projects", Boolean.class).ifPresent(v -> params.put("has_projects", v));
        contextDTO.getExecutionArgumentScoped("has_wiki", Boolean.class).ifPresent(v -> params.put("has_wiki", v));
        
        return callGitHubMethod(token, "create_repository", params);
    }

    // Issue Verbs

    @Verb(
        name = "github_list_issues",
        description = "List issues for a repository. Requires 'owner' and 'repo'. Optional: 'state', 'labels', 'sort', 'direction', 'page', 'perPage'.",
        returnType = JsonNode.class,
        returnName = "issues",
        isAiCallable = true,
        requiresTokenManagement = true,
        skipMemoryStorage = true,
        paramDescriptions = {"owner: Repository owner", "repo: Repository name", "state: Issue state (open, closed, all)"}
    )
    public JsonNode githubListIssues(TokenDTO token, AgentExecutionContextDTO contextDTO) throws ZtatException {
        String owner = contextDTO.getExecutionArgumentScoped("owner", String.class)
            .orElseThrow(() -> new IllegalArgumentException("owner parameter is required"));
        String repo = contextDTO.getExecutionArgumentScoped("repo", String.class)
            .orElseThrow(() -> new IllegalArgumentException("repo parameter is required"));
        
        ObjectNode params = JsonUtil.MAPPER.createObjectNode();
        params.put("owner", owner);
        params.put("repo", repo);
        contextDTO.getExecutionArgumentScoped("state", String.class).ifPresent(v -> params.put("state", v));
        contextDTO.getExecutionArgumentScoped("labels", String.class).ifPresent(v -> params.put("labels", v));
        contextDTO.getExecutionArgumentScoped("sort", String.class).ifPresent(v -> params.put("sort", v));
        contextDTO.getExecutionArgumentScoped("direction", String.class).ifPresent(v -> params.put("direction", v));
        contextDTO.getExecutionArgumentScoped("page", Integer.class).ifPresent(v -> params.put("page", v));
        contextDTO.getExecutionArgumentScoped("perPage", Integer.class).ifPresent(v -> params.put("perPage", v));
        
        return callGitHubMethod(token, "list_issues", params);
    }

    @Verb(
        name = "github_get_issue",
        description = "Get details of a specific issue. Requires 'owner', 'repo', and 'issue_number'.",
        returnType = JsonNode.class,
        returnName = "issue",
        isAiCallable = true,
        requiresTokenManagement = true,
        paramDescriptions = {"owner: Repository owner", "repo: Repository name", "issue_number: Issue number"}
    )
    public JsonNode githubGetIssue(TokenDTO token, AgentExecutionContextDTO contextDTO) throws ZtatException {
        String owner = contextDTO.getExecutionArgumentScoped("owner", String.class)
            .orElseThrow(() -> new IllegalArgumentException("owner parameter is required"));
        String repo = contextDTO.getExecutionArgumentScoped("repo", String.class)
            .orElseThrow(() -> new IllegalArgumentException("repo parameter is required"));
        Integer issueNumber = contextDTO.getExecutionArgumentScoped("issue_number", Integer.class)
            .orElseThrow(() -> new IllegalArgumentException("issue_number parameter is required"));
        
        ObjectNode params = JsonUtil.MAPPER.createObjectNode();
        params.put("owner", owner);
        params.put("repo", repo);
        params.put("issue_number", issueNumber);
        
        return callGitHubMethod(token, "get_issue", params);
    }

    @Verb(
        name = "github_create_issue",
        description = "Create a new issue. Requires 'owner', 'repo', and 'title'. Optional: 'body', 'labels', 'assignees'.",
        returnType = JsonNode.class,
        returnName = "issue",
        isAiCallable = true,
        requiresTokenManagement = true,
        paramDescriptions = {"owner: Repository owner", "repo: Repository name", "title: Issue title", "body: Issue body"}
    )
    public JsonNode githubCreateIssue(TokenDTO token, AgentExecutionContextDTO contextDTO) throws ZtatException {
        String owner = contextDTO.getExecutionArgumentScoped("owner", String.class)
            .orElseThrow(() -> new IllegalArgumentException("owner parameter is required"));
        String repo = contextDTO.getExecutionArgumentScoped("repo", String.class)
            .orElseThrow(() -> new IllegalArgumentException("repo parameter is required"));
        String title = contextDTO.getExecutionArgumentScoped("title", String.class)
            .orElseThrow(() -> new IllegalArgumentException("title parameter is required"));
        
        ObjectNode params = JsonUtil.MAPPER.createObjectNode();
        params.put("owner", owner);
        params.put("repo", repo);
        params.put("title", title);
        contextDTO.getExecutionArgumentScoped("body", String.class).ifPresent(v -> params.put("body", v));
        contextDTO.getExecutionArgumentScoped("labels", JsonNode.class).ifPresent(v -> params.set("labels", v));
        contextDTO.getExecutionArgumentScoped("assignees", JsonNode.class).ifPresent(v -> params.set("assignees", v));
        
        return callGitHubMethod(token, "create_issue", params);
    }

    @Verb(
        name = "github_create_issue_comment",
        description = "Create a comment on an issue. Requires 'owner', 'repo', 'issue_number', and 'body'.",
        returnType = JsonNode.class,
        returnName = "comment",
        isAiCallable = true,
        requiresTokenManagement = true,
        paramDescriptions = {"owner: Repository owner", "repo: Repository name", "issue_number: Issue number", "body: Comment body"}
    )
    public JsonNode githubCreateIssueComment(TokenDTO token, AgentExecutionContextDTO contextDTO) throws ZtatException {
        String owner = contextDTO.getExecutionArgumentScoped("owner", String.class)
            .orElseThrow(() -> new IllegalArgumentException("owner parameter is required"));
        String repo = contextDTO.getExecutionArgumentScoped("repo", String.class)
            .orElseThrow(() -> new IllegalArgumentException("repo parameter is required"));
        Integer issueNumber = contextDTO.getExecutionArgumentScoped("issue_number", Integer.class)
            .orElseThrow(() -> new IllegalArgumentException("issue_number parameter is required"));
        String body = contextDTO.getExecutionArgumentScoped("body", String.class)
            .orElseThrow(() -> new IllegalArgumentException("body parameter is required"));
        
        ObjectNode params = JsonUtil.MAPPER.createObjectNode();
        params.put("owner", owner);
        params.put("repo", repo);
        params.put("issue_number", issueNumber);
        params.put("body", body);
        
        return callGitHubMethod(token, "create_issue_comment", params);
    }

    // Pull Request Verbs

    @Verb(
        name = "github_list_pull_requests",
        description = "List pull requests for a repository. Requires 'owner' and 'repo'. Optional: 'state', 'head', 'base', 'sort', 'direction'.",
        returnType = JsonNode.class,
        returnName = "pull_requests",
        isAiCallable = true,
        requiresTokenManagement = true,
        skipMemoryStorage = true,
        paramDescriptions = {"owner: Repository owner", "repo: Repository name", "state: PR state (open, closed, all)"}
    )
    public JsonNode githubListPullRequests(TokenDTO token, AgentExecutionContextDTO contextDTO) throws ZtatException {
        String owner = contextDTO.getExecutionArgumentScoped("owner", String.class)
            .orElseThrow(() -> new IllegalArgumentException("owner parameter is required"));
        String repo = contextDTO.getExecutionArgumentScoped("repo", String.class)
            .orElseThrow(() -> new IllegalArgumentException("repo parameter is required"));
        
        ObjectNode params = JsonUtil.MAPPER.createObjectNode();
        params.put("owner", owner);
        params.put("repo", repo);
        contextDTO.getExecutionArgumentScoped("state", String.class).ifPresent(v -> params.put("state", v));
        contextDTO.getExecutionArgumentScoped("head", String.class).ifPresent(v -> params.put("head", v));
        contextDTO.getExecutionArgumentScoped("base", String.class).ifPresent(v -> params.put("base", v));
        contextDTO.getExecutionArgumentScoped("sort", String.class).ifPresent(v -> params.put("sort", v));
        contextDTO.getExecutionArgumentScoped("direction", String.class).ifPresent(v -> params.put("direction", v));
        
        return callGitHubMethod(token, "list_pull_requests", params);
    }

    @Verb(
        name = "github_get_pull_request",
        description = "Get details of a specific pull request. Requires 'owner', 'repo', and 'pull_number'.",
        returnType = JsonNode.class,
        returnName = "pull_request",
        isAiCallable = true,
        requiresTokenManagement = true,
        paramDescriptions = {"owner: Repository owner", "repo: Repository name", "pull_number: PR number"}
    )
    public JsonNode githubGetPullRequest(TokenDTO token, AgentExecutionContextDTO contextDTO) throws ZtatException {
        String owner = contextDTO.getExecutionArgumentScoped("owner", String.class)
            .orElseThrow(() -> new IllegalArgumentException("owner parameter is required"));
        String repo = contextDTO.getExecutionArgumentScoped("repo", String.class)
            .orElseThrow(() -> new IllegalArgumentException("repo parameter is required"));
        Integer pullNumber = contextDTO.getExecutionArgumentScoped("pull_number", Integer.class)
            .orElseThrow(() -> new IllegalArgumentException("pull_number parameter is required"));
        
        ObjectNode params = JsonUtil.MAPPER.createObjectNode();
        params.put("owner", owner);
        params.put("repo", repo);
        params.put("pull_number", pullNumber);
        
        return callGitHubMethod(token, "get_pull_request", params);
    }

    @Verb(
        name = "github_create_pull_request",
        description = "Create a new pull request. Requires 'owner', 'repo', 'title', 'head', and 'base'. Optional: 'body'.",
        returnType = JsonNode.class,
        returnName = "pull_request",
        isAiCallable = true,
        requiresTokenManagement = true,
        paramDescriptions = {
            "owner: Repository owner",
            "repo: Repository name",
            "title: PR title",
            "head: Head branch",
            "base: Base branch",
            "body: PR body/description"
        }
    )
    public JsonNode githubCreatePullRequest(TokenDTO token, AgentExecutionContextDTO contextDTO) throws ZtatException {
        String owner = contextDTO.getExecutionArgumentScoped("owner", String.class)
            .orElseThrow(() -> new IllegalArgumentException("owner parameter is required"));
        String repo = contextDTO.getExecutionArgumentScoped("repo", String.class)
            .orElseThrow(() -> new IllegalArgumentException("repo parameter is required"));
        String title = contextDTO.getExecutionArgumentScoped("title", String.class)
            .orElseThrow(() -> new IllegalArgumentException("title parameter is required"));
        String head = contextDTO.getExecutionArgumentScoped("head", String.class)
            .orElseThrow(() -> new IllegalArgumentException("head parameter is required"));
        String base = contextDTO.getExecutionArgumentScoped("base", String.class)
            .orElseThrow(() -> new IllegalArgumentException("base parameter is required"));
        
        ObjectNode params = JsonUtil.MAPPER.createObjectNode();
        params.put("owner", owner);
        params.put("repo", repo);
        params.put("title", title);
        params.put("head", head);
        params.put("base", base);
        contextDTO.getExecutionArgumentScoped("body", String.class).ifPresent(v -> params.put("body", v));
        
        return callGitHubMethod(token, "create_pull_request", params);
    }

    @Verb(
        name = "github_merge_pull_request",
        description = "Merge a pull request. Requires 'owner', 'repo', and 'pull_number'. Optional: 'commit_title', 'commit_message', 'merge_method'.",
        returnType = JsonNode.class,
        returnName = "merge_result",
        isAiCallable = false,
        requiresTokenManagement = true,
        paramDescriptions = {
            "owner: Repository owner",
            "repo: Repository name",
            "pull_number: PR number",
            "merge_method: Merge method (merge, squash, rebase)"
        }
    )
    public JsonNode githubMergePullRequest(TokenDTO token, AgentExecutionContextDTO contextDTO) throws ZtatException {
        String owner = contextDTO.getExecutionArgumentScoped("owner", String.class)
            .orElseThrow(() -> new IllegalArgumentException("owner parameter is required"));
        String repo = contextDTO.getExecutionArgumentScoped("repo", String.class)
            .orElseThrow(() -> new IllegalArgumentException("repo parameter is required"));
        Integer pullNumber = contextDTO.getExecutionArgumentScoped("pull_number", Integer.class)
            .orElseThrow(() -> new IllegalArgumentException("pull_number parameter is required"));
        
        ObjectNode params = JsonUtil.MAPPER.createObjectNode();
        params.put("owner", owner);
        params.put("repo", repo);
        params.put("pull_number", pullNumber);
        contextDTO.getExecutionArgumentScoped("commit_title", String.class).ifPresent(v -> params.put("commit_title", v));
        contextDTO.getExecutionArgumentScoped("commit_message", String.class).ifPresent(v -> params.put("commit_message", v));
        contextDTO.getExecutionArgumentScoped("merge_method", String.class).ifPresent(v -> params.put("merge_method", v));
        
        return callGitHubMethod(token, "merge_pull_request", params);
    }

    // Workflow/Actions Verbs

    @Verb(
        name = "github_list_workflow_runs",
        description = "List workflow runs for a repository. Requires 'owner' and 'repo'. Optional: 'actor', 'branch', 'event', 'status'.",
        returnType = JsonNode.class,
        returnName = "workflow_runs",
        isAiCallable = true,
        requiresTokenManagement = true,
        skipMemoryStorage = true,
        paramDescriptions = {"owner: Repository owner", "repo: Repository name", "status: Run status (queued, in_progress, completed)"}
    )
    public JsonNode githubListWorkflowRuns(TokenDTO token, AgentExecutionContextDTO contextDTO) throws ZtatException {
        String owner = contextDTO.getExecutionArgumentScoped("owner", String.class)
            .orElseThrow(() -> new IllegalArgumentException("owner parameter is required"));
        String repo = contextDTO.getExecutionArgumentScoped("repo", String.class)
            .orElseThrow(() -> new IllegalArgumentException("repo parameter is required"));
        
        ObjectNode params = JsonUtil.MAPPER.createObjectNode();
        params.put("owner", owner);
        params.put("repo", repo);
        contextDTO.getExecutionArgumentScoped("actor", String.class).ifPresent(v -> params.put("actor", v));
        contextDTO.getExecutionArgumentScoped("branch", String.class).ifPresent(v -> params.put("branch", v));
        contextDTO.getExecutionArgumentScoped("event", String.class).ifPresent(v -> params.put("event", v));
        contextDTO.getExecutionArgumentScoped("status", String.class).ifPresent(v -> params.put("status", v));
        
        return callGitHubMethod(token, "list_workflow_runs", params);
    }

    @Verb(
        name = "github_get_workflow_run",
        description = "Get details of a specific workflow run. Requires 'owner', 'repo', and 'run_id'.",
        returnType = JsonNode.class,
        returnName = "workflow_run",
        isAiCallable = true,
        requiresTokenManagement = true,
        paramDescriptions = {"owner: Repository owner", "repo: Repository name", "run_id: Workflow run ID"}
    )
    public JsonNode githubGetWorkflowRun(TokenDTO token, AgentExecutionContextDTO contextDTO) throws ZtatException {
        String owner = contextDTO.getExecutionArgumentScoped("owner", String.class)
            .orElseThrow(() -> new IllegalArgumentException("owner parameter is required"));
        String repo = contextDTO.getExecutionArgumentScoped("repo", String.class)
            .orElseThrow(() -> new IllegalArgumentException("repo parameter is required"));
        Long runId = contextDTO.getExecutionArgumentScoped("run_id", Long.class)
            .orElseThrow(() -> new IllegalArgumentException("run_id parameter is required"));
        
        ObjectNode params = JsonUtil.MAPPER.createObjectNode();
        params.put("owner", owner);
        params.put("repo", repo);
        params.put("run_id", runId);
        
        return callGitHubMethod(token, "get_workflow_run", params);
    }

    @Verb(
        name = "github_list_commits",
        description = "List commits for a repository. Requires 'owner' and 'repo'. Optional: 'sha', 'path', 'author', 'page', 'perPage'.",
        returnType = JsonNode.class,
        returnName = "commits",
        isAiCallable = true,
        requiresTokenManagement = true,
        skipMemoryStorage = true,
        paramDescriptions = {"owner: Repository owner", "repo: Repository name", "sha: Branch/commit SHA", "author: Filter by author"}
    )
    public JsonNode githubListCommits(TokenDTO token, AgentExecutionContextDTO contextDTO) throws ZtatException {
        String owner = contextDTO.getExecutionArgumentScoped("owner", String.class)
            .orElseThrow(() -> new IllegalArgumentException("owner parameter is required"));
        String repo = contextDTO.getExecutionArgumentScoped("repo", String.class)
            .orElseThrow(() -> new IllegalArgumentException("repo parameter is required"));
        
        ObjectNode params = JsonUtil.MAPPER.createObjectNode();
        params.put("owner", owner);
        params.put("repo", repo);
        contextDTO.getExecutionArgumentScoped("sha", String.class).ifPresent(v -> params.put("sha", v));
        contextDTO.getExecutionArgumentScoped("path", String.class).ifPresent(v -> params.put("path", v));
        contextDTO.getExecutionArgumentScoped("author", String.class).ifPresent(v -> params.put("author", v));
        contextDTO.getExecutionArgumentScoped("page", Integer.class).ifPresent(v -> params.put("page", v));
        contextDTO.getExecutionArgumentScoped("perPage", Integer.class).ifPresent(v -> params.put("perPage", v));
        
        return callGitHubMethod(token, "list_commits", params);
    }

    @Verb(
        name = "github_search_code",
        description = "Search code across GitHub. Requires 'query'. Optional: 'sort', 'order', 'page', 'perPage'.",
        returnType = JsonNode.class,
        returnName = "search_results",
        isAiCallable = true,
        requiresTokenManagement = true,
        skipMemoryStorage = true,
        paramDescriptions = {"query: Search query", "sort: Sort by (indexed)", "order: Sort order (asc, desc)"}
    )
    public JsonNode githubSearchCode(TokenDTO token, AgentExecutionContextDTO contextDTO) throws ZtatException {
        String query = contextDTO.getExecutionArgumentScoped("query", String.class)
            .orElseThrow(() -> new IllegalArgumentException("query parameter is required"));
        
        ObjectNode params = JsonUtil.MAPPER.createObjectNode();
        params.put("query", query);
        contextDTO.getExecutionArgumentScoped("sort", String.class).ifPresent(v -> params.put("sort", v));
        contextDTO.getExecutionArgumentScoped("order", String.class).ifPresent(v -> params.put("order", v));
        contextDTO.getExecutionArgumentScoped("page", Integer.class).ifPresent(v -> params.put("page", v));
        contextDTO.getExecutionArgumentScoped("perPage", Integer.class).ifPresent(v -> params.put("perPage", v));
        
        return callGitHubMethod(token, "search_code", params);
    }
}
