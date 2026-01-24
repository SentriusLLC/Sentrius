package io.sentrius.sentrius.analysis.agents.verbs;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.sentrius.agent.analysis.agents.verbs.JiraVerbs;
import io.sentrius.sso.core.dto.TicketDTO;
import io.sentrius.sso.core.dto.agents.AgentExecutionContextDTO;
import io.sentrius.sso.core.dto.ztat.TokenDTO;
import io.sentrius.sso.core.exceptions.ZtatException;
import io.sentrius.sso.core.services.agents.ZeroTrustClientService;
import io.sentrius.sso.core.utils.JsonUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for JiraVerbs class
 */
class JiraVerbsTest {

    @Mock
    private ZeroTrustClientService zeroTrustClientService;

    @Mock
    private TokenDTO token;

    private JiraVerbs jiraVerbs;
    private AgentExecutionContextDTO contextDTO;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        jiraVerbs = new JiraVerbs(zeroTrustClientService);
        contextDTO = new AgentExecutionContextDTO();
    }

    @Test
    void testSearchJiraTickets_Success() throws ZtatException, JsonProcessingException {
        // Arrange
        String query = "project = TEST";
        contextDTO.getExecutionArgs().put("query", JsonUtil.MAPPER.convertValue(query, com.fasterxml.jackson.databind.JsonNode.class));
        
        TicketDTO ticket1 = TicketDTO.builder()
            .id("TEST-123")
            .summary("Test ticket 1")
            .build();
        
        TicketDTO ticket2 = TicketDTO.builder()
            .id("TEST-124")
            .summary("Test ticket 2")
            .build();
        
        List<TicketDTO> expectedTickets = Arrays.asList(ticket1, ticket2);
        String jsonResponse = JsonUtil.MAPPER.writeValueAsString(expectedTickets);
        
        // Use lenient stubbing with explicit type matchers for varargs methods
        org.mockito.Mockito.lenient().doReturn(jsonResponse).when(zeroTrustClientService)
            .callGetOnApi(any(TokenDTO.class), any(String.class), any(java.util.Map.Entry.class));

        // Act
        List<TicketDTO> result = jiraVerbs.searchJiraTickets(token, contextDTO);
        
        // Assert
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("TEST-123", result.get(0).getId());
        assertEquals("TEST-124", result.get(1).getId());
    }

    @Test
    void testSearchJiraTickets_MissingQuery() {
        // Arrange - no query in context
        
        // Act & Assert
        assertThrows(RuntimeException.class, () -> {
            jiraVerbs.searchJiraTickets(token, contextDTO);
        });
    }

    @Test
    void testGetJiraTicket_Success() throws ZtatException, JsonProcessingException {
        // Arrange
        String issueKey = "TEST-123";
        contextDTO.getExecutionArgs().put("issueKey", JsonUtil.MAPPER.convertValue(issueKey, com.fasterxml.jackson.databind.JsonNode.class));
        
        TicketDTO expectedTicket = TicketDTO.builder()
            .id(issueKey)
            .summary("Test ticket")
            .description("command: ls -la")
            .build();
        
        String jsonResponse = JsonUtil.MAPPER.writeValueAsString(expectedTicket);
        
        // Use lenient stubbing with explicit type matchers for varargs methods
        org.mockito.Mockito.lenient().doReturn(jsonResponse).when(zeroTrustClientService)
            .callGetOnApi(any(TokenDTO.class), any(String.class), any(java.util.Map.Entry.class));

        // Act
        TicketDTO result = jiraVerbs.getJiraTicket(token, contextDTO);
        
        // Assert
        assertNotNull(result);
        assertEquals(issueKey, result.getId());
        assertEquals("Test ticket", result.getSummary());
    }

    @Test
    void testAddJiraComment_Success() throws ZtatException {
        // Arrange
        String issueKey = "TEST-123";
        String comment = "Test comment";
        contextDTO.getExecutionArgs().put("issueKey", JsonUtil.MAPPER.convertValue(issueKey, com.fasterxml.jackson.databind.JsonNode.class));
        contextDTO.getExecutionArgs().put("comment", JsonUtil.MAPPER.convertValue(comment, com.fasterxml.jackson.databind.JsonNode.class));
        
        when(zeroTrustClientService.callPostOnApi(
            eq(token),
            anyString(),
            any(),
            any()
        )).thenReturn("Success");
        
        // Act
        Boolean result = jiraVerbs.addJiraComment(token, contextDTO);
        
        // Assert
        assertTrue(result);
        
        verify(zeroTrustClientService).callPostOnApi(
            eq(token),
            anyString(),
            any(),
            any()
        );
    }

    @Test
    void testIsJiraAvailable_Available() throws ZtatException {
        // Arrange - Use lenient stubbing with explicit type matchers for varargs methods
        org.mockito.Mockito.lenient().doReturn("[]").when(zeroTrustClientService)
            .callGetOnApi(any(TokenDTO.class), any(String.class), any(java.util.Map.Entry.class));

        // Act
        Boolean result = jiraVerbs.isJiraAvailable(token, contextDTO);
        
        // Assert
        assertTrue(result);
    }

    @Test
    void testIsJiraAvailable_NotAvailable() throws ZtatException {
        // Arrange - Use lenient stubbing with explicit type matchers for varargs methods
        org.mockito.Mockito.lenient().doThrow(new RuntimeException("JIRA not configured")).when(zeroTrustClientService)
            .callGetOnApi(any(TokenDTO.class), any(String.class), any(java.util.Map.Entry.class));

        // Act
        Boolean result = jiraVerbs.isJiraAvailable(token, contextDTO);
        
        // Assert
        assertFalse(result);
    }

    @Test
    void testExecuteFromJiraTicket_Success() throws ZtatException, JsonProcessingException {
        // Arrange
        String issueKey = "TEST-123";
        Long hostSystemId = 1L;
        contextDTO.getExecutionArgs().put("issueKey", JsonUtil.MAPPER.convertValue(issueKey, com.fasterxml.jackson.databind.JsonNode.class));
        contextDTO.getExecutionArgs().put("hostSystemId", JsonUtil.MAPPER.convertValue(hostSystemId, com.fasterxml.jackson.databind.JsonNode.class));
        
        TicketDTO ticket = TicketDTO.builder()
            .id(issueKey)
            .summary("Execute maintenance command")
            .description("command: systemctl restart nginx")
            .build();
        
        String ticketJsonResponse = JsonUtil.MAPPER.writeValueAsString(ticket);
        
        // Use lenient stubbing with explicit type matchers for varargs methods
        org.mockito.Mockito.lenient().doReturn(ticketJsonResponse).when(zeroTrustClientService)
            .callGetOnApi(any(TokenDTO.class), any(String.class), any(java.util.Map.Entry.class));

        org.mockito.Mockito.lenient().doReturn("Success").when(zeroTrustClientService)
            .callPostOnApi(any(TokenDTO.class), anyString(), any(ObjectNode.class), any(java.util.Map.Entry.class));

        // Act
        ObjectNode result = jiraVerbs.executeFromJiraTicket(token, contextDTO);
        
        // Assert
        assertNotNull(result);
        assertEquals(issueKey, result.get("issueKey").asText());
        assertEquals(hostSystemId, result.get("hostSystemId").asLong());
        assertEquals("systemctl restart nginx", result.get("command").asText());
        assertEquals("ready_to_execute", result.get("status").asText());
    }

    @Test
    void testExecuteFromJiraTicket_NoCommandFound() throws ZtatException, JsonProcessingException {
        // Arrange
        String issueKey = "TEST-123";
        Long hostSystemId = 1L;
        contextDTO.getExecutionArgs().put("issueKey", JsonUtil.MAPPER.convertValue(issueKey, com.fasterxml.jackson.databind.JsonNode.class));
        contextDTO.getExecutionArgs().put("hostSystemId", JsonUtil.MAPPER.convertValue(hostSystemId, com.fasterxml.jackson.databind.JsonNode.class));
        
        TicketDTO ticket = TicketDTO.builder()
            .id(issueKey)
            .summary("Test ticket")
            .description("No command here")
            .build();
        
        String ticketJsonResponse = JsonUtil.MAPPER.writeValueAsString(ticket);
        
        // Use lenient stubbing with explicit type matchers for varargs methods
        org.mockito.Mockito.lenient().doReturn(ticketJsonResponse).when(zeroTrustClientService)
            .callGetOnApi(any(TokenDTO.class), any(String.class), any(java.util.Map.Entry.class));

        // Act & Assert
        assertThrows(RuntimeException.class, () -> {
            jiraVerbs.executeFromJiraTicket(token, contextDTO);
        });
    }
}
