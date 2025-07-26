package io.sentrius.sso.controllers.api;

import io.sentrius.sso.core.dto.capabilities.EndpointDescriptor;
import io.sentrius.sso.core.services.capabilities.EndpointScanningService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test to verify that JIRA verbs are properly discovered by the capabilities endpoint.
 */


public class CapabilitiesApiControllerJiraIntegrationTest {

    @Autowired
    private EndpointScanningService endpointScanningService;

    
    public void testJiraVerbsAreDiscovered() {
        // Force refresh to ensure we get latest endpoints
        endpointScanningService.refreshEndpoints();
        
        List<EndpointDescriptor> allEndpoints = endpointScanningService.getAllEndpoints();
        
        // Verify we found some endpoints
        assertTrue(allEndpoints.size() > 0, "Should have found some endpoints");
        
        // Look for JIRA-related verbs
        List<EndpointDescriptor> jiraVerbs = allEndpoints.stream()
                .filter(endpoint -> "VERB".equals(endpoint.getType()))
                .filter(endpoint -> endpoint.getClassName().contains("JiraVerbService"))
                .toList();
        
        // Verify we found JIRA verbs
        assertTrue(jiraVerbs.size() > 0, "Should have found JiraVerbService endpoints");
        
        // Check for specific JIRA verbs
        boolean foundSearchForTickets = jiraVerbs.stream()
                .anyMatch(verb -> "searchForTickets".equals(verb.getName()));
        assertTrue(foundSearchForTickets, "Should have found searchForTickets verb");
        
        boolean foundAssignTicket = jiraVerbs.stream()
                .anyMatch(verb -> "assignTicket".equals(verb.getName()));
        assertTrue(foundAssignTicket, "Should have found assignTicket verb");
        
        boolean foundIsJiraAvailable = jiraVerbs.stream()
                .anyMatch(verb -> "isJiraAvailable".equals(verb.getName()));
        assertTrue(foundIsJiraAvailable, "Should have found isJiraAvailable verb");
        
        boolean foundUpdateTicket = jiraVerbs.stream()
                .anyMatch(verb -> "updateTicket".equals(verb.getName()));
        assertTrue(foundUpdateTicket, "Should have found updateTicket verb");
        
        // Verify the verbs are marked as AI callable
        for (EndpointDescriptor jiraVerb : jiraVerbs) {
            Object isAiCallable = jiraVerb.getMetadata().get("isAiCallable");
            assertTrue(isAiCallable instanceof Boolean && (Boolean) isAiCallable, 
                "JIRA verb " + jiraVerb.getName() + " should be AI callable");
        }
        
        // Log found verbs for debugging
        System.out.println("Found JIRA verbs:");
        jiraVerbs.forEach(verb -> {
            System.out.println("  - " + verb.getName() + ": " + verb.getDescription());
        });
    }


    public void testVerbEndpointFilterReturnsJiraVerbs() {
        // Force refresh to ensure we get latest endpoints
        endpointScanningService.refreshEndpoints();
        
        List<EndpointDescriptor> verbEndpoints = endpointScanningService.getAllEndpoints()
                .stream()
                .filter(endpoint -> "VERB".equals(endpoint.getType()))
                .toList();
        
        // Verify we have some verb endpoints
        assertTrue(verbEndpoints.size() > 0, "Should have found verb endpoints");
        
        // Look for JIRA verbs specifically
        List<EndpointDescriptor> jiraVerbs = verbEndpoints.stream()
                .filter(endpoint -> endpoint.getClassName().contains("JiraVerbService"))
                .toList();
        
        assertTrue(jiraVerbs.size() >= 4, "Should have found at least 4 JIRA verbs (searchForTickets, assignTicket, updateTicket, isJiraAvailable)");
    }
}