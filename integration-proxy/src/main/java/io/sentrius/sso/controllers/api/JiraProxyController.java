package io.sentrius.sso.controllers.api;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.sentrius.sso.config.ApplicationEnvironmentConfig;
import io.sentrius.sso.core.annotations.LimitAccess;
import io.sentrius.sso.core.config.SystemOptions;
import io.sentrius.sso.core.controllers.BaseController;
import io.sentrius.sso.core.dto.TicketDTO;
import io.sentrius.sso.core.integrations.ticketing.JiraService;
import io.sentrius.sso.core.model.security.IntegrationSecurityToken;
import io.sentrius.sso.core.model.security.enums.ApplicationAccessEnum;
import io.sentrius.sso.core.model.verbs.Endpoint;
import io.sentrius.sso.core.services.ErrorOutputService;
import io.sentrius.sso.core.services.UserService;
import io.sentrius.sso.core.services.security.IntegrationSecurityTokenService;
import io.sentrius.sso.core.services.security.KeycloakService;
import io.sentrius.sso.core.utils.JsonUtil;
import io.sentrius.sso.integrations.exceptions.HttpException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpStatus;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

@RestController
@RequestMapping("/api/v1/jira")
@Slf4j
public class JiraProxyController extends BaseController {

    final KeycloakService keycloakService;
    final IntegrationSecurityTokenService integrationSecurityTokenService;
    final RestTemplateBuilder restTemplateBuilder;
    final ApplicationEnvironmentConfig applicationConfig;

    Tracer tracer = GlobalOpenTelemetry.getTracer("io.sentrius.sso");

    protected JiraProxyController(
        UserService userService, 
        SystemOptions systemOptions,
        ErrorOutputService errorOutputService,
        KeycloakService keycloakService,
        IntegrationSecurityTokenService integrationSecurityTokenService,
        RestTemplateBuilder restTemplateBuilder,
        ApplicationEnvironmentConfig applicationConfig
    ) {
        super(userService, systemOptions, errorOutputService);
        this.keycloakService = keycloakService;
        this.integrationSecurityTokenService = integrationSecurityTokenService;
        this.restTemplateBuilder = restTemplateBuilder;
        this.applicationConfig = applicationConfig;
    }

    @GetMapping("/rest/api/3/search")
    @Endpoint(description = "Searches for JIRA issues using JQL or a simple query")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_LOG_IN})
    public ResponseEntity<?> searchForJiraIssue(
        @RequestHeader("Authorization") String token,
        @RequestParam(value = "jql", required = false) String jql,
        @RequestParam(value = "query", required = false) String query,
        HttpServletRequest request, 
        HttpServletResponse response
    ) throws JsonProcessingException, HttpException {

        Span span = tracer.spanBuilder("jira-proxy-search").startSpan();
        try (Scope scope = span.makeCurrent()) {
            String compactJwt = token.startsWith("Bearer ") ? token.substring(7) : token;

            if (!keycloakService.validateJwt(compactJwt)) {
                log.warn("Invalid Keycloak token");
                return ResponseEntity.status(HttpStatus.SC_UNAUTHORIZED).body("Invalid Keycloak token");
            }

            var operatingUser = getOperatingUser(request, response);
            if (null == operatingUser) {
                return ResponseEntity.status(HttpStatus.SC_UNAUTHORIZED).body("User not authenticated");
            }

            // Get the first available JIRA integration for the user
            // In a production environment, you might want to allow specifying which integration to use
            List<IntegrationSecurityToken> jiraIntegrations = integrationSecurityTokenService
                .findByConnectionType("jira");
            
            if (jiraIntegrations.isEmpty()) {
                return ResponseEntity.status(HttpStatus.SC_NOT_FOUND).body("No JIRA integration configured");
            }

            IntegrationSecurityToken jiraIntegration = jiraIntegrations.get(0);
            JiraService jiraService = new JiraService(new RestTemplate(), jiraIntegration);

            // Use the query parameter if jql is not provided
            String searchQuery = jql != null ? jql : query;
            if (searchQuery == null) {
                return ResponseEntity.badRequest().body("Either 'jql' or 'query' parameter is required");
            }

            List<TicketDTO> tickets = jiraService.searchForIncidents(searchQuery);
            
            span.setAttribute("search.query", searchQuery);
            span.setAttribute("search.results.count", tickets.size());
            
            return ResponseEntity.ok(tickets);
            
        } catch (ExecutionException | InterruptedException e) {
            log.error("Error executing JIRA search", e);
            throw new RuntimeException(e);
        } finally {
            span.end();
        }
    }

    @GetMapping("/rest/api/3/issue")
    @Endpoint(description = "Retrieves details of a specific JIRA issue")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_LOG_IN})
    public ResponseEntity<?> fetchJiraIssue(
        @RequestHeader("Authorization") String token,
        @RequestParam(name = "issueKey") String issueKey,
        HttpServletRequest request, 
        HttpServletResponse response
    ) throws JsonProcessingException, HttpException {

        Span span = tracer.spanBuilder("jira-proxy-get-issue").startSpan();
        try (Scope scope = span.makeCurrent()) {
            String compactJwt = token.startsWith("Bearer ") ? token.substring(7) : token;

            if (!keycloakService.validateJwt(compactJwt)) {
                log.warn("Invalid Keycloak token");
                return ResponseEntity.status(HttpStatus.SC_UNAUTHORIZED).body("Invalid Keycloak token");
            }

            var operatingUser = getOperatingUser(request, response);
            if (null == operatingUser) {
                return ResponseEntity.status(HttpStatus.SC_UNAUTHORIZED).body("User not authenticated");
            }

            List<IntegrationSecurityToken> jiraIntegrations = integrationSecurityTokenService
                .findByConnectionType("jira");
            
            if (jiraIntegrations.isEmpty()) {
                return ResponseEntity.status(HttpStatus.SC_NOT_FOUND).body("No JIRA integration configured");
            }

            IntegrationSecurityToken jiraIntegration = jiraIntegrations.get(0);
            JiraService jiraService = new JiraService(new RestTemplate(), jiraIntegration);

            boolean isActive = jiraService.isTicketActive(issueKey);
            
            span.setAttribute("issue.key", issueKey);
            span.setAttribute("issue.active", isActive);
            
            return ResponseEntity.ok(new IssueStatusResponse(issueKey, isActive ? "Active" : "Inactive"));
            
        } finally {
            span.end();
        }
    }

    @PostMapping("/rest/api/3/issue/comment")
    @Endpoint(description = "Adds a comment to a JIRA issue")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_LOG_IN})
    public ResponseEntity<?> addCommentToJiraIssue(
        @RequestHeader("Authorization") String token,
        @RequestParam(name="issueKey") String issueKey,
        @RequestBody CommentRequest commentRequest,
        HttpServletRequest request, 
        HttpServletResponse response
    ) throws JsonProcessingException, HttpException {

        Span span = tracer.spanBuilder("jira-proxy-add-comment").startSpan();
        try (Scope scope = span.makeCurrent()) {
            String compactJwt = token.startsWith("Bearer ") ? token.substring(7) : token;

            if (!keycloakService.validateJwt(compactJwt)) {
                log.warn("Invalid Keycloak token");
                return ResponseEntity.status(HttpStatus.SC_UNAUTHORIZED).body("Invalid Keycloak token");
            }

            var operatingUser = getOperatingUser(request, response);
            if (null == operatingUser) {
                return ResponseEntity.status(HttpStatus.SC_UNAUTHORIZED).body("User not authenticated");
            }

            List<IntegrationSecurityToken> jiraIntegrations = integrationSecurityTokenService
                .findByConnectionType("jira");
            
            if (jiraIntegrations.isEmpty()) {
                return ResponseEntity.status(HttpStatus.SC_NOT_FOUND).body("No JIRA integration configured");
            }

            IntegrationSecurityToken jiraIntegration = jiraIntegrations.get(0);
            JiraService jiraService = new JiraService(new RestTemplate(), jiraIntegration);

            // Extract comment text from the request
            String commentText = extractCommentText(commentRequest);
            if (commentText == null || commentText.trim().isEmpty()) {
                return ResponseEntity.badRequest().body("Comment text is required");
            }

            boolean success = jiraService.updateTicket(issueKey, commentText);
            
            span.setAttribute("issue.key", issueKey);
            span.setAttribute("comment.success", success);
            
            if (success) {
                return ResponseEntity.ok(new CommentResponse("Comment added successfully"));
            } else {
                return ResponseEntity.status(HttpStatus.SC_INTERNAL_SERVER_ERROR)
                    .body("Failed to add comment to issue");
            }
            
        } finally {
            span.end();
        }
    }

    @GetMapping("/rest/api/3/issue/comment")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_LOG_IN})
    public ResponseEntity<?> getJiraIssueComments(
        @RequestHeader("Authorization") String token,
        @RequestParam(name="issueKey") String issueKey,
        @RequestBody CommentRequest commentRequest,
        HttpServletRequest request,
        HttpServletResponse response
    ) throws JsonProcessingException {

        Span span = tracer.spanBuilder("jira-proxy-add-comment").startSpan();
        try (Scope scope = span.makeCurrent()) {
            String compactJwt = token.startsWith("Bearer ") ? token.substring(7) : token;

            if (!keycloakService.validateJwt(compactJwt)) {
                log.warn("Invalid Keycloak token");
                return ResponseEntity.status(HttpStatus.SC_UNAUTHORIZED).body("Invalid Keycloak token");
            }

            var operatingUser = getOperatingUser(request, response);
            if (null == operatingUser) {
                return ResponseEntity.status(HttpStatus.SC_UNAUTHORIZED).body("User not authenticated");
            }

            List<IntegrationSecurityToken> jiraIntegrations = integrationSecurityTokenService
                .findByConnectionType("jira");

            if (jiraIntegrations.isEmpty()) {
                return ResponseEntity.status(HttpStatus.SC_NOT_FOUND).body("No JIRA integration configured");
            }

            IntegrationSecurityToken jiraIntegration = jiraIntegrations.get(0);
            JiraService jiraService = new JiraService(new RestTemplate(), jiraIntegration);

            // Extract comment text from the request
            String commentText = extractCommentText(commentRequest);
            if (commentText == null || commentText.trim().isEmpty()) {
                return ResponseEntity.badRequest().body("Comment text is required");
            }

            List<String> comments  = jiraService.getComments(issueKey);

            span.setAttribute("issue.key", issueKey);
            span.setAttribute("comment.success", comments != null && !comments.isEmpty());

            if (comments != null && !comments.isEmpty()) {
                ObjectNode responseNode = JsonUtil.MAPPER.createObjectNode();
                responseNode.putArray("comments").addAll(comments.stream()
                    .map(TextNode::new)
                    .toList());
                return ResponseEntity.ok(responseNode);
            } else {
                return ResponseEntity.status(HttpStatus.SC_INTERNAL_SERVER_ERROR)
                    .body("Failed to add comment to issue");
            }

        } finally {
            span.end();
        }
    }

    @PutMapping("/rest/api/3/issue/assignee")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_LOG_IN})
    public ResponseEntity<?> assignJiraIssue(
        @RequestHeader("Authorization") String token,
        @RequestParam(name="issueKey") String issueKey,
        @RequestBody AssigneeRequest assigneeRequest,
        HttpServletRequest request, 
        HttpServletResponse response
    ) throws JsonProcessingException, HttpException {

        Span span = tracer.spanBuilder("jira-proxy-assign-issue").startSpan();
        try (Scope scope = span.makeCurrent()) {
            String compactJwt = token.startsWith("Bearer ") ? token.substring(7) : token;

            if (!keycloakService.validateJwt(compactJwt)) {
                log.warn("Invalid Keycloak token");
                return ResponseEntity.status(HttpStatus.SC_UNAUTHORIZED).body("Invalid Keycloak token");
            }

            var operatingUser = getOperatingUser(request, response);
            if (null == operatingUser) {
                return ResponseEntity.status(HttpStatus.SC_UNAUTHORIZED).body("User not authenticated");
            }

            List<IntegrationSecurityToken> jiraIntegrations = integrationSecurityTokenService
                .findByConnectionType("jira");
            
            if (jiraIntegrations.isEmpty()) {
                return ResponseEntity.status(HttpStatus.SC_NOT_FOUND).body("No JIRA integration configured");
            }

            IntegrationSecurityToken jiraIntegration = jiraIntegrations.get(0);
            JiraService jiraService = new JiraService(new RestTemplate(), jiraIntegration);

            Optional<String> assigneeId = Optional.ofNullable(assigneeRequest.getAccountId());
            boolean success = jiraService.assignTicket(issueKey, assigneeId);
            
            span.setAttribute("issue.key", issueKey);
            span.setAttribute("assignee.id", assigneeId.orElse("unassigned"));
            span.setAttribute("assignment.success", success);
            
            if (success) {
                return ResponseEntity.noContent().build();
            } else {
                return ResponseEntity.status(HttpStatus.SC_INTERNAL_SERVER_ERROR)
                    .body("Failed to assign issue");
            }
            
        } finally {
            span.end();
        }
    }

    private String extractCommentText(CommentRequest commentRequest) {
        // Handle both simple text and JIRA's complex body structure
        if (commentRequest.getBody() != null) {
            // Try to extract text from JIRA's structured body format
            Object body = commentRequest.getBody();
            if (body instanceof String) {
                return (String) body;
            }
            // For complex body structures, try to extract text
            // This would need more sophisticated parsing for real JIRA body format
            return body.toString();
        }
        return commentRequest.getText();
    }

    // DTOs for request/response
    public static class CommentRequest {
        private Object body;
        private String text;

        public Object getBody() { return body; }
        public void setBody(Object body) { this.body = body; }
        public String getText() { return text; }
        public void setText(String text) { this.text = text; }
    }

    public static class AssigneeRequest {
        private String accountId;

        public String getAccountId() { return accountId; }
        public void setAccountId(String accountId) { this.accountId = accountId; }
    }

    public static class IssueStatusResponse {
        private final String key;
        private final String status;

        public IssueStatusResponse(String key, String status) {
            this.key = key;
            this.status = status;
        }

        public String getKey() { return key; }
        public String getStatus() { return status; }
    }

    public static class CommentResponse {
        private final String message;

        public CommentResponse(String message) {
            this.message = message;
        }

        public String getMessage() { return message; }
    }
}