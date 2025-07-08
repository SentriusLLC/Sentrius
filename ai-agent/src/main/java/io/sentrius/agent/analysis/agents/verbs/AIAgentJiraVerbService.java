package io.sentrius.agent.analysis.agents.verbs;

import io.sentrius.agent.analysis.agents.integration.AIAgentJiraIntegrationService;
import io.sentrius.sso.core.dto.TicketDTO;
import io.sentrius.sso.core.dto.ztat.AgentExecution;
import io.sentrius.sso.core.model.users.User;
import io.sentrius.sso.core.model.verbs.Verb;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Service that provides AI agent verbs for JIRA integration.
 * These verbs can be discovered and called by the AI agent system.
 */
@Slf4j
@Service
@ConditionalOnBean(AIAgentJiraIntegrationService.class)
@RequiredArgsConstructor
public class AIAgentJiraVerbService {

    private final AIAgentJiraIntegrationService jiraIntegrationService;

    /**
     * Searches for JIRA tickets based on a query string.
     * This verb allows AI agents to search for tickets using JQL or simple text.
     *
     * @param execution The agent execution context
     * @param args Map containing the search query
     * @return List of tickets matching the query
     */
    @Verb(
        name = "searchJiraTickets",
        description = "Search for JIRA tickets using a query string. Can use JQL or simple text search.",
        returnType = List.class,
        isAiCallable = true,
        paramDescriptions = {"Search query string (JQL or simple text)"}
    )
    public List<TicketDTO> searchJiraTickets(AgentExecution execution, Map<String, Object> args) {
        log.info("AI Agent verb: searchJiraTickets called with args: {}", args);
        
        String query = (String) args.get("query");
        if (query == null || query.trim().isEmpty()) {
            log.warn("No query provided for JIRA ticket search");
            return List.of();
        }
        
        return jiraIntegrationService.searchForTickets(query);
    }

    /**
     * Assigns a JIRA ticket to a user.
     * This verb allows AI agents to assign tickets to users.
     *
     * @param execution The agent execution context
     * @param args Map containing the ticket key and user
     * @return true if assignment was successful, false otherwise
     */
    @Verb(
        name = "assignJiraTicket",
        description = "Assign a JIRA ticket to a user",
        returnType = Boolean.class,
        isAiCallable = true,
        paramDescriptions = {"JIRA ticket key (e.g., PROJ-123)", "User to assign the ticket to"}
    )
    public Boolean assignJiraTicket(AgentExecution execution, Map<String, Object> args) {
        log.info("AI Agent verb: assignJiraTicket called with args: {}", args);
        
        String ticketKey = (String) args.get("ticketKey");
        User user = (User) args.get("user");
        
        if (ticketKey == null || ticketKey.trim().isEmpty()) {
            log.warn("No ticket key provided for JIRA ticket assignment");
            return false;
        }
        
        if (user == null) {
            log.warn("No user provided for JIRA ticket assignment");
            return false;
        }
        
        return jiraIntegrationService.assignTicket(ticketKey, user);
    }

    /**
     * Updates a JIRA ticket with a comment.
     * This verb allows AI agents to add comments to tickets.
     *
     * @param execution The agent execution context
     * @param args Map containing the ticket key, user, and message
     * @return true if update was successful, false otherwise
     */
    @Verb(
        name = "updateJiraTicket",
        description = "Add a comment to a JIRA ticket",
        returnType = Boolean.class,
        isAiCallable = true,
        paramDescriptions = {"JIRA ticket key (e.g., PROJ-123)", "User adding the comment", "Comment message"}
    )
    public Boolean updateJiraTicket(AgentExecution execution, Map<String, Object> args) {
        log.info("AI Agent verb: updateJiraTicket called with args: {}", args);
        
        String ticketKey = (String) args.get("ticketKey");
        User user = (User) args.get("user");
        String message = (String) args.get("message");
        
        if (ticketKey == null || ticketKey.trim().isEmpty()) {
            log.warn("No ticket key provided for JIRA ticket update");
            return false;
        }
        
        if (user == null) {
            log.warn("No user provided for JIRA ticket update");
            return false;
        }
        
        if (message == null || message.trim().isEmpty()) {
            log.warn("No message provided for JIRA ticket update");
            return false;
        }
        
        return jiraIntegrationService.updateTicket(ticketKey, user, message);
    }

    /**
     * Checks if JIRA integration is configured and available.
     * This verb allows AI agents to check JIRA availability before attempting operations.
     *
     * @param execution The agent execution context
     * @param args Map (can be empty)
     * @return true if JIRA integration is available, false otherwise
     */
    @Verb(
        name = "checkJiraAvailability",
        description = "Check if JIRA integration is configured and available",
        returnType = Boolean.class,
        isAiCallable = true,
        paramDescriptions = {}
    )
    public Boolean checkJiraAvailability(AgentExecution execution, Map<String, Object> args) {
        log.info("AI Agent verb: checkJiraAvailability called");
        return jiraIntegrationService.isJiraAvailable();
    }
}