package io.sentrius.sso.core.integrations.ticketing;

import java.util.List;
import java.util.Optional;

import io.sentrius.sso.core.dto.TicketDTO;
import io.sentrius.sso.core.model.security.IntegrationSecurityToken;
import io.sentrius.sso.core.model.users.User;
import io.sentrius.sso.core.model.verbs.Verb;
import io.sentrius.sso.core.services.security.IntegrationSecurityTokenService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Service;

/**
 * Service that exposes JIRA operations as AI-callable verbs.
 * This allows AI agents to discover and call JIRA functionality through the capabilities API.
 */
@Slf4j
@Service
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class JiraVerbService {

    private final TicketService ticketService;
    private final IntegrationSecurityTokenService integrationService;

    public JiraVerbService(TicketService ticketService, IntegrationSecurityTokenService integrationService) {
        this.ticketService = ticketService;
        this.integrationService = integrationService;
    }

    /**
     * Searches for JIRA tickets based on a query string.
     * This method is exposed as a Verb so AI agents can discover and call it.
     *
     * @param query The search query (can be JQL or simple text)
     * @return List of tickets matching the query
     */
    @Verb(
        name = "searchForTickets",
        description = "Search for JIRA tickets using a query string. Can use JQL or simple text search.",
        returnType = List.class,
        isAiCallable = false,
        paramDescriptions = {"Search query string (JQL or simple text)"}
    )
    public List<TicketDTO> searchForTickets(String query) {
        log.info("Searching for tickets with query: {}", query);
        
        // Check if JIRA integration is available
        if (!isJiraIntegrationAvailable()) {
            log.warn("JIRA integration not available, returning empty results");
            return List.of();
        }
        
        return ticketService.searchForIncidents(query);
    }

    /**
     * Assigns a JIRA ticket to a user.
     * This method is exposed as a Verb so AI agents can discover and call it.
     *
     * @param ticketKey The JIRA ticket key (e.g., "PROJ-123")
     * @param user The user to assign the ticket to
     * @return true if assignment was successful, false otherwise
     */
    @Verb(
        name = "assignTicket",
        description = "Assign a JIRA ticket to a user",
        returnType = Boolean.class,
        isAiCallable = false,
        paramDescriptions = {"JIRA ticket key (e.g., PROJ-123)", "User to assign the ticket to"}
    )
    public Boolean assignTicket(String ticketKey, User user) {
        log.info("Assigning ticket {} to user {}", ticketKey, user.getEmailAddress());
        
        // Check if JIRA integration is available
        if (!isJiraIntegrationAvailable()) {
            log.warn("JIRA integration not available, cannot assign ticket");
            return false;
        }
        
        return ticketService.assignJira(ticketKey, user);
    }

    /**
     * Updates a JIRA ticket with a comment.
     * This method is exposed as a Verb so AI agents can discover and call it.
     *
     * @param ticketKey The JIRA ticket key (e.g., "PROJ-123")
     * @param user The user adding the comment
     * @param message The comment message
     * @return true if update was successful, false otherwise
     */
    @Verb(
        name = "updateTicket",
        description = "Add a comment to a JIRA ticket",
        returnType = Boolean.class,
        isAiCallable = false,
        paramDescriptions = {"JIRA ticket key (e.g., PROJ-123)", "User adding the comment", "Comment message"}
    )
    public Boolean updateTicket(String ticketKey, User user, String message) {
        log.info("Updating ticket {} with comment from user {}", ticketKey, user.getEmailAddress());
        
        // Check if JIRA integration is available
        if (!isJiraIntegrationAvailable()) {
            log.warn("JIRA integration not available, cannot update ticket");
            return false;
        }
        
        return ticketService.updateJira(ticketKey, user, message);
    }

    /**
     * Checks if at least one JIRA integration is configured and available.
     * This method is exposed as a Verb so AI agents can check JIRA availability.
     *
     * @return true if JIRA integration is available, false otherwise
     */
    @Verb(
        name = "isJiraAvailable",
        description = "Check if JIRA integration is configured and available",
        returnType = Boolean.class,
        isAiCallable = false,
        paramDescriptions = {}
    )
    public Boolean isJiraAvailable() {
        boolean available = isJiraIntegrationAvailable();
        log.info("JIRA integration availability check: {}", available);
        return available;
    }

    /**
     * Helper method to check if JIRA integration is available.
     * 
     * @return true if at least one JIRA integration is configured
     */
    private boolean isJiraIntegrationAvailable() {
        try {
            List<IntegrationSecurityToken> jiraIntegrations = integrationService.findByConnectionType("jira");
            return !jiraIntegrations.isEmpty();
        } catch (Exception e) {
            log.error("Error checking JIRA integration availability", e);
            return false;
        }
    }
}