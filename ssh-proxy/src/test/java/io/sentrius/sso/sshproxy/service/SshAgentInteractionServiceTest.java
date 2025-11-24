package io.sentrius.sso.sshproxy.service;

import io.sentrius.sso.core.dto.UserDTO;
import io.sentrius.sso.core.dto.agents.AgentExecution;
import io.sentrius.sso.core.model.chat.ChatLog;
import io.sentrius.sso.core.model.sessions.SessionLog;
import io.sentrius.sso.core.model.users.User;
import io.sentrius.sso.core.services.ChatService;
import io.sentrius.sso.core.services.agents.AgentExecutionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class SshAgentInteractionServiceTest {

    @Mock
    private ChatService chatService;

    @Mock
    private AgentExecutionService agentExecutionService;

    @Mock
    private InlineTerminalResponseService terminalResponseService;

    @InjectMocks
    private SshAgentInteractionService agentInteractionService;

    private User testUser;
    private SessionLog testSession;
    private ByteArrayOutputStream terminalOutput;
    private AgentExecution agentExecution;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
            .id(1L)
            .username("testuser")
            .emailAddress("test@example.com")
            .build();

        testSession = mock(SessionLog.class);
        lenient().when(testSession.getId()).thenReturn(100L);

        terminalOutput = new ByteArrayOutputStream();

        agentExecution = AgentExecution.builder()
            .executionId("test-execution-id")
            .build();
    }

    @Test
    void testProcessAgentQuery_Success() throws IOException {
        // Arrange
        String query = "How do I list files?";
        when(agentExecutionService.getAgentExecution(any(UserDTO.class)))
            .thenReturn(agentExecution);
        doNothing().when(chatService).save(any(ChatLog.class));
        doNothing().when(terminalResponseService).sendMessage(anyString(), eq(terminalOutput));

        // Act
        boolean result = agentInteractionService.processAgentQuery(testUser, testSession, query, terminalOutput);

        // Assert
        assertTrue(result);
        verify(agentExecutionService).getAgentExecution(any(UserDTO.class));
        verify(chatService, times(2)).save(any(ChatLog.class));
        verify(terminalResponseService, atLeastOnce()).sendMessage(anyString(), eq(terminalOutput));
    }

    @Test
    void testProcessAgentQuery_SavesUserMessage() {
        // Arrange
        String query = "What is chmod?";
        when(agentExecutionService.getAgentExecution(any(UserDTO.class)))
            .thenReturn(agentExecution);
        ArgumentCaptor<ChatLog> chatLogCaptor = ArgumentCaptor.forClass(ChatLog.class);

        // Act
        agentInteractionService.processAgentQuery(testUser, testSession, query, terminalOutput);

        // Assert
        verify(chatService, times(2)).save(chatLogCaptor.capture());
        ChatLog userMessage = chatLogCaptor.getAllValues().get(0);
        assertEquals(testUser.getUsername(), userMessage.getSender());
        assertEquals(query, userMessage.getMessage());
        assertEquals(testSession, userMessage.getSession());
        assertEquals("test-execution-id", userMessage.getChatGroupId());
    }

    @Test
    void testProcessAgentQuery_SavesAgentResponse() {
        // Arrange
        String query = "What is sudo?";
        when(agentExecutionService.getAgentExecution(any(UserDTO.class)))
            .thenReturn(agentExecution);
        ArgumentCaptor<ChatLog> chatLogCaptor = ArgumentCaptor.forClass(ChatLog.class);

        // Act
        agentInteractionService.processAgentQuery(testUser, testSession, query, terminalOutput);

        // Assert
        verify(chatService, times(2)).save(chatLogCaptor.capture());
        ChatLog agentMessage = chatLogCaptor.getAllValues().get(1);
        assertEquals("agent", agentMessage.getSender());
        assertNotNull(agentMessage.getMessage());
        assertTrue(agentMessage.getMessage().contains(query));
        assertEquals(testSession, agentMessage.getSession());
        assertEquals("test-execution-id", agentMessage.getChatGroupId());
    }

    @Test
    void testProcessAgentQuery_HandlesException() throws IOException {
        // Arrange
        String query = "test query";
        when(agentExecutionService.getAgentExecution(any(UserDTO.class)))
            .thenThrow(new RuntimeException("Service error"));
        doNothing().when(terminalResponseService).sendMessage(anyString(), eq(terminalOutput));

        // Act
        boolean result = agentInteractionService.processAgentQuery(testUser, testSession, query, terminalOutput);

        // Assert
        assertFalse(result);
        verify(terminalResponseService).sendMessage(contains("Error"), eq(terminalOutput));
    }

    @Test
    void testIsAgentCommand_WithAtAgentPrefix() {
        assertTrue(agentInteractionService.isAgentCommand("@agent How do I use grep?"));
        assertTrue(agentInteractionService.isAgentCommand("@agent test"));
        assertTrue(agentInteractionService.isAgentCommand("@agent"));
    }

    @Test
    void testIsAgentCommand_WithAskPrefix() {
        assertTrue(agentInteractionService.isAgentCommand("/ask What is SSH?"));
        assertTrue(agentInteractionService.isAgentCommand("/ask test"));
        assertTrue(agentInteractionService.isAgentCommand("/ask"));
    }

    @Test
    void testIsAgentCommand_WithWhitespace() {
        assertTrue(agentInteractionService.isAgentCommand("  @agent test  "));
        assertTrue(agentInteractionService.isAgentCommand("  /ask test  "));
    }

    @Test
    void testIsAgentCommand_NotAgentCommand() {
        assertFalse(agentInteractionService.isAgentCommand("ls -la"));
        assertFalse(agentInteractionService.isAgentCommand("sudo apt update"));
        assertFalse(agentInteractionService.isAgentCommand("@agent_test"));
        assertFalse(agentInteractionService.isAgentCommand("/asksomething"));
        assertFalse(agentInteractionService.isAgentCommand(""));
        assertFalse(agentInteractionService.isAgentCommand(null));
    }

    @Test
    void testExtractQuery_FromAtAgent() {
        assertEquals("How do I use grep?", 
            agentInteractionService.extractQuery("@agent How do I use grep?"));
        assertEquals("test", 
            agentInteractionService.extractQuery("@agent test"));
        assertEquals("", 
            agentInteractionService.extractQuery("@agent"));
    }

    @Test
    void testExtractQuery_FromAsk() {
        assertEquals("What is SSH?", 
            agentInteractionService.extractQuery("/ask What is SSH?"));
        assertEquals("test", 
            agentInteractionService.extractQuery("/ask test"));
        assertEquals("", 
            agentInteractionService.extractQuery("/ask"));
    }

    @Test
    void testExtractQuery_WithWhitespace() {
        assertEquals("test query", 
            agentInteractionService.extractQuery("  @agent test query  "));
        assertEquals("test query", 
            agentInteractionService.extractQuery("  /ask test query  "));
    }

    @Test
    void testExtractQuery_NonAgentCommand() {
        assertEquals("", 
            agentInteractionService.extractQuery("ls -la"));
        assertEquals("", 
            agentInteractionService.extractQuery(""));
    }

    @Test
    void testSendAgentHelp() throws IOException {
        // Arrange
        doNothing().when(terminalResponseService).sendMessage(anyString(), eq(terminalOutput));

        // Act
        agentInteractionService.sendAgentHelp(terminalOutput);

        // Assert
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(terminalResponseService).sendMessage(messageCaptor.capture(), eq(terminalOutput));
        String helpMessage = messageCaptor.getValue();
        assertTrue(helpMessage.contains("AGENT ASSISTANCE"));
        assertTrue(helpMessage.contains("@agent"));
        assertTrue(helpMessage.contains("/ask"));
        assertTrue(helpMessage.contains("Examples"));
    }

    @Test
    void testProcessAgentQuery_CreatesCorrectUserDTO() {
        // Arrange
        String query = "test";
        ArgumentCaptor<UserDTO> userDtoCaptor = ArgumentCaptor.forClass(UserDTO.class);
        when(agentExecutionService.getAgentExecution(userDtoCaptor.capture()))
            .thenReturn(agentExecution);

        // Act
        agentInteractionService.processAgentQuery(testUser, testSession, query, terminalOutput);

        // Assert
        UserDTO capturedDto = userDtoCaptor.getValue();
        assertEquals(testUser.getId(), capturedDto.id);
        assertEquals(testUser.getUsername(), capturedDto.username);
        assertEquals(testUser.getEmailAddress(), capturedDto.emailAddress);
    }

    @Test
    void testProcessAgentQuery_HandlesNullEmail() {
        // Arrange
        User userWithoutEmail = User.builder()
            .id(2L)
            .username("testuser2")
            .emailAddress(null)
            .build();
        String query = "test";
        ArgumentCaptor<UserDTO> userDtoCaptor = ArgumentCaptor.forClass(UserDTO.class);
        when(agentExecutionService.getAgentExecution(userDtoCaptor.capture()))
            .thenReturn(agentExecution);

        // Act
        agentInteractionService.processAgentQuery(userWithoutEmail, testSession, query, terminalOutput);

        // Assert
        UserDTO capturedDto = userDtoCaptor.getValue();
        assertEquals("", capturedDto.emailAddress);
    }
}
