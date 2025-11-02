package io.sentrius.sentrius.analysis.agents.agents;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.sentrius.agent.analysis.agents.agents.ChatAgent;
import io.sentrius.agent.analysis.agents.agents.VerbRegistry;
import io.sentrius.agent.analysis.agents.verbs.AgentVerbs;
import io.sentrius.agent.analysis.agents.verbs.ChatVerbs;
import io.sentrius.agent.analysis.api.AgentKeyService;
import io.sentrius.agent.analysis.api.UserCommunicationService;
import io.sentrius.agent.config.AgentConfigOptions;
import io.sentrius.sso.core.dto.UserDTO;
import io.sentrius.sso.core.dto.agents.AgentExecution;
import io.sentrius.sso.core.exceptions.ZtatException;
import io.sentrius.sso.core.services.agents.AgentClientService;
import io.sentrius.sso.core.services.agents.AgentExecutionService;
import io.sentrius.sso.core.services.agents.ZeroTrustClientService;
import io.sentrius.sso.core.services.security.KeycloakService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Test class for ChatAgent pause and resume functionality.
 */
@ExtendWith(MockitoExtension.class)
class ChatAgentPauseTest {

    @Mock
    private AgentVerbs agentVerbs;
    
    @Mock
    private ZeroTrustClientService zeroTrustClientService;
    
    @Mock
    private AgentClientService agentClientService;
    
    @Mock
    private VerbRegistry verbRegistry;
    
    @Mock
    private AgentExecutionService agentExecutionService;
    
    @Mock
    private UserCommunicationService userCommunicationService;
    
    @Mock
    private AgentConfigOptions agentConfigOptions;
    
    @Mock
    private AgentKeyService agentKeyService;
    
    @Mock
    private KeycloakService keycloakService;
    
    @Mock
    private ChatVerbs chatVerbs;

    private ChatAgent chatAgent;

    @BeforeEach
    void setUp() {
        chatAgent = new ChatAgent(
            agentVerbs,
            zeroTrustClientService,
            agentClientService,
            verbRegistry,
            agentExecutionService,
            userCommunicationService,
            agentConfigOptions,
            agentKeyService,
            keycloakService,
            chatVerbs
        );
    }

    @Test
    void testInitiallyNotPaused() {
        assertFalse(chatAgent.isPaused(), "Agent should not be paused initially");
    }

    @Test
    void testPauseAgent() throws ZtatException {
        // Setup mock agent execution
        AgentExecution mockExecution = AgentExecution.builder()
            .user(UserDTO.builder().username("test-user").build())
            .build();
        
        // Set the agent execution using the package-private setter
        chatAgent.setAgentExecution(mockExecution);

        // Pause the agent
        chatAgent.pauseAgent();

        // Verify the agent is paused
        assertTrue(chatAgent.isPaused(), "Agent should be paused after pauseAgent() is called");

        // Verify provenance event was submitted (once)
        verify(agentClientService, times(1)).submitProvenance(any(), any());
    }

    @Test
    void testResumeAgent() throws ZtatException {
        // Setup mock agent execution
        AgentExecution mockExecution = AgentExecution.builder()
            .user(UserDTO.builder().username("test-user").build())
            .build();
        
        chatAgent.setAgentExecution(mockExecution);

        // Pause and then resume the agent
        chatAgent.pauseAgent();
        chatAgent.resumeAgent();

        // Verify the agent is no longer paused
        assertFalse(chatAgent.isPaused(), "Agent should not be paused after resumeAgent() is called");

        // Verify provenance events were submitted (twice: once for pause, once for resume)
        verify(agentClientService, times(2)).submitProvenance(any(), any());
    }

    @Test
    void testMultiplePauseCallsIdempotent() throws ZtatException {
        // Setup mock agent execution
        AgentExecution mockExecution = AgentExecution.builder()
            .user(UserDTO.builder().username("test-user").build())
            .build();
        
        chatAgent.setAgentExecution(mockExecution);

        // Pause multiple times
        chatAgent.pauseAgent();
        chatAgent.pauseAgent();
        chatAgent.pauseAgent();

        // Should still be paused
        assertTrue(chatAgent.isPaused(), "Agent should remain paused");

        // Verify provenance event was only submitted once (idempotent)
        verify(agentClientService, times(1)).submitProvenance(any(), any());
    }

    @Test
    void testResumeWithoutPauseDoesNothing() throws ZtatException {
        // Setup mock agent execution
        AgentExecution mockExecution = AgentExecution.builder()
            .user(UserDTO.builder().username("test-user").build())
            .build();
        
        chatAgent.setAgentExecution(mockExecution);

        // Try to resume without pausing first
        chatAgent.resumeAgent();

        // Should still not be paused
        assertFalse(chatAgent.isPaused(), "Agent should not be paused");

        // No provenance events should be submitted
        verify(agentClientService, never()).submitProvenance(any(), any());
    }

    @Test
    void testGetAgentExecutionReturnsContext() {
        // Create and set a mock agent execution
        AgentExecution mockExecution = AgentExecution.builder()
            .executionId("test-execution-id")
            .user(UserDTO.builder().username("test-user").build())
            .build();
        
        chatAgent.setAgentExecution(mockExecution);

        // Get the agent execution
        AgentExecution result = chatAgent.getAgentExecution();

        // Verify it returns the correct execution
        assertNotNull(result, "Agent execution should not be null");
        assertEquals("test-execution-id", result.getExecutionId(), "Execution ID should match");
        assertEquals("test-user", result.getUser().getUsername(), "Username should match");
    }

    @Test
    void testShutdownWakesPausedAgent() throws ZtatException {
        // Setup mock agent execution
        AgentExecution mockExecution = AgentExecution.builder()
            .user(UserDTO.builder().username("test-user").build())
            .build();
        
        chatAgent.setAgentExecution(mockExecution);

        // Pause the agent
        chatAgent.pauseAgent();
        assertTrue(chatAgent.isPaused(), "Agent should be paused");

        // Shutdown the agent
        chatAgent.shutdown();

        // Agent should no longer be paused after shutdown
        assertFalse(chatAgent.isPaused(), "Agent should not be paused after shutdown");
    }
}
