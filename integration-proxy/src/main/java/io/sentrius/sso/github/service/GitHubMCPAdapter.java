package io.sentrius.sso.github.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.sentrius.sso.core.model.security.IntegrationSecurityToken;
import io.sentrius.sso.core.services.security.IntegrationSecurityTokenService;
import io.sentrius.sso.core.utils.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Service that adapts GitHub API calls to MCP protocol format
 * Handles MCP request parsing and response formatting
 */
@Slf4j
@Service
public class GitHubMCPAdapter {

    private final GitHubApiService githubApiService;
    private final IntegrationSecurityTokenService tokenService;
    private final ObjectMapper objectMapper;

    public GitHubMCPAdapter(GitHubApiService githubApiService,
                             IntegrationSecurityTokenService tokenService,
                             ObjectMapper objectMapper) {
        this.githubApiService = githubApiService;
        this.tokenService = tokenService;
        this.objectMapper = objectMapper;
    }

    /**
     * Process an MCP request and return an MCP-formatted response
     */
    public ObjectNode processRequest(String tokenId, JsonNode request) {
        try {
            // Validate token
            Optional<IntegrationSecurityToken> tokenOpt = tokenService.findById(Long.parseLong(tokenId));
            if (tokenOpt.isEmpty()) {
                return createErrorResponse(request.get("id"), -32602, "Invalid token ID", null);
            }

            IntegrationSecurityToken token = tokenOpt.get();
            if (!"github".equals(token.getConnectionType())) {
                return createErrorResponse(request.get("id"), -32602, "Token is not a GitHub token", null);
            }

            // Extract GitHub token
            JsonNode connectionInfo = JsonUtil.MAPPER.readTree(token.getConnectionInfo());
            String githubToken = connectionInfo.get("apiToken").asText();

            // Get method and params
            String method = request.get("method").asText();
            JsonNode params = request.has("params") ? request.get("params") : objectMapper.createObjectNode();

            // Route to appropriate handler
            JsonNode result = routeMethod(method, params, githubToken);
            
            return createSuccessResponse(request.get("id"), result);

        } catch (Exception e) {
            log.error("Error processing MCP request", e);
            return createErrorResponse(
                request.has("id") ? request.get("id") : null,
                -32603,
                "Internal error: " + e.getMessage(),
                null
            );
        }
    }

    /**
     * Route MCP method to appropriate GitHub API handler
     */
    private JsonNode routeMethod(String method, JsonNode params, String githubToken) {
        try {
            switch (method) {
                // Tool listing
                case "tools/list":
                    return listTools();

                // File operations
                case "get_file_contents":
                    return callGetFileContents(params, githubToken);

                // Repository operations
                case "search_repositories":
                    return callSearchRepositories(params, githubToken);
                case "list_branches":
                    return callListBranches(params, githubToken);
                case "list_releases":
                    return callListReleases(params, githubToken);
                case "get_latest_release":
                    return callGetLatestRelease(params, githubToken);

                // Commit operations
                case "list_commits":
                    return callListCommits(params, githubToken);
                case "get_commit":
                    return callGetCommit(params, githubToken);

                // Issue operations
                case "list_issues":
                    return callListIssues(params, githubToken);
                case "get_issue":
                case "issue_read":
                    return callIssueRead(params, githubToken);
                case "search_issues":
                    return callSearchIssues(params, githubToken);

                // Pull request operations
                case "list_pull_requests":
                    return callListPullRequests(params, githubToken);
                case "get_pull_request":
                case "pull_request_read":
                    return callPullRequestRead(params, githubToken);
                case "create_pull_request":
                    return callCreatePullRequest(params, githubToken);

                // Search operations
                case "search_code":
                    return callSearchCode(params, githubToken);
                case "search_users":
                    return callSearchUsers(params, githubToken);

                // User operations
                case "get_me":
                    return callGetMe(params, githubToken);

                // Tool call wrapper
                case "tools/call":
                    String toolName = params.get("name").asText();
                    JsonNode arguments = params.has("arguments") ? params.get("arguments") : objectMapper.createObjectNode();
                    return routeMethod(toolName, arguments, githubToken);

                default:
                    throw new UnsupportedOperationException("Method not supported: " + method);
            }
        } catch (Exception e) {
            log.error("Error routing method: {}", method, e);
            throw new RuntimeException("Failed to execute method: " + method, e);
        }
    }

    /**
     * List available GitHub tools in MCP format
     */
    private JsonNode listTools() {
        ObjectNode response = objectMapper.createObjectNode();
        ArrayNode tools = objectMapper.createArrayNode();

        // Add all supported tools with descriptions
        tools.add(createToolDefinition("get_file_contents", 
            "Get the contents of a file or directory from a repository",
            new String[]{"owner", "repo", "path"}));
        
        tools.add(createToolDefinition("search_repositories", 
            "Search for repositories on GitHub",
            new String[]{"query"}));
        
        tools.add(createToolDefinition("list_commits", 
            "List commits for a repository",
            new String[]{"owner", "repo"}));
        
        tools.add(createToolDefinition("get_commit", 
            "Get details of a specific commit",
            new String[]{"owner", "repo", "sha"}));
        
        tools.add(createToolDefinition("list_issues", 
            "List issues for a repository",
            new String[]{"owner", "repo"}));
        
        tools.add(createToolDefinition("issue_read", 
            "Read issue details, comments, and labels",
            new String[]{"owner", "repo", "issue_number"}));
        
        tools.add(createToolDefinition("search_issues", 
            "Search for issues and pull requests",
            new String[]{"query"}));
        
        tools.add(createToolDefinition("list_pull_requests", 
            "List pull requests for a repository",
            new String[]{"owner", "repo"}));
        
        tools.add(createToolDefinition("pull_request_read", 
            "Read pull request details, files, and reviews",
            new String[]{"owner", "repo", "pull_number"}));
        
        tools.add(createToolDefinition("create_pull_request", 
            "Create a new pull request",
            new String[]{"owner", "repo", "title", "head", "base"}));
        
        tools.add(createToolDefinition("search_code", 
            "Search code across GitHub",
            new String[]{"query"}));
        
        tools.add(createToolDefinition("search_users", 
            "Search for users on GitHub",
            new String[]{"query"}));
        
        tools.add(createToolDefinition("get_me", 
            "Get information about the authenticated user",
            new String[]{}));
        
        tools.add(createToolDefinition("list_branches", 
            "List branches for a repository",
            new String[]{"owner", "repo"}));
        
        tools.add(createToolDefinition("list_releases", 
            "List releases for a repository",
            new String[]{"owner", "repo"}));
        
        tools.add(createToolDefinition("get_latest_release", 
            "Get the latest release for a repository",
            new String[]{"owner", "repo"}));

        response.set("tools", tools);
        return response;
    }

    /**
     * Create a tool definition for MCP protocol
     */
    private ObjectNode createToolDefinition(String name, String description, String[] requiredParams) {
        ObjectNode tool = objectMapper.createObjectNode();
        tool.put("name", name);
        tool.put("description", description);
        
        ObjectNode inputSchema = objectMapper.createObjectNode();
        inputSchema.put("type", "object");
        
        ObjectNode properties = objectMapper.createObjectNode();
        ArrayNode required = objectMapper.createArrayNode();
        
        for (String param : requiredParams) {
            ObjectNode paramDef = objectMapper.createObjectNode();
            paramDef.put("type", "string");
            properties.set(param, paramDef);
            required.add(param);
        }
        
        inputSchema.set("properties", properties);
        inputSchema.set("required", required);
        tool.set("inputSchema", inputSchema);
        
        return tool;
    }

    // Tool implementation methods

    private JsonNode callGetFileContents(JsonNode params, String githubToken) {
        String owner = params.get("owner").asText();
        String repo = params.get("repo").asText();
        String path = params.get("path").asText();
        String ref = params.has("ref") ? params.get("ref").asText() : null;

        return githubApiService.getFileContents(githubToken, owner, repo, path, ref);
    }

    private JsonNode callSearchRepositories(JsonNode params, String githubToken) {
        String query = params.get("query").asText();
        String sort = params.has("sort") ? params.get("sort").asText() : null;
        String order = params.has("order") ? params.get("order").asText() : null;
        Integer page = params.has("page") ? params.get("page").asInt() : null;
        Integer perPage = params.has("perPage") ? params.get("perPage").asInt() : null;

        return githubApiService.searchRepositories(githubToken, query, sort, order, page, perPage);
    }

    private JsonNode callListCommits(JsonNode params, String githubToken) {
        String owner = params.get("owner").asText();
        String repo = params.get("repo").asText();
        String sha = params.has("sha") ? params.get("sha").asText() : null;
        String path = params.has("path") ? params.get("path").asText() : null;
        String author = params.has("author") ? params.get("author").asText() : null;
        Integer page = params.has("page") ? params.get("page").asInt() : null;
        Integer perPage = params.has("perPage") ? params.get("perPage").asInt() : null;

        return githubApiService.listCommits(githubToken, owner, repo, sha, path, author, page, perPage);
    }

    private JsonNode callGetCommit(JsonNode params, String githubToken) {
        String owner = params.get("owner").asText();
        String repo = params.get("repo").asText();
        String sha = params.get("sha").asText();

        return githubApiService.getCommit(githubToken, owner, repo, sha);
    }

    private JsonNode callListIssues(JsonNode params, String githubToken) {
        String owner = params.get("owner").asText();
        String repo = params.get("repo").asText();
        String state = params.has("state") ? params.get("state").asText() : null;
        String labels = params.has("labels") ? params.get("labels").asText() : null;
        String sort = params.has("sort") ? params.get("sort").asText() : null;
        String direction = params.has("direction") ? params.get("direction").asText() : null;
        Integer page = params.has("page") ? params.get("page").asInt() : null;
        Integer perPage = params.has("perPage") ? params.get("perPage").asInt() : null;

        return githubApiService.listIssues(githubToken, owner, repo, state, labels, sort, direction, page, perPage);
    }

    private JsonNode callIssueRead(JsonNode params, String githubToken) {
        String owner = params.get("owner").asText();
        String repo = params.get("repo").asText();
        int issueNumber = params.get("issue_number").asInt();

        return githubApiService.getIssue(githubToken, owner, repo, issueNumber);
    }

    private JsonNode callSearchIssues(JsonNode params, String githubToken) {
        String query = params.get("query").asText();
        String sort = params.has("sort") ? params.get("sort").asText() : null;
        String order = params.has("order") ? params.get("order").asText() : null;
        Integer page = params.has("page") ? params.get("page").asInt() : null;
        Integer perPage = params.has("perPage") ? params.get("perPage").asInt() : null;

        return githubApiService.searchIssues(githubToken, query, sort, order, page, perPage);
    }

    private JsonNode callListPullRequests(JsonNode params, String githubToken) {
        String owner = params.get("owner").asText();
        String repo = params.get("repo").asText();
        String state = params.has("state") ? params.get("state").asText() : null;
        String head = params.has("head") ? params.get("head").asText() : null;
        String base = params.has("base") ? params.get("base").asText() : null;
        String sort = params.has("sort") ? params.get("sort").asText() : null;
        String direction = params.has("direction") ? params.get("direction").asText() : null;
        Integer page = params.has("page") ? params.get("page").asInt() : null;
        Integer perPage = params.has("perPage") ? params.get("perPage").asInt() : null;

        return githubApiService.listPullRequests(githubToken, owner, repo, state, head, base, sort, direction, page, perPage);
    }

    private JsonNode callPullRequestRead(JsonNode params, String githubToken) {
        String owner = params.get("owner").asText();
        String repo = params.get("repo").asText();
        int pullNumber = params.get("pull_number").asInt();

        return githubApiService.getPullRequest(githubToken, owner, repo, pullNumber);
    }

    private JsonNode callCreatePullRequest(JsonNode params, String githubToken) {
        String owner = params.get("owner").asText();
        String repo = params.get("repo").asText();
        String title = params.get("title").asText();
        String head = params.get("head").asText();
        String base = params.get("base").asText();
        String body = params.has("body") ? params.get("body").asText() : null;

        return githubApiService.createPullRequest(githubToken, owner, repo, title, head, base, body);
    }

    private JsonNode callSearchCode(JsonNode params, String githubToken) {
        String query = params.get("query").asText();
        String sort = params.has("sort") ? params.get("sort").asText() : null;
        String order = params.has("order") ? params.get("order").asText() : null;
        Integer page = params.has("page") ? params.get("page").asInt() : null;
        Integer perPage = params.has("perPage") ? params.get("perPage").asInt() : null;

        return githubApiService.searchCode(githubToken, query, sort, order, page, perPage);
    }

    private JsonNode callSearchUsers(JsonNode params, String githubToken) {
        String query = params.get("query").asText();
        String sort = params.has("sort") ? params.get("sort").asText() : null;
        String order = params.has("order") ? params.get("order").asText() : null;
        Integer page = params.has("page") ? params.get("page").asInt() : null;
        Integer perPage = params.has("perPage") ? params.get("perPage").asInt() : null;

        return githubApiService.searchUsers(githubToken, query, sort, order, page, perPage);
    }

    private JsonNode callGetMe(JsonNode params, String githubToken) {
        return githubApiService.getAuthenticatedUser(githubToken);
    }

    private JsonNode callListBranches(JsonNode params, String githubToken) {
        String owner = params.get("owner").asText();
        String repo = params.get("repo").asText();
        Integer page = params.has("page") ? params.get("page").asInt() : null;
        Integer perPage = params.has("perPage") ? params.get("perPage").asInt() : null;

        return githubApiService.listBranches(githubToken, owner, repo, page, perPage);
    }

    private JsonNode callListReleases(JsonNode params, String githubToken) {
        String owner = params.get("owner").asText();
        String repo = params.get("repo").asText();
        Integer page = params.has("page") ? params.get("page").asInt() : null;
        Integer perPage = params.has("perPage") ? params.get("perPage").asInt() : null;

        return githubApiService.listReleases(githubToken, owner, repo, page, perPage);
    }

    private JsonNode callGetLatestRelease(JsonNode params, String githubToken) {
        String owner = params.get("owner").asText();
        String repo = params.get("repo").asText();

        return githubApiService.getLatestRelease(githubToken, owner, repo);
    }

    /**
     * Create an MCP success response
     */
    private ObjectNode createSuccessResponse(JsonNode id, JsonNode result) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        if (id != null) {
            response.set("id", id);
        }
        response.set("result", result);
        return response;
    }

    /**
     * Create an MCP error response
     */
    private ObjectNode createErrorResponse(JsonNode id, int code, String message, JsonNode data) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        if (id != null) {
            response.set("id", id);
        }
        
        ObjectNode error = objectMapper.createObjectNode();
        error.put("code", code);
        error.put("message", message);
        if (data != null) {
            error.set("data", data);
        }
        
        response.set("error", error);
        return response;
    }
}
