package io.sentrius.agent.analysis.agents.agents;

import io.sentrius.agent.analysis.agents.integration.AIAgentJiraIntegrationService;
import io.sentrius.agent.analysis.agents.verbs.AIAgentJiraVerbService;
import io.sentrius.agent.discovery.AgentEndpointDiscoveryService;
import io.sentrius.sso.core.services.capabilities.EndpointScanningService;
import io.sentrius.sso.core.services.agents.ZeroTrustClientService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VerbRegistryJiraIntegrationTest {

    @Mock
    private ApplicationContext applicationContext;

    @Mock
    private ZeroTrustClientService zeroTrustClientService;

    @Mock
    private EndpointScanningService endpointScanningService;

    @Mock
    private AgentEndpointDiscoveryService agentEndpointDiscoveryService;

    @Mock
    private AIAgentJiraIntegrationService aiAgentJiraIntegrationService;

    @Test
    void testVerbRegistryScansJiraVerbs() {
        // Given
        AIAgentJiraVerbService jiraVerbService = new AIAgentJiraVerbService(aiAgentJiraIntegrationService);
        when(applicationContext.getBean(AIAgentJiraVerbService.class)).thenReturn(jiraVerbService);

        VerbRegistry verbRegistry = new VerbRegistry(
            applicationContext,
            zeroTrustClientService,
            endpointScanningService,
            agentEndpointDiscoveryService
        );

        // When
        verbRegistry.scanClasspath();

        // Then
        // Verify that the registry contains the expected JIRA verbs
        assertTrue(verbRegistry.isVerbRegistered("searchJiraTickets"));
        assertTrue(verbRegistry.isVerbRegistered("assignJiraTicket"));
        assertTrue(verbRegistry.isVerbRegistered("updateJiraTicket"));
        assertTrue(verbRegistry.isVerbRegistered("checkJiraAvailability"));
    }

    @Test
    void testVerbRegistryGetVerbs() {
        // Given
        AIAgentJiraVerbService jiraVerbService = new AIAgentJiraVerbService(aiAgentJiraIntegrationService);
        when(applicationContext.getBean(AIAgentJiraVerbService.class)).thenReturn(jiraVerbService);

        VerbRegistry verbRegistry = new VerbRegistry(
            applicationContext,
            zeroTrustClientService,
            endpointScanningService,
            agentEndpointDiscoveryService
        );

        // When
        verbRegistry.scanClasspath();
        var verbs = verbRegistry.getVerbs();

        // Then
        assertNotNull(verbs);
        assertTrue(verbs.containsKey("searchJiraTickets"));
        assertTrue(verbs.containsKey("assignJiraTicket"));
        assertTrue(verbs.containsKey("updateJiraTicket"));
        assertTrue(verbs.containsKey("checkJiraAvailability"));
    }
}