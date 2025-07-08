package io.sentrius.agent.analysis.agents.verbs;

import io.sentrius.agent.analysis.agents.integration.AIAgentJiraIntegrationService;
import io.sentrius.sso.core.dto.TicketDTO;
import io.sentrius.sso.core.dto.ztat.AgentExecution;
import io.sentrius.sso.core.model.users.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AIAgentJiraVerbServiceTest {

    @Mock
    private AIAgentJiraIntegrationService jiraIntegrationService;

    @Mock
    private AgentExecution agentExecution;

    private AIAgentJiraVerbService aiAgentJiraVerbService;

    @BeforeEach
    void setUp() {
        aiAgentJiraVerbService = new AIAgentJiraVerbService(jiraIntegrationService);
    }

    @Test
    void testSearchJiraTickets_Success() {
        // Given
        String query = "project = SUPPORT AND status = Open";
        Map<String, Object> args = new HashMap<>();
        args.put("query", query);
        
        List<TicketDTO> expectedTickets = Arrays.asList(
            TicketDTO.builder().id("SUPPORT-123").summary("Login issue").status("Open").build(),
            TicketDTO.builder().id("SUPPORT-124").summary("Password reset").status("Open").build()
        );
        
        when(jiraIntegrationService.searchForTickets(query)).thenReturn(expectedTickets);

        // When
        List<TicketDTO> result = aiAgentJiraVerbService.searchJiraTickets(agentExecution, args);

        // Then
        assertEquals(expectedTickets, result);
        verify(jiraIntegrationService).searchForTickets(query);
    }

    @Test
    void testSearchJiraTickets_EmptyQuery() {
        // Given
        Map<String, Object> args = new HashMap<>();
        args.put("query", "");

        // When
        List<TicketDTO> result = aiAgentJiraVerbService.searchJiraTickets(agentExecution, args);

        // Then
        assertTrue(result.isEmpty());
        verify(jiraIntegrationService, never()).searchForTickets(anyString());
    }

    @Test
    void testSearchJiraTickets_NullQuery() {
        // Given
        Map<String, Object> args = new HashMap<>();
        args.put("query", null);

        // When
        List<TicketDTO> result = aiAgentJiraVerbService.searchJiraTickets(agentExecution, args);

        // Then
        assertTrue(result.isEmpty());
        verify(jiraIntegrationService, never()).searchForTickets(anyString());
    }

    @Test
    void testAssignJiraTicket_Success() {
        // Given
        String ticketKey = "SUPPORT-123";
        User user = User.builder()
            .username("testuser")
            .emailAddress("test@example.com")
            .build();
        
        Map<String, Object> args = new HashMap<>();
        args.put("ticketKey", ticketKey);
        args.put("user", user);
        
        when(jiraIntegrationService.assignTicket(ticketKey, user)).thenReturn(true);

        // When
        Boolean result = aiAgentJiraVerbService.assignJiraTicket(agentExecution, args);

        // Then
        assertTrue(result);
        verify(jiraIntegrationService).assignTicket(ticketKey, user);
    }

    @Test
    void testAssignJiraTicket_EmptyTicketKey() {
        // Given
        User user = User.builder()
            .username("testuser")
            .emailAddress("test@example.com")
            .build();
        
        Map<String, Object> args = new HashMap<>();
        args.put("ticketKey", "");
        args.put("user", user);

        // When
        Boolean result = aiAgentJiraVerbService.assignJiraTicket(agentExecution, args);

        // Then
        assertFalse(result);
        verify(jiraIntegrationService, never()).assignTicket(anyString(), any(User.class));
    }

    @Test
    void testAssignJiraTicket_NullUser() {
        // Given
        String ticketKey = "SUPPORT-123";
        
        Map<String, Object> args = new HashMap<>();
        args.put("ticketKey", ticketKey);
        args.put("user", null);

        // When
        Boolean result = aiAgentJiraVerbService.assignJiraTicket(agentExecution, args);

        // Then
        assertFalse(result);
        verify(jiraIntegrationService, never()).assignTicket(anyString(), any(User.class));
    }

    @Test
    void testUpdateJiraTicket_Success() {
        // Given
        String ticketKey = "SUPPORT-123";
        User user = User.builder()
            .username("testuser")
            .emailAddress("test@example.com")
            .build();
        String message = "Working on this issue";
        
        Map<String, Object> args = new HashMap<>();
        args.put("ticketKey", ticketKey);
        args.put("user", user);
        args.put("message", message);
        
        when(jiraIntegrationService.updateTicket(ticketKey, user, message)).thenReturn(true);

        // When
        Boolean result = aiAgentJiraVerbService.updateJiraTicket(agentExecution, args);

        // Then
        assertTrue(result);
        verify(jiraIntegrationService).updateTicket(ticketKey, user, message);
    }

    @Test
    void testUpdateJiraTicket_EmptyMessage() {
        // Given
        String ticketKey = "SUPPORT-123";
        User user = User.builder()
            .username("testuser")
            .emailAddress("test@example.com")
            .build();
        
        Map<String, Object> args = new HashMap<>();
        args.put("ticketKey", ticketKey);
        args.put("user", user);
        args.put("message", "");

        // When
        Boolean result = aiAgentJiraVerbService.updateJiraTicket(agentExecution, args);

        // Then
        assertFalse(result);
        verify(jiraIntegrationService, never()).updateTicket(anyString(), any(User.class), anyString());
    }

    @Test
    void testUpdateJiraTicket_NullTicketKey() {
        // Given
        User user = User.builder()
            .username("testuser")
            .emailAddress("test@example.com")
            .build();
        String message = "Working on this issue";
        
        Map<String, Object> args = new HashMap<>();
        args.put("ticketKey", null);
        args.put("user", user);
        args.put("message", message);

        // When
        Boolean result = aiAgentJiraVerbService.updateJiraTicket(agentExecution, args);

        // Then
        assertFalse(result);
        verify(jiraIntegrationService, never()).updateTicket(anyString(), any(User.class), anyString());
    }

    @Test
    void testCheckJiraAvailability_Available() {
        // Given
        Map<String, Object> args = new HashMap<>();
        when(jiraIntegrationService.isJiraAvailable()).thenReturn(true);

        // When
        Boolean result = aiAgentJiraVerbService.checkJiraAvailability(agentExecution, args);

        // Then
        assertTrue(result);
        verify(jiraIntegrationService).isJiraAvailable();
    }

    @Test
    void testCheckJiraAvailability_NotAvailable() {
        // Given
        Map<String, Object> args = new HashMap<>();
        when(jiraIntegrationService.isJiraAvailable()).thenReturn(false);

        // When
        Boolean result = aiAgentJiraVerbService.checkJiraAvailability(agentExecution, args);

        // Then
        assertFalse(result);
        verify(jiraIntegrationService).isJiraAvailable();
    }
}