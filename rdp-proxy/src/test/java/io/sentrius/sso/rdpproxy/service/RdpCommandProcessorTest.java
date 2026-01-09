package io.sentrius.sso.rdpproxy.service;

import io.sentrius.sso.core.config.SystemOptions;
import io.sentrius.sso.core.model.ConnectedSystem;
import io.sentrius.sso.core.model.sessions.SessionLog;
import io.sentrius.sso.core.services.agents.AgentService;
import io.sentrius.sso.core.services.security.ZeroTrustAccessTokenService;
import io.sentrius.sso.core.services.terminal.SessionTrackingService;
import io.sentrius.sso.services.WebTerminalAISupportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class RdpCommandProcessorTest {

    @Mock
    private SessionTrackingService sessionTrackingService;

    @Mock
    private RdpTerminalResponseService terminalResponseService;

    @Mock
    private AgentService agentService;

    @Mock
    private ZeroTrustAccessTokenService zeroTrustAccessTokenService;

    @Mock
    private WebTerminalAISupportService webTerminalAISupportService;

    @Mock
    private SystemOptions systemOptions;

    @Mock
    private ConnectedSystem connectedSystem;

    @Mock
    private SessionLog sessionLog;

    private RdpCommandProcessor rdpCommandProcessor;

    @BeforeEach
    void setUp() {
        rdpCommandProcessor = new RdpCommandProcessor(
            sessionTrackingService, 
            terminalResponseService, 
            agentService,
            zeroTrustAccessTokenService,
            systemOptions,
            webTerminalAISupportService
        );
        
        // Mock the session to avoid null pointer exceptions - use lenient to avoid unnecessary stubbing errors
        lenient().when(connectedSystem.getSession()).thenReturn(sessionLog);
        lenient().when(sessionLog.getId()).thenReturn(123L);
    }

    @Test
    void testProcessRdpAction_AllowedAction() throws Exception {
        // Arrange
        RdpCommandProcessor.RdpAction action = new RdpCommandProcessor.RdpAction(
            RdpCommandProcessor.RdpAction.RdpActionType.SCREEN_CAPTURE,
            "display",
            "Normal screen capture"
        );
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        // Act
        boolean result = rdpCommandProcessor.processRdpAction(connectedSystem, action, output);

        // Assert
        assertTrue(result);
    }

    @Test
    void testProcessRdpAction_DangerousFileDelete() throws Exception {
        // Arrange
        RdpCommandProcessor.RdpAction action = new RdpCommandProcessor.RdpAction(
            RdpCommandProcessor.RdpAction.RdpActionType.FILE_DELETE,
            "C:\\Windows\\system32\\important.dll",
            "Attempting to delete system file"
        );
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        // Act
        boolean result = rdpCommandProcessor.processRdpAction(connectedSystem, action, output);

        // Assert
        assertFalse(result);
        verify(terminalResponseService).sendTriggerResponse(any(), eq(output));
    }

    @Test
    void testProcessRdpAction_WarningFileTransfer() throws Exception {
        // Arrange
        RdpCommandProcessor.RdpAction action = new RdpCommandProcessor.RdpAction(
            RdpCommandProcessor.RdpAction.RdpActionType.FILE_COPY_OUT,
            "sensitive_document.pdf",
            "File transfer out of system"
        );
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        // Act
        boolean result = rdpCommandProcessor.processRdpAction(connectedSystem, action, output);

        // Assert
        assertTrue(result); // Allowed but with warning
        verify(terminalResponseService).sendTriggerResponse(any(), eq(output));
    }

    @Test
    void testProcessRdpAction_HighPrivilegeAction() throws Exception {
        // Arrange
        RdpCommandProcessor.RdpAction action = new RdpCommandProcessor.RdpAction(
            RdpCommandProcessor.RdpAction.RdpActionType.ADMIN_ACCESS,
            "administrator",
            "Administrative access requested"
        );
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        // Act
        boolean result = rdpCommandProcessor.processRdpAction(connectedSystem, action, output);

        // Assert
        assertTrue(result); // Allowed but recorded
        verify(terminalResponseService).sendTriggerResponse(any(), eq(output));
    }

    @Test
    void testProcessInputEvent_KeyboardInput() throws Exception {
        // Arrange
        RdpCommandProcessor.RdpInputEvent event = new RdpCommandProcessor.RdpInputEvent(
            RdpCommandProcessor.RdpInputEvent.RdpInputType.KEYBOARD,
            "normal typing"
        );
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        // Act
        boolean result = rdpCommandProcessor.processInputEvent(connectedSystem, event, output);

        // Assert
        assertTrue(result);
    }

    @Test
    void testProcessInputEvent_DangerousKeyboardCombination() throws Exception {
        // Arrange
        RdpCommandProcessor.RdpInputEvent event = new RdpCommandProcessor.RdpInputEvent(
            RdpCommandProcessor.RdpInputEvent.RdpInputType.KEYBOARD,
            "ctrl+alt+del"
        );
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        // Act
        boolean result = rdpCommandProcessor.processInputEvent(connectedSystem, event, output);

        // Assert
        assertFalse(result); // Should be blocked
        verify(terminalResponseService).sendMessage(any(), eq(output));
    }

    @Test
    void testProcessInputEvent_SuspiciousMouseClicking() throws Exception {
        // Arrange
        RdpCommandProcessor.RdpInputEvent event = new RdpCommandProcessor.RdpInputEvent(
            RdpCommandProcessor.RdpInputEvent.RdpInputType.MOUSE_CLICK,
            "left:100:200"
        );
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        // Act
        boolean result = rdpCommandProcessor.processInputEvent(connectedSystem, event, output);

        // Assert
        assertTrue(result); // Normal clicks should be allowed
    }

    @Test
    void testProcessInputEvent_MouseMovement() throws Exception {
        // Arrange
        RdpCommandProcessor.RdpInputEvent event = new RdpCommandProcessor.RdpInputEvent(
            RdpCommandProcessor.RdpInputEvent.RdpInputType.MOUSE_MOVE,
            "150:250"
        );
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        // Act
        boolean result = rdpCommandProcessor.processInputEvent(connectedSystem, event, output);

        // Assert
        assertTrue(result); // Normal movement should be allowed
    }

    @Test
    void testProcessInputEvent_ExcessiveScrolling() throws Exception {
        // Arrange
        RdpCommandProcessor.RdpInputEvent event = new RdpCommandProcessor.RdpInputEvent(
            RdpCommandProcessor.RdpInputEvent.RdpInputType.SCROLL,
            "2000" // Excessive scroll delta
        );
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        // Act
        boolean result = rdpCommandProcessor.processInputEvent(connectedSystem, event, output);

        // Assert
        assertTrue(result); // Allow but with warning
        verify(terminalResponseService).sendMessage(any(), eq(output));
    }

    @Test
    void testProcessInputEvent_PasswordTyping() throws Exception {
        // Arrange
        RdpCommandProcessor.RdpInputEvent event = new RdpCommandProcessor.RdpInputEvent(
            RdpCommandProcessor.RdpInputEvent.RdpInputType.KEYBOARD,
            "password123"
        );
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        // Act
        boolean result = rdpCommandProcessor.processInputEvent(connectedSystem, event, output);

        // Assert
        // Note: Without an AccessTokenAuditor registered, the processor returns early
        // In real usage, the AccessTokenAuditor would be registered with rules
        assertTrue(result); // Allow by default when no auditor is registered
    }

    @Test
    void testInputEventAnalysis_Creation() {
        // Arrange
        RdpCommandProcessor.RdpInputEvent event = new RdpCommandProcessor.RdpInputEvent(
            RdpCommandProcessor.RdpInputEvent.RdpInputType.KEYBOARD,
            "test input"
        );

        // Act
        RdpCommandProcessor.InputEventAnalysis analysis = new RdpCommandProcessor.InputEventAnalysis(event);

        // Assert
        assertNotNull(analysis);
        assertEquals(event, analysis.getOriginalEvent());
        assertFalse(analysis.isSuspicious());
        assertEquals("Input event: KEYBOARD", analysis.getDescription());
    }

    @Test
    void testInputBehaviorRecord_Builder() {
        // Arrange
        RdpCommandProcessor.RdpInputEvent event = new RdpCommandProcessor.RdpInputEvent(
            RdpCommandProcessor.RdpInputEvent.RdpInputType.MOUSE_CLICK,
            "left:50:75"
        );
        RdpCommandProcessor.InputEventAnalysis analysis = new RdpCommandProcessor.InputEventAnalysis(event);

        // Act
        RdpCommandProcessor.InputBehaviorRecord record = RdpCommandProcessor.InputBehaviorRecord.builder()
            .sessionId(123L)
            .timestamp(System.currentTimeMillis())
            .inputType("MOUSE_CLICK")
            .inputData("left:50:75")
            .analysis(analysis)
            .suspicious(false)
            .containsSensitiveData(false)
            .containsCommands(false)
            .build();

        // Assert
        assertNotNull(record);
        assertEquals(Long.valueOf(123L), record.getSessionId());
        assertEquals("MOUSE_CLICK", record.getInputType());
        assertEquals("left:50:75", record.getInputData());
        assertFalse(record.isSuspicious());
    }

    @Test
    void testAgentInputNotification_Builder() {
        // Arrange
        RdpCommandProcessor.RdpInputEvent event = new RdpCommandProcessor.RdpInputEvent(
            RdpCommandProcessor.RdpInputEvent.RdpInputType.KEYBOARD,
            "suspicious input"
        );
        RdpCommandProcessor.InputEventAnalysis analysis = new RdpCommandProcessor.InputEventAnalysis(event);
        analysis.setSuspicious(true);

        // Act
        RdpCommandProcessor.AgentInputNotification notification = RdpCommandProcessor.AgentInputNotification.builder()
            .sessionId(456L)
            .userId(789L)
            .inputType("KEYBOARD")
            .inputData("suspicious input")
            .analysis(analysis)
            .timestamp(System.currentTimeMillis())
            .requiresMultimodalAnalysis(true)
            .build();

        // Assert
        assertNotNull(notification);
        assertEquals(Long.valueOf(456L), notification.getSessionId());
        assertEquals(Long.valueOf(789L), notification.getUserId());
        assertTrue(notification.isRequiresMultimodalAnalysis());
    }

    @Test
    void testRdpActionTypes() {
        // Test that all action types are properly defined
        RdpCommandProcessor.RdpAction.RdpActionType[] types = 
            RdpCommandProcessor.RdpAction.RdpActionType.values();
        
        assertTrue(types.length > 0);
        
        // Test creating actions with different types
        for (RdpCommandProcessor.RdpAction.RdpActionType type : types) {
            RdpCommandProcessor.RdpAction action = new RdpCommandProcessor.RdpAction(
                type, "test-target", "test description"
            );
            assertEquals(type, action.getType());
            assertEquals("test-target", action.getTarget());
            assertEquals("test description", action.getDescription());
        }
    }

    @Test
    void testRdpInputEventTypes() {
        // Test that all input event types are properly defined
        RdpCommandProcessor.RdpInputEvent.RdpInputType[] types = 
            RdpCommandProcessor.RdpInputEvent.RdpInputType.values();
        
        assertTrue(types.length > 0);
        
        // Test creating events with different types
        for (RdpCommandProcessor.RdpInputEvent.RdpInputType type : types) {
            RdpCommandProcessor.RdpInputEvent event = new RdpCommandProcessor.RdpInputEvent(
                type, "test-data"
            );
            assertEquals(type, event.getType());
            assertEquals("test-data", event.getData());
        }
    }
}