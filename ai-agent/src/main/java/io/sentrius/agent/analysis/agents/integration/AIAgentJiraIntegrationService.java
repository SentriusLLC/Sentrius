package io.sentrius.agent.analysis.agents.integration;

import io.sentrius.sso.core.dto.TicketDTO;
import io.sentrius.sso.core.integrations.ticketing.JiraVerbService;
import io.sentrius.sso.core.model.users.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service that provides AI agent integration with JIRA capabilities.
 * This service bridges the ai-agent module with the JIRA capabilities from the dataplane module.
 */
@Slf4j
@Service
@ConditionalOnBean(JiraVerbService.class)
@RequiredArgsConstructor
public class AIAgentJiraIntegrationService {

    private final JiraVerbService jiraVerbService;

    /**
     * Searches for JIRA tickets based on a query string.
     * This is a convenience method that delegates to the JiraVerbService.
     *
     * @param query The search query (can be JQL or simple text)
     * @return List of tickets matching the query
     */
    public List<TicketDTO> searchForTickets(String query) {
        log.info("AI Agent requesting JIRA ticket search with query: {}", query);
        return jiraVerbService.searchForTickets(query);
    }

    /**
     * Assigns a JIRA ticket to a user.
     * This is a convenience method that delegates to the JiraVerbService.
     *
     * @param ticketKey The JIRA ticket key (e.g., "PROJ-123")
     * @param user The user to assign the ticket to
     * @return true if assignment was successful, false otherwise
     */
    public Boolean assignTicket(String ticketKey, User user) {
        log.info("AI Agent requesting JIRA ticket assignment: {} to user {}", ticketKey, user.getEmailAddress());
        return jiraVerbService.assignTicket(ticketKey, user);
    }

    /**
     * Updates a JIRA ticket with a comment.
     * This is a convenience method that delegates to the JiraVerbService.
     *
     * @param ticketKey The JIRA ticket key (e.g., "PROJ-123")
     * @param user The user adding the comment
     * @param message The comment message
     * @return true if update was successful, false otherwise
     */
    public Boolean updateTicket(String ticketKey, User user, String message) {
        log.info("AI Agent requesting JIRA ticket update: {} with comment from user {}", ticketKey, user.getEmailAddress());
        return jiraVerbService.updateTicket(ticketKey, user, message);
    }

    /**
     * Checks if JIRA integration is configured and available.
     * This is a convenience method that delegates to the JiraVerbService.
     *
     * @return true if JIRA integration is available, false otherwise
     */
    public Boolean isJiraAvailable() {
        log.info("AI Agent checking JIRA availability");
        return jiraVerbService.isJiraAvailable();
    }
}