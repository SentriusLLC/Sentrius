package io.sentrius.agent.analysis.agents.integration;

import io.sentrius.sso.core.dto.TicketDTO;
import io.sentrius.sso.core.integrations.ticketing.JiraVerbService;
import io.sentrius.sso.core.model.users.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AIAgentJiraIntegrationServiceTest {

    @Mock
    private JiraVerbService jiraVerbService;

    private AIAgentJiraIntegrationService aiAgentJiraIntegrationService;

    @BeforeEach
    void setUp() {
        aiAgentJiraIntegrationService = new AIAgentJiraIntegrationService(jiraVerbService);
    }

    @Test
    void testSearchForTickets_Success() {
        // Given
        String query = "project = SUPPORT AND status = Open";
        List<TicketDTO> expectedTickets = Arrays.asList(
            TicketDTO.builder().id("SUPPORT-123").summary("Login issue").status("Open").build(),
            TicketDTO.builder().id("SUPPORT-124").summary("Password reset").status("Open").build()
        );
        
        when(jiraVerbService.searchForTickets(query)).thenReturn(expectedTickets);

        // When
        List<TicketDTO> result = aiAgentJiraIntegrationService.searchForTickets(query);

        // Then
        assertEquals(expectedTickets, result);
        verify(jiraVerbService).searchForTickets(query);
    }

    @Test
    void testSearchForTickets_EmptyResults() {
        // Given
        String query = "project = NONEXISTENT";
        List<TicketDTO> expectedTickets = List.of();
        
        when(jiraVerbService.searchForTickets(query)).thenReturn(expectedTickets);

        // When
        List<TicketDTO> result = aiAgentJiraIntegrationService.searchForTickets(query);

        // Then
        assertTrue(result.isEmpty());
        verify(jiraVerbService).searchForTickets(query);
    }

    @Test
    void testAssignTicket_Success() {
        // Given
        String ticketKey = "SUPPORT-123";
        User user = User.builder()
            .username("testuser")
            .emailAddress("test@example.com")
            .build();
        
        when(jiraVerbService.assignTicket(ticketKey, user)).thenReturn(true);

        // When
        Boolean result = aiAgentJiraIntegrationService.assignTicket(ticketKey, user);

        // Then
        assertTrue(result);
        verify(jiraVerbService).assignTicket(ticketKey, user);
    }

    @Test
    void testAssignTicket_Failure() {
        // Given
        String ticketKey = "SUPPORT-123";
        User user = User.builder()
            .username("testuser")
            .emailAddress("test@example.com")
            .build();
        
        when(jiraVerbService.assignTicket(ticketKey, user)).thenReturn(false);

        // When
        Boolean result = aiAgentJiraIntegrationService.assignTicket(ticketKey, user);

        // Then
        assertFalse(result);
        verify(jiraVerbService).assignTicket(ticketKey, user);
    }

    @Test
    void testUpdateTicket_Success() {
        // Given
        String ticketKey = "SUPPORT-123";
        User user = User.builder()
            .username("testuser")
            .emailAddress("test@example.com")
            .build();
        String message = "Working on this issue";
        
        when(jiraVerbService.updateTicket(ticketKey, user, message)).thenReturn(true);

        // When
        Boolean result = aiAgentJiraIntegrationService.updateTicket(ticketKey, user, message);

        // Then
        assertTrue(result);
        verify(jiraVerbService).updateTicket(ticketKey, user, message);
    }

    @Test
    void testUpdateTicket_Failure() {
        // Given
        String ticketKey = "SUPPORT-123";
        User user = User.builder()
            .username("testuser")
            .emailAddress("test@example.com")
            .build();
        String message = "Working on this issue";
        
        when(jiraVerbService.updateTicket(ticketKey, user, message)).thenReturn(false);

        // When
        Boolean result = aiAgentJiraIntegrationService.updateTicket(ticketKey, user, message);

        // Then
        assertFalse(result);
        verify(jiraVerbService).updateTicket(ticketKey, user, message);
    }

    @Test
    void testIsJiraAvailable_Available() {
        // Given
        when(jiraVerbService.isJiraAvailable()).thenReturn(true);

        // When
        Boolean result = aiAgentJiraIntegrationService.isJiraAvailable();

        // Then
        assertTrue(result);
        verify(jiraVerbService).isJiraAvailable();
    }

    @Test
    void testIsJiraAvailable_NotAvailable() {
        // Given
        when(jiraVerbService.isJiraAvailable()).thenReturn(false);

        // When
        Boolean result = aiAgentJiraIntegrationService.isJiraAvailable();

        // Then
        assertFalse(result);
        verify(jiraVerbService).isJiraAvailable();
    }
}