package io.sentrius.sso.core.integrations.ticketing;

import io.sentrius.sso.core.dto.TicketDTO;
import io.sentrius.sso.core.model.security.IntegrationSecurityToken;
import io.sentrius.sso.core.model.users.User;
import io.sentrius.sso.core.services.security.IntegrationSecurityTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JiraVerbServiceTest {

    @Mock
    private TicketService ticketService;

    @Mock
    private IntegrationSecurityTokenService integrationService;

    @InjectMocks
    private JiraVerbService jiraVerbService;

    private User testUser;
    private IntegrationSecurityToken mockIntegration;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setEmailAddress("test@example.com");
        
        mockIntegration = IntegrationSecurityToken.builder()
                .connectionType("jira")
                .name("Test JIRA Integration")
                .connectionInfo("{}")
                .build();
    }

    @Test
    void testSearchForTickets_WithJiraAvailable() {
        // Given
        String query = "project = TEST";
        List<TicketDTO> expectedTickets = List.of(
            TicketDTO.builder()
                .id("TEST-1")
                .summary("Test ticket")
                .status("Open")
                .type("jira")
                .build()
        );
        
        when(integrationService.findByConnectionType("jira")).thenReturn(List.of(mockIntegration));
        when(ticketService.searchForIncidents(query)).thenReturn(expectedTickets);

        // When
        List<TicketDTO> result = jiraVerbService.searchForTickets(query);

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("TEST-1", result.get(0).getId());
        assertEquals("Test ticket", result.get(0).getSummary());
        
        verify(integrationService).findByConnectionType("jira");
        verify(ticketService).searchForIncidents(query);
    }

    @Test
    void testSearchForTickets_WithoutJiraAvailable() {
        // Given
        String query = "project = TEST";
        when(integrationService.findByConnectionType("jira")).thenReturn(Collections.emptyList());

        // When
        List<TicketDTO> result = jiraVerbService.searchForTickets(query);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
        
        verify(integrationService).findByConnectionType("jira");
        verify(ticketService, never()).searchForIncidents(any());
    }

    @Test
    void testAssignTicket_WithJiraAvailable() {
        // Given
        String ticketKey = "TEST-1";
        when(integrationService.findByConnectionType("jira")).thenReturn(List.of(mockIntegration));
        when(ticketService.assignJira(ticketKey, testUser)).thenReturn(true);

        // When
        Boolean result = jiraVerbService.assignTicket(ticketKey, testUser);

        // Then
        assertTrue(result);
        
        verify(integrationService).findByConnectionType("jira");
        verify(ticketService).assignJira(ticketKey, testUser);
    }

    @Test
    void testAssignTicket_WithoutJiraAvailable() {
        // Given
        String ticketKey = "TEST-1";
        when(integrationService.findByConnectionType("jira")).thenReturn(Collections.emptyList());

        // When
        Boolean result = jiraVerbService.assignTicket(ticketKey, testUser);

        // Then
        assertFalse(result);
        
        verify(integrationService).findByConnectionType("jira");
        verify(ticketService, never()).assignJira(any(), any());
    }

    @Test
    void testUpdateTicket_WithJiraAvailable() {
        // Given
        String ticketKey = "TEST-1";
        String message = "Test comment";
        when(integrationService.findByConnectionType("jira")).thenReturn(List.of(mockIntegration));
        when(ticketService.updateJira(ticketKey, testUser, message)).thenReturn(true);

        // When
        Boolean result = jiraVerbService.updateTicket(ticketKey, testUser, message);

        // Then
        assertTrue(result);
        
        verify(integrationService).findByConnectionType("jira");
        verify(ticketService).updateJira(ticketKey, testUser, message);
    }

    @Test
    void testUpdateTicket_WithoutJiraAvailable() {
        // Given
        String ticketKey = "TEST-1";
        String message = "Test comment";
        when(integrationService.findByConnectionType("jira")).thenReturn(Collections.emptyList());

        // When
        Boolean result = jiraVerbService.updateTicket(ticketKey, testUser, message);

        // Then
        assertFalse(result);
        
        verify(integrationService).findByConnectionType("jira");
        verify(ticketService, never()).updateJira(any(), any(), any());
    }

    @Test
    void testIsJiraAvailable_WithJiraConfigured() {
        // Given
        when(integrationService.findByConnectionType("jira")).thenReturn(List.of(mockIntegration));

        // When
        Boolean result = jiraVerbService.isJiraAvailable();

        // Then
        assertTrue(result);
        
        verify(integrationService).findByConnectionType("jira");
    }

    @Test
    void testIsJiraAvailable_WithoutJiraConfigured() {
        // Given
        when(integrationService.findByConnectionType("jira")).thenReturn(Collections.emptyList());

        // When
        Boolean result = jiraVerbService.isJiraAvailable();

        // Then
        assertFalse(result);
        
        verify(integrationService).findByConnectionType("jira");
    }

    @Test
    void testIsJiraAvailable_WithException() {
        // Given
        when(integrationService.findByConnectionType("jira")).thenThrow(new RuntimeException("Database error"));

        // When
        Boolean result = jiraVerbService.isJiraAvailable();

        // Then
        assertFalse(result);
        
        verify(integrationService).findByConnectionType("jira");
    }
}