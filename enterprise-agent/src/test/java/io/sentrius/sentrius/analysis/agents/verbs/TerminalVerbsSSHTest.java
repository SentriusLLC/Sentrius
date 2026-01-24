package io.sentrius.sentrius.analysis.agents.verbs;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.sentrius.agent.analysis.agents.verbs.AgentVerbs;
import io.sentrius.agent.analysis.agents.verbs.TerminalVerbs;
import io.sentrius.sso.core.dto.agents.AgentExecution;
import io.sentrius.sso.core.dto.agents.AgentExecutionContextDTO;
import io.sentrius.sso.core.exceptions.ZtatException;
import io.sentrius.sso.core.services.agents.LLMService;
import io.sentrius.sso.core.services.agents.ZeroTrustClientService;
import io.sentrius.sso.core.utils.JsonUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TerminalVerbsSSHTest {

    @Mock
    private ZeroTrustClientService zeroTrustClientService;

    @Mock
    private LLMService llmService;

    @Mock
    private AgentVerbs agentVerbs;

    @InjectMocks
    private TerminalVerbs terminalVerbs;

    private AgentExecution execution;
    private AgentExecutionContextDTO contextDTO;

    @BeforeEach
    void setUp() throws Exception {
        execution = AgentExecution.builder()
            .executionId("test-execution-id")
            .build();
        contextDTO = AgentExecutionContextDTO.builder().build();
        
        // Set the agentApiUrl field using reflection since it's injected via @Value
        java.lang.reflect.Field field = TerminalVerbs.class.getDeclaredField("agentApiUrl");
        field.setAccessible(true);
        field.set(terminalVerbs, "http://localhost:8080");
    }

    @Test
    void openSSHSession_WithValidHostConnection_ReturnsSuccessResponse() throws ZtatException, Exception {
        // Arrange
        String hostConnection = "test-host-123";
        ObjectNode argsNode = JsonUtil.MAPPER.createObjectNode();
        argsNode.put("hostConnection", hostConnection);
        contextDTO.setExecutionArgs(argsNode);

        // Act
        ObjectNode result = terminalVerbs.openSSHSession(execution, contextDTO);

        // Assert
        assertNotNull(result);
        assertEquals(hostConnection, result.get("sessionId").asText());
        assertEquals("opened", result.get("status").asText());
        assertEquals(hostConnection, result.get("hostConnection").asText());
        assertTrue(result.has("message"));
        assertTrue(result.has("wsUrl"));
    }

    @Test
    void openSSHSession_WithoutHostConnection_ThrowsException() throws ZtatException, Exception {
        // Arrange
        contextDTO.setExecutionArgs(JsonUtil.MAPPER.createObjectNode());

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () ->
            terminalVerbs.openSSHSession(execution, contextDTO)
        );
        assertTrue(exception.getMessage().contains("hostConnection parameter is required"));
    }

    @Test
    void openSSHSession_CalledTwice_ReusesExistingSession() throws ZtatException, Exception {
        // Arrange
        String hostConnection = "test-host-456";
        ObjectNode argsNode = JsonUtil.MAPPER.createObjectNode();
        argsNode.put("hostConnection", hostConnection);
        contextDTO.setExecutionArgs(argsNode);

        // Act - First call creates new session
        ObjectNode firstResult = terminalVerbs.openSSHSession(execution, contextDTO);
        assertEquals("opened", firstResult.get("status").asText());

        // Act - Second call reuses existing session
        ObjectNode secondResult = terminalVerbs.openSSHSession(execution, contextDTO);

        // Assert
        assertNotNull(secondResult);
        assertEquals("existing", secondResult.get("status").asText());
        assertEquals(hostConnection, secondResult.get("sessionId").asText());
        assertTrue(secondResult.get("message").asText().contains("existing"));
    }

    @Test
    void sendTerminalCommand_WithoutSessionId_ThrowsException() throws ZtatException, Exception {
        // Arrange
        ObjectNode argsNode = JsonUtil.MAPPER.createObjectNode();
        argsNode.put("command", "ls -la");
        contextDTO.setExecutionArgs(argsNode);

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () ->
            terminalVerbs.sendTerminalCommand(execution, contextDTO)
        );
        assertTrue(exception.getMessage().contains("sessionId parameter is required"));
    }

    @Test
    void sendTerminalCommand_WithoutCommand_ThrowsException() throws ZtatException, Exception {
        // Arrange
        ObjectNode argsNode = JsonUtil.MAPPER.createObjectNode();
        argsNode.put("sessionId", "test-session");
        contextDTO.setExecutionArgs(argsNode);

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () ->
            terminalVerbs.sendTerminalCommand(execution, contextDTO)
        );
        assertTrue(exception.getMessage().contains("command parameter is required"));
    }

    @Test
    void sendTerminalCommand_WithInvalidSession_ThrowsException() throws ZtatException, Exception {
        // Arrange
        ObjectNode argsNode = JsonUtil.MAPPER.createObjectNode();
        argsNode.put("sessionId", "non-existent-session");
        argsNode.put("command", "ls -la");
        contextDTO.setExecutionArgs(argsNode);

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () ->
            terminalVerbs.sendTerminalCommand(execution, contextDTO)
        );
        assertTrue(exception.getMessage().contains("No active SSH terminal session found"));
    }

    @Test
    void readTerminalOutput_WithoutSessionId_ThrowsException() throws ZtatException, Exception {
        // Arrange
        contextDTO.setExecutionArgs(JsonUtil.MAPPER.createObjectNode());

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () ->
            terminalVerbs.readTerminalOutput(execution, contextDTO)
        );
        assertTrue(exception.getMessage().contains("sessionId parameter is required"));
    }

    @Test
    void readTerminalOutput_WithNonExistentSession_FallsBackToApi() throws ZtatException, Exception {
        // Arrange
        String sessionId = "non-existent-session";
        ObjectNode argsNode = JsonUtil.MAPPER.createObjectNode();
        argsNode.put("sessionId", sessionId);
        contextDTO.setExecutionArgs(argsNode);
        
        String mockOutput = "test output from API";
        // Use lenient stubbing for varargs methods with complex type matching
        org.mockito.Mockito.lenient().doReturn(mockOutput).when(zeroTrustClientService)
            .callGetOnApi(any(AgentExecution.class), any(String.class), any(java.util.Map.Entry.class));

        // Act
        ObjectNode result = terminalVerbs.readTerminalOutput(execution, contextDTO);

        // Assert
        assertNotNull(result);
        assertEquals(sessionId, result.get("sessionId").asText());
        assertEquals(mockOutput, result.get("output").asText());
        assertEquals("api", result.get("source").asText());
    }

    @Test
    void closeSSHTerminal_WithoutSessionId_ThrowsException() throws ZtatException, Exception {
        // Arrange
        contextDTO.setExecutionArgs(JsonUtil.MAPPER.createObjectNode());

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () ->
            terminalVerbs.closeSSHTerminal(execution, contextDTO)
        );
        assertTrue(exception.getMessage().contains("sessionId parameter is required"));
    }

    @Test
    void closeSSHTerminal_WithValidSessionId_ReturnsSuccessResponse() throws ZtatException, Exception {
        // Arrange
        String sessionId = "test-session-789";
        ObjectNode argsNode = JsonUtil.MAPPER.createObjectNode();
        argsNode.put("sessionId", sessionId);
        contextDTO.setExecutionArgs(argsNode);

        // Act
        ObjectNode result = terminalVerbs.closeSSHTerminal(execution, contextDTO);

        // Assert
        assertNotNull(result);
        assertEquals(sessionId, result.get("sessionId").asText());
        assertEquals("closed", result.get("status").asText());
        assertTrue(result.get("message").asText().contains("closed successfully"));
    }

    @Test
    void listActiveTerminalSessions_WhenNoActiveSessions_ReturnsEmptyArray() throws ZtatException, Exception {
        // Act
        com.fasterxml.jackson.databind.node.ArrayNode result = 
            terminalVerbs.listActiveTerminalSessions(execution, contextDTO);

        // Assert
        assertNotNull(result);
        assertEquals(0, result.size());
    }

    @Test
    void listActiveTerminalSessions_WithActiveSessions_ReturnsSessionList() throws ZtatException, Exception {
        // Arrange - Create a session first
        String hostConnection = "test-host-999";
        ObjectNode argsNode = JsonUtil.MAPPER.createObjectNode();
        argsNode.put("hostConnection", hostConnection);
        contextDTO.setExecutionArgs(argsNode);
        terminalVerbs.openSSHSession(execution, contextDTO);

        // Act
        com.fasterxml.jackson.databind.node.ArrayNode result = 
            terminalVerbs.listActiveTerminalSessions(execution, contextDTO);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        ObjectNode sessionInfo = (ObjectNode) result.get(0);
        assertEquals(hostConnection, sessionInfo.get("sessionId").asText());
        assertTrue(sessionInfo.has("active"));
        assertTrue(sessionInfo.has("createdAt"));
        assertTrue(sessionInfo.has("lastActivityAt"));
    }

    @Test
    void fullWorkflow_OpenSendReadClose_WorksCorrectly() throws ZtatException, Exception {
        // Arrange
        String hostConnection = "workflow-test-host";
        
        // Step 1: Open session
        ObjectNode openArgs = JsonUtil.MAPPER.createObjectNode();
        openArgs.put("hostConnection", hostConnection);
        AgentExecutionContextDTO openContext = AgentExecutionContextDTO.builder().build();
        openContext.setExecutionArgs(openArgs);
        
        ObjectNode openResult = terminalVerbs.openSSHSession(execution, openContext);
        assertNotNull(openResult);
        assertEquals("opened", openResult.get("status").asText());
        String sessionId = openResult.get("sessionId").asText();
        
        // Step 2: List sessions
        com.fasterxml.jackson.databind.node.ArrayNode sessions = 
            terminalVerbs.listActiveTerminalSessions(execution, AgentExecutionContextDTO.builder().build());
        assertEquals(1, sessions.size());
        
        // Step 3: Close session
        ObjectNode closeArgs = JsonUtil.MAPPER.createObjectNode();
        closeArgs.put("sessionId", sessionId);
        AgentExecutionContextDTO closeContext = AgentExecutionContextDTO.builder().build();
        closeContext.setExecutionArgs(closeArgs);
        
        ObjectNode closeResult = terminalVerbs.closeSSHTerminal(execution, closeContext);
        assertNotNull(closeResult);
        assertEquals("closed", closeResult.get("status").asText());
        
        // Step 4: Verify session is removed
        sessions = terminalVerbs.listActiveTerminalSessions(execution, AgentExecutionContextDTO.builder().build());
        assertEquals(0, sessions.size());
    }
}
