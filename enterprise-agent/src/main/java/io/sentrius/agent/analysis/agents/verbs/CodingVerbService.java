package io.sentrius.agent.analysis.agents.verbs;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.sentrius.sso.core.dto.agents.AgentExecution;
import io.sentrius.sso.core.model.verbs.Verb;
import io.sentrius.sso.core.services.agents.AgentClientService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * Service that exposes coding operations as AI-callable verbs.
 * This allows the enterprise AI agent to discover and call the Python coding agent
 * for automated code generation and PR submission.
 */
@Slf4j
@Service
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class CodingVerbService extends VerbBase {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    
    @Value("${agent.coding.enabled:false}")
    private boolean codingAgentEnabled;
    
    @Value("${agent.coding.callback.url:}")
    private String codingAgentUrl;

    public CodingVerbService(@Value("${agent.ai.config}") String agentConfigFile,
                             @Value("${agent.ai.context.db.id:none}") String agentDatabaseContext,
                             AgentClientService agentClientService,
                             RestTemplate restTemplate,
                             ObjectMapper objectMapper) {
        super(agentConfigFile, agentDatabaseContext, agentClientService);
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Handles a JIRA issue by generating code and creating a pull request.
     * This method is exposed as a Verb so AI agents can discover and call it.
     *
     * @param issueKey JIRA issue key (e.g., "PROJECT-123")
     * @param repository GitHub repository (format: "owner/repo")
     * @param context Additional context for code generation (language, framework, etc.)
     * @return Status message indicating success or failure
     */
    @Verb(
        name = "handleJiraIssueWithCode",
        description = "Generate code and create a PR for a JIRA issue. Returns the PR URL if successful.",
        returnType = String.class,
        isAiCallable = true,
        paramDescriptions = {
            "JIRA issue key (e.g., PROJECT-123)",
            "GitHub repository (format: owner/repo)",
            "Context map with language, framework, and other details"
        }
    )
    public String handleJiraIssueWithCode(String issueKey, String repository, Map<String, Object> context) {
        log.info("Handling JIRA issue {} for repository {} with coding agent", issueKey, repository);
        
        if (!isCodingAgentAvailable()) {
            log.warn("Coding agent not available, cannot handle JIRA issue");
            return "Error: Coding agent is not configured or available";
        }
        
        try {
            Map<String, Object> taskData = new HashMap<>();
            taskData.put("operation", "handle_jira_issue");
            taskData.put("issue_key", issueKey);
            taskData.put("repo", repository);
            taskData.put("context", context != null ? context : new HashMap<>());
            
            String result = invokeCodingAgent(taskData);
            log.info("Successfully handled JIRA issue {} with coding agent", issueKey);
            return result;
            
        } catch (Exception e) {
            log.error("Error handling JIRA issue with coding agent", e);
            return "Error: " + e.getMessage();
        }
    }

    /**
     * Handles a GitHub issue by generating code and creating a pull request.
     * This method is exposed as a Verb so AI agents can discover and call it.
     *
     * @param repository GitHub repository (format: "owner/repo")
     * @param issueNumber GitHub issue number
     * @param context Additional context for code generation (language, framework, etc.)
     * @return Status message indicating success or failure
     */
    @Verb(
        name = "handleGitHubIssueWithCode",
        description = "Generate code and create a PR for a GitHub issue. Returns the PR URL if successful.",
        returnType = String.class,
        isAiCallable = true,
        paramDescriptions = {
            "GitHub repository (format: owner/repo)",
            "GitHub issue number",
            "Context map with language, framework, and other details"
        }
    )
    public String handleGitHubIssueWithCode(String repository, Integer issueNumber, Map<String, Object> context) {
        log.info("Handling GitHub issue #{} for repository {} with coding agent", issueNumber, repository);
        
        if (!isCodingAgentAvailable()) {
            log.warn("Coding agent not available, cannot handle GitHub issue");
            return "Error: Coding agent is not configured or available";
        }
        
        try {
            Map<String, Object> taskData = new HashMap<>();
            taskData.put("operation", "handle_github_issue");
            taskData.put("repo", repository);
            taskData.put("issue_number", issueNumber);
            taskData.put("context", context != null ? context : new HashMap<>());
            
            String result = invokeCodingAgent(taskData);
            log.info("Successfully handled GitHub issue #{} with coding agent", issueNumber);
            return result;
            
        } catch (Exception e) {
            log.error("Error handling GitHub issue with coding agent", e);
            return "Error: " + e.getMessage();
        }
    }

    /**
     * Creates a pull request with pre-generated code changes.
     * This method is exposed as a Verb so AI agents can discover and call it.
     *
     * @param repository GitHub repository (format: "owner/repo")
     * @param title Pull request title
     * @param description Pull request description
     * @param codeChanges Map containing code changes (files with paths, content, operations)
     * @return Status message indicating success or failure
     */
    @Verb(
        name = "createPullRequest",
        description = "Create a pull request with specified code changes. Returns the PR URL if successful.",
        returnType = String.class,
        isAiCallable = true,
        paramDescriptions = {
            "GitHub repository (format: owner/repo)",
            "Pull request title",
            "Pull request description",
            "Map containing code changes (files array with path, content, operation)"
        }
    )
    public String createPullRequest(String repository, String title, String description, 
                                   Map<String, Object> codeChanges) {
        log.info("Creating pull request for repository {} with coding agent", repository);
        
        if (!isCodingAgentAvailable()) {
            log.warn("Coding agent not available, cannot create PR");
            return "Error: Coding agent is not configured or available";
        }
        
        try {
            Map<String, Object> taskData = new HashMap<>();
            taskData.put("operation", "create_pr");
            taskData.put("repo", repository);
            taskData.put("title", title);
            taskData.put("description", description);
            if (codeChanges != null) {
                taskData.put("code_changes", codeChanges);
            }
            
            String result = invokeCodingAgent(taskData);
            log.info("Successfully created pull request for repository {}", repository);
            return result;
            
        } catch (Exception e) {
            log.error("Error creating pull request with coding agent", e);
            return "Error: " + e.getMessage();
        }
    }

    /**
     * Checks if the coding agent is configured and available.
     * This method is exposed as a Verb so AI agents can check coding agent availability.
     *
     * @return true if coding agent is available, false otherwise
     */
    @Verb(
        name = "isCodingAgentAvailable",
        description = "Check if the coding agent is configured and available for automated code generation",
        returnType = Boolean.class,
        isAiCallable = true,
        paramDescriptions = {}
    )
    public Boolean isCodingAgentAvailable() {
        boolean available = codingAgentEnabled && codingAgentUrl != null && !codingAgentUrl.isEmpty();
        log.info("Coding agent availability check: {}", available);
        return available;
    }

    /**
     * Invokes the Python coding agent with the specified task data.
     * 
     * @param taskData The task data to send to the coding agent
     * @return The result from the coding agent
     */
    private String invokeCodingAgent(Map<String, Object> taskData) throws Exception {
        if (codingAgentUrl == null || codingAgentUrl.isEmpty()) {
            throw new IllegalStateException("Coding agent URL not configured");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(taskData, headers);
        
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                codingAgentUrl + "/execute",
                HttpMethod.POST,
                request,
                String.class
            );
            
            if (response.getStatusCode().is2xxSuccessful()) {
                return response.getBody();
            } else {
                throw new Exception("Coding agent returned error: " + response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("Error invoking coding agent at {}", codingAgentUrl, e);
            throw new Exception("Failed to invoke coding agent: " + e.getMessage(), e);
        }
    }
}
