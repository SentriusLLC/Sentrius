package io.sentrius.agent.analysis.agents.verbs;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.sentrius.sso.core.dto.TicketDTO;
import io.sentrius.sso.core.dto.agents.AgentExecutionContextDTO;
import io.sentrius.sso.core.dto.ztat.TokenDTO;
import io.sentrius.sso.core.exceptions.ZtatException;
import io.sentrius.sso.core.model.verbs.Verb;
import io.sentrius.sso.core.services.agents.ZeroTrustClientService;
import io.sentrius.sso.core.utils.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * The `JiraVerbs` class provides methods to interact with JIRA ticket operations.
 * It includes functionality to search tickets, get ticket details, and add comments.
 * 
 * This integrates with the JIRA integration-proxy to enable agents to work with JIRA tickets.
 */
@Slf4j
@Service
public class JiraVerbs {

    private final ZeroTrustClientService zeroTrustClientService;

    /**
     * Constructs a `JiraVerbs` instance with the required services.
     *
     * @param zeroTrustClientService The service for interacting with Zero Trust APIs.
     */
    public JiraVerbs(ZeroTrustClientService zeroTrustClientService) {
        this.zeroTrustClientService = zeroTrustClientService;
    }

    /**
     * Searches for JIRA tickets using a JQL query or simple text search.
     *
     * @param token The zero trust token
     * @param contextDTO The execution context containing the query parameter
     * @return A list of TicketDTO objects matching the search criteria
     * @throws ZtatException If there is an error during the operation
     */
    @Verb(
        name = "search_jira_tickets",
        description = "Search for JIRA tickets using JQL or simple text query. Requires 'query' parameter in context.",
        returnType = List.class,
        returnName = "tickets",
        isAiCallable = true,
        requiresTokenManagement = true,
        paramDescriptions = {"query: JQL query string or simple text search"}
    )
    public List<TicketDTO> searchJiraTickets(TokenDTO token, AgentExecutionContextDTO contextDTO) 
            throws ZtatException {
        try {
            String query = contextDTO.getExecutionArgumentScoped("query", String.class)
                .orElseThrow(() -> new IllegalArgumentException("Query parameter is required"));
            
            log.info("Searching JIRA tickets with query: {}", query);
            
            // Call the integration-proxy JIRA search endpoint
            String response = zeroTrustClientService.callGetOnApi(token, "/api/v1/jira/rest/api/3/search", 
                Map.entry("query", List.of(query)));
            
            if (response == null) {
                log.warn("No JIRA tickets found for query: {}", query);
                return Collections.emptyList();
            }
            
            // Parse response as list of tickets
            List<TicketDTO> tickets = JsonUtil.MAPPER.readValue(response, 
                new TypeReference<List<TicketDTO>>() {});
            
            log.info("Found {} JIRA tickets", tickets != null ? tickets.size() : 0);
            return tickets != null ? tickets : Collections.emptyList();
            
        } catch (Exception e) {
            log.error("Failed to search JIRA tickets", e);
            throw new RuntimeException("Failed to search JIRA tickets: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves details of a specific JIRA ticket.
     *
     * @param token The zero trust token
     * @param contextDTO The execution context containing the issueKey parameter
     * @return The ticket details as TicketDTO
     * @throws ZtatException If there is an error during the operation
     */
    @Verb(
        name = "get_jira_ticket",
        description = "Get details of a specific JIRA ticket. Requires 'issueKey' parameter in context.",
        returnType = TicketDTO.class,
        returnName = "ticket",
        isAiCallable = true,
        requiresTokenManagement = true,
        paramDescriptions = {"issueKey: JIRA ticket key (e.g., PROJ-123)"}
    )
    public TicketDTO getJiraTicket(TokenDTO token, AgentExecutionContextDTO contextDTO) 
            throws ZtatException {
        try {
            String issueKey = contextDTO.getExecutionArgumentScoped("issueKey", String.class)
                .orElseThrow(() -> new IllegalArgumentException("issueKey parameter is required"));
            
            log.info("Fetching JIRA ticket: {}", issueKey);
            
            // Call the integration-proxy JIRA issue endpoint
            String response = zeroTrustClientService.callGetOnApi(token, "/api/v1/jira/rest/api/3/issue",
                Map.entry("issueKey", List.of(issueKey)));
            
            if (response == null) {
                throw new RuntimeException("JIRA ticket not found: " + issueKey);
            }
            
            TicketDTO ticket = JsonUtil.MAPPER.readValue(response, TicketDTO.class);
            
            log.info("Retrieved JIRA ticket: {}", issueKey);
            return ticket;
            
        } catch (Exception e) {
            log.error("Failed to get JIRA ticket", e);
            throw new RuntimeException("Failed to get JIRA ticket: " + e.getMessage(), e);
        }
    }

    /**
     * Adds a comment to a JIRA ticket.
     *
     * @param token The zero trust token
     * @param contextDTO The execution context containing issueKey and comment parameters
     * @return true if comment was added successfully
     * @throws ZtatException If there is an error during the operation
     */
    @Verb(
        name = "add_jira_comment",
        description = "Add a comment to a JIRA ticket. Requires 'issueKey' and 'comment' parameters in context.",
        returnType = Boolean.class,
        returnName = "success",
        isAiCallable = true,
        requiresTokenManagement = true,
        paramDescriptions = {
            "issueKey: JIRA ticket key (e.g., PROJ-123)",
            "comment: Comment text to add"
        }
    )
    public Boolean addJiraComment(TokenDTO token, AgentExecutionContextDTO contextDTO) 
            throws ZtatException {
        try {
            String issueKey = contextDTO.getExecutionArgumentScoped("issueKey", String.class)
                .orElseThrow(() -> new IllegalArgumentException("issueKey parameter is required"));
            String comment = contextDTO.getExecutionArgumentScoped("comment", String.class)
                .orElseThrow(() -> new IllegalArgumentException("comment parameter is required"));
            
            log.info("Adding comment to JIRA ticket: {}", issueKey);
            
            // Create request body
            ObjectNode requestBody = JsonUtil.MAPPER.createObjectNode();
            requestBody.put("comment", comment);
            
            // Call the integration-proxy JIRA comment endpoint
            String response = zeroTrustClientService.callPostOnApi(token, "/api/v1/jira/rest/api/3/comment",
                requestBody, Map.entry("issueKey", List.of(issueKey)));
            
            log.info("Successfully added comment to JIRA ticket: {}", issueKey);
            return response != null;
            
        } catch (Exception e) {
            log.error("Failed to add comment to JIRA ticket", e);
            return false;
        }
    }

    /**
     * Checks if JIRA integration is available and configured.
     *
     * @param token The zero trust token
     * @param contextDTO The execution context
     * @return true if JIRA is available, false otherwise
     */
    @Verb(
        name = "is_jira_available",
        description = "Check if JIRA integration is configured and available",
        returnType = Boolean.class,
        returnName = "available",
        isAiCallable = true,
        requiresTokenManagement = true
    )
    public Boolean isJiraAvailable(TokenDTO token, AgentExecutionContextDTO contextDTO) throws ZtatException {
        try {
            // Try to search with empty query to test connectivity
            String response = zeroTrustClientService.callGetOnApi(token, "/api/v1/jira/rest/api/3/search",
                Map.entry("query", List.of("")));
            return response != null;
        } catch (Exception e) {
            log.debug("JIRA integration not available: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Executes actions on SSH servers based on JIRA ticket instructions.
     * This combines JIRA ticket retrieval with SSH command extraction.
     *
     * @param token The zero trust token
     * @param contextDTO The execution context containing issueKey and hostSystemId parameters
     * @return Execution result with command output
     * @throws ZtatException If there is an error during the operation
     */
    @Verb(
        name = "execute_from_jira_ticket",
        description = "Execute commands on SSH servers based on JIRA ticket instructions. " +
                     "Extracts command from ticket description and prepares it for execution. " +
                     "Requires 'issueKey' and 'hostSystemId' parameters in context.",
        returnType = ObjectNode.class,
        returnName = "result",
        isAiCallable = true,
        requiresTokenManagement = true,
        paramDescriptions = {
            "issueKey: JIRA ticket key (e.g., PROJ-123)",
            "hostSystemId: ID of the host system to execute commands on"
        }
    )
    public ObjectNode executeFromJiraTicket(TokenDTO token, AgentExecutionContextDTO contextDTO) 
            throws ZtatException {
        try {
            String issueKey = contextDTO.getExecutionArgumentScoped("issueKey", String.class)
                .orElseThrow(() -> new IllegalArgumentException("issueKey parameter is required"));
            Long hostSystemId = contextDTO.getExecutionArgumentScoped("hostSystemId", Long.class)
                .orElseThrow(() -> new IllegalArgumentException("hostSystemId parameter is required"));
            
            log.info("Executing action from JIRA ticket {} on host {}", issueKey, hostSystemId);
            
            // Step 1: Get JIRA ticket details
            TicketDTO ticket = getJiraTicket(token, contextDTO);
            
            // Step 2: Extract command from ticket description
            String command = extractCommandFromTicket(ticket);
            if (command == null || command.isEmpty()) {
                throw new IllegalArgumentException(
                    "No command found in JIRA ticket. Use patterns like 'command:', 'execute:', or code blocks."
                );
            }
            
            log.info("Extracted command from ticket: {}", command);
            
            // Step 3: Prepare execution result (actual execution would be done by TerminalVerbs)
            ObjectNode result = JsonUtil.MAPPER.createObjectNode();
            result.put("issueKey", issueKey);
            result.put("hostSystemId", hostSystemId);
            result.put("command", command);
            result.put("ticketSummary", ticket.getSummary());
            result.put("status", "ready_to_execute");
            
            // Step 4: Add comment to JIRA ticket with execution plan
            String executionComment = String.format(
                "🤖 Agent Execution Plan\n\n" +
                "**Command to execute:** `%s`\n" +
                "**Target Host ID:** %d\n" +
                "**Status:** Ready for execution\n\n" +
                "This action will be executed by the automation agent.",
                command, hostSystemId
            );
            
            // Create a new context for the comment
            AgentExecutionContextDTO commentContext = new AgentExecutionContextDTO();
            commentContext.getExecutionArgs().put("issueKey", JsonUtil.MAPPER.convertValue(issueKey, com.fasterxml.jackson.databind.JsonNode.class));
            commentContext.getExecutionArgs().put("comment", JsonUtil.MAPPER.convertValue(executionComment, com.fasterxml.jackson.databind.JsonNode.class));
            addJiraComment(token, commentContext);
            
            log.info("Successfully prepared execution from JIRA ticket: {}", issueKey);
            return result;
            
        } catch (Exception e) {
            log.error("Failed to execute from JIRA ticket", e);
            throw new RuntimeException("Failed to execute from JIRA ticket: " + e.getMessage(), e);
        }
    }

    /**
     * Extracts a command from a JIRA ticket description.
     * Looks for patterns like "command:", "execute:", "run:", or code blocks.
     *
     * @param ticket The JIRA ticket
     * @return The extracted command, or null if not found
     */
    private String extractCommandFromTicket(TicketDTO ticket) {
        String description = ticket.getDescription();
        if (description == null || description.isEmpty()) {
            return null;
        }
        
        // Pattern matching for common command indicators
        String[] patterns = {"command:", "execute:", "run:", "cmd:"};
        
        String lowerDesc = description.toLowerCase();
        for (String pattern : patterns) {
            int idx = lowerDesc.indexOf(pattern);
            if (idx != -1) {
                // Extract text after the pattern
                int startIdx = idx + pattern.length();
                int endIdx = description.indexOf('\n', startIdx);
                if (endIdx == -1) {
                    endIdx = description.length();
                }
                
                String command = description.substring(startIdx, endIdx).trim();
                if (!command.isEmpty()) {
                    return command;
                }
            }
        }
        
        // Look for code blocks (```command```)
        int codeBlockStart = description.indexOf("```");
        if (codeBlockStart != -1) {
            int codeBlockEnd = description.indexOf("```", codeBlockStart + 3);
            if (codeBlockEnd != -1) {
                String command = description.substring(codeBlockStart + 3, codeBlockEnd).trim();
                // Remove language identifier if present (e.g., ```bash\ncommand``` -> command)
                int firstNewline = command.indexOf('\n');
                if (firstNewline != -1) {
                    command = command.substring(firstNewline + 1).trim();
                }
                if (!command.isEmpty()) {
                    return command;
                }
            }
        }
        
        return null;
    }
}
