package io.sentrius.sso.github.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.sentrius.sso.core.model.security.IntegrationSecurityToken;
import io.sentrius.sso.core.services.security.IntegrationSecurityTokenService;
import io.sentrius.sso.core.utils.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
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
                case "get_repository":
                    return callGetRepository(params, githubToken);
                case "list_user_repositories":
                    return callListUserRepositories(params, githubToken);
                case "list_user_repositories_by_username":
                    return callListUserRepositoriesByUsername(params, githubToken);
                case "list_organization_repositories":
                    return callListOrganizationRepositories(params, githubToken);
                case "create_repository":
                    return callCreateRepository(params, githubToken);
                case "update_repository":
                    return callUpdateRepository(params, githubToken);
                case "delete_repository":
                    callDeleteRepository(params, githubToken);
                    return objectMapper.createObjectNode().put("success", true);
                case "list_branches":
                    return callListBranches(params, githubToken);
                case "list_releases":
                    return callListReleases(params, githubToken);
                case "get_latest_release":
                    return callGetLatestRelease(params, githubToken);
                case "get_release_by_tag":
                    return callGetReleaseByTag(params, githubToken);
                case "list_collaborators":
                    return callListCollaborators(params, githubToken);
                case "add_collaborator":
                    callAddCollaborator(params, githubToken);
                    return objectMapper.createObjectNode().put("success", true);
                case "remove_collaborator":
                    callRemoveCollaborator(params, githubToken);
                    return objectMapper.createObjectNode().put("success", true);
                case "list_webhooks":
                    return callListWebhooks(params, githubToken);
                case "create_webhook":
                    return callCreateWebhook(params, githubToken);
                case "delete_webhook":
                    callDeleteWebhook(params, githubToken);
                    return objectMapper.createObjectNode().put("success", true);

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
                case "create_issue":
                    return callCreateIssue(params, githubToken);
                case "update_issue":
                    return callUpdateIssue(params, githubToken);
                case "list_issue_comments":
                    return callListIssueComments(params, githubToken);
                case "create_issue_comment":
                    return callCreateIssueComment(params, githubToken);
                case "list_issue_labels":
                    return callListIssueLabels(params, githubToken);
                case "add_issue_labels":
                    return callAddIssueLabels(params, githubToken);
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
                case "update_pull_request":
                    return callUpdatePullRequest(params, githubToken);
                case "merge_pull_request":
                    return callMergePullRequest(params, githubToken);
                case "list_pull_request_files":
                    return callListPullRequestFiles(params, githubToken);
                case "list_pull_request_reviews":
                    return callListPullRequestReviews(params, githubToken);
                case "create_pull_request_review":
                    return callCreatePullRequestReview(params, githubToken);
                case "list_pull_request_review_comments":
                    return callListPullRequestReviewComments(params, githubToken);

                // Search operations
                case "search_code":
                    return callSearchCode(params, githubToken);
                case "search_users":
                    return callSearchUsers(params, githubToken);

                // User operations
                case "get_me":
                case "get_authenticated_user":
                    return callGetMe(params, githubToken);
                case "get_user":
                    return callGetUser(params, githubToken);
                case "list_user_followers":
                    return callListUserFollowers(params, githubToken);
                case "list_user_following":
                    return callListUserFollowing(params, githubToken);

                // Organization operations
                case "get_organization":
                    return callGetOrganization(params, githubToken);
                case "list_organization_members":
                    return callListOrganizationMembers(params, githubToken);
                case "list_organization_teams":
                    return callListOrganizationTeams(params, githubToken);

                // Workflow/Actions operations
                case "list_workflow_runs":
                    return callListWorkflowRuns(params, githubToken);
                case "get_workflow_run":
                    return callGetWorkflowRun(params, githubToken);
                case "list_workflow_run_jobs":
                    return callListWorkflowRunJobs(params, githubToken);
                case "get_workflow_run_logs_url":
                    return callGetWorkflowRunLogsUrl(params, githubToken);
                case "list_workflow_run_artifacts":
                    return callListWorkflowRunArtifacts(params, githubToken);
                case "trigger_workflow_dispatch":
                    callTriggerWorkflowDispatch(params, githubToken);
                    return objectMapper.createObjectNode().put("success", true);

                // Git operations
                case "list_tags":
                    return callListTags(params, githubToken);
                case "get_reference":
                    return callGetReference(params, githubToken);
                case "create_reference":
                    return callCreateReference(params, githubToken);
                case "update_reference":
                    return callUpdateReference(params, githubToken);
                case "delete_reference":
                    callDeleteReference(params, githubToken);
                    return objectMapper.createObjectNode().put("success", true);

                // Rate limit
                case "get_rate_limit":
                    return callGetRateLimit(params, githubToken);

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

        // File operations
        tools.add(createToolDefinition("get_file_contents", 
            "Get the contents of a file or directory from a repository",
            new String[]{"owner", "repo", "path"}));

        // Repository operations
        tools.add(createToolDefinition("search_repositories", 
            "Search for repositories on GitHub",
            new String[]{"query"}));
        tools.add(createToolDefinition("get_repository", 
            "Get details of a specific repository",
            new String[]{"owner", "repo"}));
        tools.add(createToolDefinition("list_user_repositories", 
            "List repositories for the authenticated user",
            new String[]{}));
        tools.add(createToolDefinition("list_user_repositories_by_username", 
            "List repositories for a specific user",
            new String[]{"username"}));
        tools.add(createToolDefinition("list_organization_repositories", 
            "List repositories for an organization",
            new String[]{"org"}));
        tools.add(createToolDefinition("create_repository", 
            "Create a new repository",
            new String[]{"name"}));
        tools.add(createToolDefinition("update_repository", 
            "Update repository settings",
            new String[]{"owner", "repo", "updates"}));
        tools.add(createToolDefinition("delete_repository", 
            "Delete a repository",
            new String[]{"owner", "repo"}));
        tools.add(createToolDefinition("list_branches", 
            "List branches for a repository",
            new String[]{"owner", "repo"}));
        tools.add(createToolDefinition("list_releases", 
            "List releases for a repository",
            new String[]{"owner", "repo"}));
        tools.add(createToolDefinition("get_latest_release", 
            "Get the latest release for a repository",
            new String[]{"owner", "repo"}));
        tools.add(createToolDefinition("get_release_by_tag", 
            "Get a specific release by tag name",
            new String[]{"owner", "repo", "tag"}));
        tools.add(createToolDefinition("list_collaborators", 
            "List repository collaborators",
            new String[]{"owner", "repo"}));
        tools.add(createToolDefinition("add_collaborator", 
            "Add a collaborator to a repository",
            new String[]{"owner", "repo", "username"}));
        tools.add(createToolDefinition("remove_collaborator", 
            "Remove a collaborator from a repository",
            new String[]{"owner", "repo", "username"}));
        tools.add(createToolDefinition("list_webhooks", 
            "List repository webhooks",
            new String[]{"owner", "repo"}));
        tools.add(createToolDefinition("create_webhook", 
            "Create a repository webhook",
            new String[]{"owner", "repo", "config"}));
        tools.add(createToolDefinition("delete_webhook", 
            "Delete a repository webhook",
            new String[]{"owner", "repo", "hook_id"}));
        
        // Commit operations
        tools.add(createToolDefinition("list_commits", 
            "List commits for a repository",
            new String[]{"owner", "repo"}));
        tools.add(createToolDefinition("get_commit", 
            "Get details of a specific commit",
            new String[]{"owner", "repo", "sha"}));
        
        // Issue operations
        tools.add(createToolDefinition("list_issues", 
            "List issues for a repository",
            new String[]{"owner", "repo"}));
        tools.add(createToolDefinition("get_issue", 
            "Get details of a specific issue",
            new String[]{"owner", "repo", "issue_number"}));
        tools.add(createToolDefinition("create_issue", 
            "Create a new issue",
            new String[]{"owner", "repo", "title"}));
        tools.add(createToolDefinition("update_issue", 
            "Update an existing issue",
            new String[]{"owner", "repo", "issue_number", "updates"}));
        tools.add(createToolDefinition("list_issue_comments", 
            "List comments on an issue",
            new String[]{"owner", "repo", "issue_number"}));
        tools.add(createToolDefinition("create_issue_comment", 
            "Create a comment on an issue",
            new String[]{"owner", "repo", "issue_number", "body"}));
        tools.add(createToolDefinition("list_issue_labels", 
            "List available labels for a repository",
            new String[]{"owner", "repo"}));
        tools.add(createToolDefinition("add_issue_labels", 
            "Add labels to an issue",
            new String[]{"owner", "repo", "issue_number", "labels"}));
        tools.add(createToolDefinition("search_issues", 
            "Search for issues and pull requests",
            new String[]{"query"}));
        
        // Pull request operations
        tools.add(createToolDefinition("list_pull_requests", 
            "List pull requests for a repository",
            new String[]{"owner", "repo"}));
        tools.add(createToolDefinition("get_pull_request", 
            "Get details of a specific pull request",
            new String[]{"owner", "repo", "pull_number"}));
        tools.add(createToolDefinition("create_pull_request", 
            "Create a new pull request",
            new String[]{"owner", "repo", "title", "head", "base"}));
        tools.add(createToolDefinition("update_pull_request", 
            "Update an existing pull request",
            new String[]{"owner", "repo", "pull_number", "updates"}));
        tools.add(createToolDefinition("merge_pull_request", 
            "Merge a pull request",
            new String[]{"owner", "repo", "pull_number"}));
        tools.add(createToolDefinition("list_pull_request_files", 
            "List files changed in a pull request",
            new String[]{"owner", "repo", "pull_number"}));
        tools.add(createToolDefinition("list_pull_request_reviews", 
            "List reviews on a pull request",
            new String[]{"owner", "repo", "pull_number"}));
        tools.add(createToolDefinition("create_pull_request_review", 
            "Create a review on a pull request",
            new String[]{"owner", "repo", "pull_number"}));
        tools.add(createToolDefinition("list_pull_request_review_comments", 
            "List review comments on a pull request",
            new String[]{"owner", "repo", "pull_number"}));
        
        // Search operations
        tools.add(createToolDefinition("search_code", 
            "Search code across GitHub",
            new String[]{"query"}));
        tools.add(createToolDefinition("search_users", 
            "Search for users on GitHub",
            new String[]{"query"}));
        
        // User operations
        tools.add(createToolDefinition("get_me", 
            "Get information about the authenticated user",
            new String[]{}));
        tools.add(createToolDefinition("get_user", 
            "Get information about a specific user",
            new String[]{"username"}));
        tools.add(createToolDefinition("list_user_followers", 
            "List followers of a user",
            new String[]{"username"}));
        tools.add(createToolDefinition("list_user_following", 
            "List users followed by a user",
            new String[]{"username"}));

        // Organization operations
        tools.add(createToolDefinition("get_organization", 
            "Get details of an organization",
            new String[]{"org"}));
        tools.add(createToolDefinition("list_organization_members", 
            "List members of an organization",
            new String[]{"org"}));
        tools.add(createToolDefinition("list_organization_teams", 
            "List teams in an organization",
            new String[]{"org"}));

        // Workflow/Actions operations
        tools.add(createToolDefinition("list_workflow_runs", 
            "List workflow runs for a repository",
            new String[]{"owner", "repo"}));
        tools.add(createToolDefinition("get_workflow_run", 
            "Get details of a specific workflow run",
            new String[]{"owner", "repo", "run_id"}));
        tools.add(createToolDefinition("list_workflow_run_jobs", 
            "List jobs for a workflow run",
            new String[]{"owner", "repo", "run_id"}));
        tools.add(createToolDefinition("get_workflow_run_logs_url", 
            "Get URL for workflow run logs",
            new String[]{"owner", "repo", "run_id"}));
        tools.add(createToolDefinition("list_workflow_run_artifacts", 
            "List artifacts for a workflow run",
            new String[]{"owner", "repo", "run_id"}));
        tools.add(createToolDefinition("trigger_workflow_dispatch", 
            "Trigger a workflow dispatch event",
            new String[]{"owner", "repo", "workflow_id", "ref"}));

        // Git operations
        tools.add(createToolDefinition("list_tags", 
            "List tags for a repository",
            new String[]{"owner", "repo"}));
        tools.add(createToolDefinition("get_reference", 
            "Get a Git reference",
            new String[]{"owner", "repo", "ref"}));
        tools.add(createToolDefinition("create_reference", 
            "Create a Git reference",
            new String[]{"owner", "repo", "ref", "sha"}));
        tools.add(createToolDefinition("update_reference", 
            "Update a Git reference",
            new String[]{"owner", "repo", "ref", "sha"}));
        tools.add(createToolDefinition("delete_reference", 
            "Delete a Git reference",
            new String[]{"owner", "repo", "ref"}));

        // Rate limit
        tools.add(createToolDefinition("get_rate_limit", 
            "Get API rate limit status",
            new String[]{}));

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

    private JsonNode callGetReleaseByTag(JsonNode params, String githubToken) {
        String owner = params.get("owner").asText();
        String repo = params.get("repo").asText();
        String tag = params.get("tag").asText();

        return githubApiService.getReleaseByTag(githubToken, owner, repo, tag);
    }

    private JsonNode callGetRepository(JsonNode params, String githubToken) {
        String owner = params.get("owner").asText();
        String repo = params.get("repo").asText();

        return githubApiService.getRepository(githubToken, owner, repo);
    }

    private JsonNode callListUserRepositories(JsonNode params, String githubToken) {
        String visibility = params.has("visibility") ? params.get("visibility").asText() : null;
        String sort = params.has("sort") ? params.get("sort").asText() : null;
        String direction = params.has("direction") ? params.get("direction").asText() : null;
        Integer page = params.has("page") ? params.get("page").asInt() : null;
        Integer perPage = params.has("perPage") ? params.get("perPage").asInt() : null;

        return githubApiService.listUserRepositories(githubToken, visibility, sort, direction, page, perPage);
    }

    private JsonNode callListUserRepositoriesByUsername(JsonNode params, String githubToken) {
        String username = params.get("username").asText();
        String type = params.has("type") ? params.get("type").asText() : null;
        String sort = params.has("sort") ? params.get("sort").asText() : null;
        String direction = params.has("direction") ? params.get("direction").asText() : null;
        Integer page = params.has("page") ? params.get("page").asInt() : null;
        Integer perPage = params.has("perPage") ? params.get("perPage").asInt() : null;

        return githubApiService.listUserRepositoriesByUsername(githubToken, username, type, sort, direction, page, perPage);
    }

    private JsonNode callListOrganizationRepositories(JsonNode params, String githubToken) {
        String org = params.get("org").asText();
        String type = params.has("type") ? params.get("type").asText() : null;
        String sort = params.has("sort") ? params.get("sort").asText() : null;
        String direction = params.has("direction") ? params.get("direction").asText() : null;
        Integer page = params.has("page") ? params.get("page").asInt() : null;
        Integer perPage = params.has("perPage") ? params.get("perPage").asInt() : null;

        return githubApiService.listOrganizationRepositories(githubToken, org, type, sort, direction, page, perPage);
    }

    private JsonNode callCreateRepository(JsonNode params, String githubToken) {
        String name = params.get("name").asText();
        String description = params.has("description") ? params.get("description").asText() : null;
        Boolean privateRepo = params.has("private") ? params.get("private").asBoolean() : null;
        Boolean hasIssues = params.has("has_issues") ? params.get("has_issues").asBoolean() : null;
        Boolean hasProjects = params.has("has_projects") ? params.get("has_projects").asBoolean() : null;
        Boolean hasWiki = params.has("has_wiki") ? params.get("has_wiki").asBoolean() : null;

        return githubApiService.createRepository(githubToken, name, description, privateRepo, hasIssues, hasProjects, hasWiki);
    }

    private JsonNode callUpdateRepository(JsonNode params, String githubToken) {
        String owner = params.get("owner").asText();
        String repo = params.get("repo").asText();
        Map<String, Object> updates = objectMapper.convertValue(params.get("updates"), new TypeReference<Map<String, Object>>() {});

        return githubApiService.updateRepository(githubToken, owner, repo, updates);
    }

    private void callDeleteRepository(JsonNode params, String githubToken) {
        String owner = params.get("owner").asText();
        String repo = params.get("repo").asText();

        githubApiService.deleteRepository(githubToken, owner, repo);
    }

    private JsonNode callCreateIssue(JsonNode params, String githubToken) {
        String owner = params.get("owner").asText();
        String repo = params.get("repo").asText();
        String title = params.get("title").asText();
        String body = params.has("body") ? params.get("body").asText() : null;
        String[] labels = params.has("labels") ? objectMapper.convertValue(params.get("labels"), String[].class) : null;
        String[] assignees = params.has("assignees") ? objectMapper.convertValue(params.get("assignees"), String[].class) : null;

        return githubApiService.createIssue(githubToken, owner, repo, title, body, labels, assignees);
    }

    private JsonNode callUpdateIssue(JsonNode params, String githubToken) {
        String owner = params.get("owner").asText();
        String repo = params.get("repo").asText();
        int issueNumber = params.get("issue_number").asInt();
        Map<String, Object> updates = objectMapper.convertValue(params.get("updates"), new TypeReference<Map<String, Object>>() {});

        return githubApiService.updateIssue(githubToken, owner, repo, issueNumber, updates);
    }

    private JsonNode callListIssueComments(JsonNode params, String githubToken) {
        String owner = params.get("owner").asText();
        String repo = params.get("repo").asText();
        int issueNumber = params.get("issue_number").asInt();
        Integer page = params.has("page") ? params.get("page").asInt() : null;
        Integer perPage = params.has("perPage") ? params.get("perPage").asInt() : null;

        return githubApiService.listIssueComments(githubToken, owner, repo, issueNumber, page, perPage);
    }

    private JsonNode callCreateIssueComment(JsonNode params, String githubToken) {
        String owner = params.get("owner").asText();
        String repo = params.get("repo").asText();
        int issueNumber = params.get("issue_number").asInt();
        String body = params.get("body").asText();

        return githubApiService.createIssueComment(githubToken, owner, repo, issueNumber, body);
    }

    private JsonNode callListIssueLabels(JsonNode params, String githubToken) {
        String owner = params.get("owner").asText();
        String repo = params.get("repo").asText();
        Integer page = params.has("page") ? params.get("page").asInt() : null;
        Integer perPage = params.has("perPage") ? params.get("perPage").asInt() : null;

        return githubApiService.listIssueLabels(githubToken, owner, repo, page, perPage);
    }

    private JsonNode callAddIssueLabels(JsonNode params, String githubToken) {
        String owner = params.get("owner").asText();
        String repo = params.get("repo").asText();
        int issueNumber = params.get("issue_number").asInt();
        String[] labels = objectMapper.convertValue(params.get("labels"), String[].class);

        return githubApiService.addIssueLabels(githubToken, owner, repo, issueNumber, labels);
    }

    private JsonNode callUpdatePullRequest(JsonNode params, String githubToken) {
        String owner = params.get("owner").asText();
        String repo = params.get("repo").asText();
        int pullNumber = params.get("pull_number").asInt();
        Map<String, Object> updates = objectMapper.convertValue(params.get("updates"), new TypeReference<Map<String, Object>>() {});

        return githubApiService.updatePullRequest(githubToken, owner, repo, pullNumber, updates);
    }

    private JsonNode callMergePullRequest(JsonNode params, String githubToken) {
        String owner = params.get("owner").asText();
        String repo = params.get("repo").asText();
        int pullNumber = params.get("pull_number").asInt();
        String commitTitle = params.has("commit_title") ? params.get("commit_title").asText() : null;
        String commitMessage = params.has("commit_message") ? params.get("commit_message").asText() : null;
        String mergeMethod = params.has("merge_method") ? params.get("merge_method").asText() : null;

        return githubApiService.mergePullRequest(githubToken, owner, repo, pullNumber, commitTitle, commitMessage, mergeMethod);
    }

    private JsonNode callListPullRequestFiles(JsonNode params, String githubToken) {
        String owner = params.get("owner").asText();
        String repo = params.get("repo").asText();
        int pullNumber = params.get("pull_number").asInt();
        Integer page = params.has("page") ? params.get("page").asInt() : null;
        Integer perPage = params.has("perPage") ? params.get("perPage").asInt() : null;

        return githubApiService.listPullRequestFiles(githubToken, owner, repo, pullNumber, page, perPage);
    }

    private JsonNode callListPullRequestReviews(JsonNode params, String githubToken) {
        String owner = params.get("owner").asText();
        String repo = params.get("repo").asText();
        int pullNumber = params.get("pull_number").asInt();
        Integer page = params.has("page") ? params.get("page").asInt() : null;
        Integer perPage = params.has("perPage") ? params.get("perPage").asInt() : null;

        return githubApiService.listPullRequestReviews(githubToken, owner, repo, pullNumber, page, perPage);
    }

    private JsonNode callCreatePullRequestReview(JsonNode params, String githubToken) {
        String owner = params.get("owner").asText();
        String repo = params.get("repo").asText();
        int pullNumber = params.get("pull_number").asInt();
        String body = params.has("body") ? params.get("body").asText() : null;
        String event = params.has("event") ? params.get("event").asText() : null;
        JsonNode comments = params.has("comments") ? params.get("comments") : null;

        return githubApiService.createPullRequestReview(githubToken, owner, repo, pullNumber, body, event, comments);
    }

    private JsonNode callListPullRequestReviewComments(JsonNode params, String githubToken) {
        String owner = params.get("owner").asText();
        String repo = params.get("repo").asText();
        int pullNumber = params.get("pull_number").asInt();
        Integer page = params.has("page") ? params.get("page").asInt() : null;
        Integer perPage = params.has("perPage") ? params.get("perPage").asInt() : null;

        return githubApiService.listPullRequestReviewComments(githubToken, owner, repo, pullNumber, page, perPage);
    }

    private JsonNode callGetUser(JsonNode params, String githubToken) {
        String username = params.get("username").asText();

        return githubApiService.getUser(githubToken, username);
    }

    private JsonNode callListUserFollowers(JsonNode params, String githubToken) {
        String username = params.get("username").asText();
        Integer page = params.has("page") ? params.get("page").asInt() : null;
        Integer perPage = params.has("perPage") ? params.get("perPage").asInt() : null;

        return githubApiService.listUserFollowers(githubToken, username, page, perPage);
    }

    private JsonNode callListUserFollowing(JsonNode params, String githubToken) {
        String username = params.get("username").asText();
        Integer page = params.has("page") ? params.get("page").asInt() : null;
        Integer perPage = params.has("perPage") ? params.get("perPage").asInt() : null;

        return githubApiService.listUserFollowing(githubToken, username, page, perPage);
    }

    private JsonNode callGetOrganization(JsonNode params, String githubToken) {
        String org = params.get("org").asText();

        return githubApiService.getOrganization(githubToken, org);
    }

    private JsonNode callListOrganizationMembers(JsonNode params, String githubToken) {
        String org = params.get("org").asText();
        String role = params.has("role") ? params.get("role").asText() : null;
        Integer page = params.has("page") ? params.get("page").asInt() : null;
        Integer perPage = params.has("perPage") ? params.get("perPage").asInt() : null;

        return githubApiService.listOrganizationMembers(githubToken, org, role, page, perPage);
    }

    private JsonNode callListOrganizationTeams(JsonNode params, String githubToken) {
        String org = params.get("org").asText();
        Integer page = params.has("page") ? params.get("page").asInt() : null;
        Integer perPage = params.has("perPage") ? params.get("perPage").asInt() : null;

        return githubApiService.listOrganizationTeams(githubToken, org, page, perPage);
    }

    private JsonNode callListWorkflowRuns(JsonNode params, String githubToken) {
        String owner = params.get("owner").asText();
        String repo = params.get("repo").asText();
        String actor = params.has("actor") ? params.get("actor").asText() : null;
        String branch = params.has("branch") ? params.get("branch").asText() : null;
        String event = params.has("event") ? params.get("event").asText() : null;
        String status = params.has("status") ? params.get("status").asText() : null;
        Integer page = params.has("page") ? params.get("page").asInt() : null;
        Integer perPage = params.has("perPage") ? params.get("perPage").asInt() : null;

        return githubApiService.listWorkflowRuns(githubToken, owner, repo, actor, branch, event, status, page, perPage);
    }

    private JsonNode callGetWorkflowRun(JsonNode params, String githubToken) {
        String owner = params.get("owner").asText();
        String repo = params.get("repo").asText();
        long runId = params.get("run_id").asLong();

        return githubApiService.getWorkflowRun(githubToken, owner, repo, runId);
    }

    private JsonNode callListWorkflowRunJobs(JsonNode params, String githubToken) {
        String owner = params.get("owner").asText();
        String repo = params.get("repo").asText();
        long runId = params.get("run_id").asLong();
        String filter = params.has("filter") ? params.get("filter").asText() : null;
        Integer page = params.has("page") ? params.get("page").asInt() : null;
        Integer perPage = params.has("perPage") ? params.get("perPage").asInt() : null;

        return githubApiService.listWorkflowRunJobs(githubToken, owner, repo, runId, filter, page, perPage);
    }

    private JsonNode callGetWorkflowRunLogsUrl(JsonNode params, String githubToken) {
        String owner = params.get("owner").asText();
        String repo = params.get("repo").asText();
        long runId = params.get("run_id").asLong();

        String logsUrl = githubApiService.getWorkflowRunLogsUrl(githubToken, owner, repo, runId);
        return objectMapper.createObjectNode().put("logs_url", logsUrl);
    }

    private JsonNode callListWorkflowRunArtifacts(JsonNode params, String githubToken) {
        String owner = params.get("owner").asText();
        String repo = params.get("repo").asText();
        long runId = params.get("run_id").asLong();
        Integer page = params.has("page") ? params.get("page").asInt() : null;
        Integer perPage = params.has("perPage") ? params.get("perPage").asInt() : null;

        return githubApiService.listWorkflowRunArtifacts(githubToken, owner, repo, runId, page, perPage);
    }

    private void callTriggerWorkflowDispatch(JsonNode params, String githubToken) {
        String owner = params.get("owner").asText();
        String repo = params.get("repo").asText();
        String workflowId = params.get("workflow_id").asText();
        String ref = params.get("ref").asText();
        Map<String, Object> inputs = params.has("inputs") ? objectMapper.convertValue(params.get("inputs"), new TypeReference<Map<String, Object>>() {}) : null;

        githubApiService.triggerWorkflowDispatch(githubToken, owner, repo, workflowId, ref, inputs);
    }

    private JsonNode callListTags(JsonNode params, String githubToken) {
        String owner = params.get("owner").asText();
        String repo = params.get("repo").asText();
        Integer page = params.has("page") ? params.get("page").asInt() : null;
        Integer perPage = params.has("perPage") ? params.get("perPage").asInt() : null;

        return githubApiService.listTags(githubToken, owner, repo, page, perPage);
    }

    private JsonNode callGetReference(JsonNode params, String githubToken) {
        String owner = params.get("owner").asText();
        String repo = params.get("repo").asText();
        String ref = params.get("ref").asText();

        return githubApiService.getReference(githubToken, owner, repo, ref);
    }

    private JsonNode callCreateReference(JsonNode params, String githubToken) {
        String owner = params.get("owner").asText();
        String repo = params.get("repo").asText();
        String ref = params.get("ref").asText();
        String sha = params.get("sha").asText();

        return githubApiService.createReference(githubToken, owner, repo, ref, sha);
    }

    private JsonNode callUpdateReference(JsonNode params, String githubToken) {
        String owner = params.get("owner").asText();
        String repo = params.get("repo").asText();
        String ref = params.get("ref").asText();
        String sha = params.get("sha").asText();
        Boolean force = params.has("force") ? params.get("force").asBoolean() : null;

        return githubApiService.updateReference(githubToken, owner, repo, ref, sha, force);
    }

    private void callDeleteReference(JsonNode params, String githubToken) {
        String owner = params.get("owner").asText();
        String repo = params.get("repo").asText();
        String ref = params.get("ref").asText();

        githubApiService.deleteReference(githubToken, owner, repo, ref);
    }

    private JsonNode callListCollaborators(JsonNode params, String githubToken) {
        String owner = params.get("owner").asText();
        String repo = params.get("repo").asText();
        Integer page = params.has("page") ? params.get("page").asInt() : null;
        Integer perPage = params.has("perPage") ? params.get("perPage").asInt() : null;

        return githubApiService.listCollaborators(githubToken, owner, repo, page, perPage);
    }

    private void callAddCollaborator(JsonNode params, String githubToken) {
        String owner = params.get("owner").asText();
        String repo = params.get("repo").asText();
        String username = params.get("username").asText();
        String permission = params.has("permission") ? params.get("permission").asText() : null;

        githubApiService.addCollaborator(githubToken, owner, repo, username, permission);
    }

    private void callRemoveCollaborator(JsonNode params, String githubToken) {
        String owner = params.get("owner").asText();
        String repo = params.get("repo").asText();
        String username = params.get("username").asText();

        githubApiService.removeCollaborator(githubToken, owner, repo, username);
    }

    private JsonNode callListWebhooks(JsonNode params, String githubToken) {
        String owner = params.get("owner").asText();
        String repo = params.get("repo").asText();
        Integer page = params.has("page") ? params.get("page").asInt() : null;
        Integer perPage = params.has("perPage") ? params.get("perPage").asInt() : null;

        return githubApiService.listWebhooks(githubToken, owner, repo, page, perPage);
    }

    private JsonNode callCreateWebhook(JsonNode params, String githubToken) {
        String owner = params.get("owner").asText();
        String repo = params.get("repo").asText();
        Map<String, Object> config = objectMapper.convertValue(params.get("config"), new TypeReference<Map<String, Object>>() {});
        String[] events = params.has("events") ? objectMapper.convertValue(params.get("events"), String[].class) : null;
        Boolean active = params.has("active") ? params.get("active").asBoolean() : null;

        return githubApiService.createWebhook(githubToken, owner, repo, config, events, active);
    }

    private void callDeleteWebhook(JsonNode params, String githubToken) {
        String owner = params.get("owner").asText();
        String repo = params.get("repo").asText();
        long hookId = params.get("hook_id").asLong();

        githubApiService.deleteWebhook(githubToken, owner, repo, hookId);
    }

    private JsonNode callGetRateLimit(JsonNode params, String githubToken) {
        return githubApiService.getRateLimit(githubToken);
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
