package io.sentrius.sso.sshproxy.service;

import io.sentrius.sso.automation.auditing.Trigger;
import io.sentrius.sso.automation.auditing.TriggerAction;
import io.sentrius.sso.core.model.ConnectedSystem;
import io.sentrius.sso.core.services.terminal.SessionTrackingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SshCommandProcessorTest {

    @Mock
    private SessionTrackingService sessionTrackingService;

    @Mock
    private InlineTerminalResponseService terminalResponseService;

    @Mock
    private ConnectedSystem connectedSystem;

    @InjectMocks
    private SshCommandProcessor sshCommandProcessor;

    private ByteArrayOutputStream terminalOutput;

    @BeforeEach
    void setUp() {
        terminalOutput = new ByteArrayOutputStream();
    }

    @Test
    void testProcessCommand_AllowedCommand() {
        String command = "ls -la";

        boolean result = sshCommandProcessor.processCommand(connectedSystem, command, terminalOutput);

        assertTrue(result);
        verifyNoInteractions(terminalResponseService);
    }

    @Test
    void testProcessCommand_DangerousCommand_RmRf() throws IOException {
        String command = "rm -rf /";
        doNothing().when(terminalResponseService).sendTriggerResponse(any(Trigger.class), any());

        boolean result = sshCommandProcessor.processCommand(connectedSystem, command, terminalOutput);

        assertFalse(result);
        verify(terminalResponseService).sendTriggerResponse(any(Trigger.class), eq(terminalOutput));
    }

    @Test
    void testProcessCommand_DangerousCommand_DdIf() throws IOException {
        String command = "dd if=/dev/zero of=/dev/sda";
        doNothing().when(terminalResponseService).sendTriggerResponse(any(Trigger.class), any());

        boolean result = sshCommandProcessor.processCommand(connectedSystem, command, terminalOutput);

        assertFalse(result);
        verify(terminalResponseService).sendTriggerResponse(any(Trigger.class), eq(terminalOutput));
    }

    @Test
    void testProcessCommand_DangerousCommand_Format() throws IOException {
        String command = "format c:";
        doNothing().when(terminalResponseService).sendTriggerResponse(any(Trigger.class), any());

        boolean result = sshCommandProcessor.processCommand(connectedSystem, command, terminalOutput);

        assertFalse(result);
        verify(terminalResponseService).sendTriggerResponse(any(Trigger.class), eq(terminalOutput));
    }

    @Test
    void testProcessCommand_DangerousCommand_SudoRm() throws IOException {
        String command = "sudo rm -rf /home";
        doNothing().when(terminalResponseService).sendTriggerResponse(any(Trigger.class), any());

        boolean result = sshCommandProcessor.processCommand(connectedSystem, command, terminalOutput);

        assertFalse(result);
        verify(terminalResponseService).sendTriggerResponse(any(Trigger.class), eq(terminalOutput));
    }

    @Test
    void testProcessCommand_DangerousCommand_Shutdown() throws IOException {
        String command = "shutdown -h now";
        doNothing().when(terminalResponseService).sendTriggerResponse(any(Trigger.class), any());

        boolean result = sshCommandProcessor.processCommand(connectedSystem, command, terminalOutput);

        assertFalse(result);
        verify(terminalResponseService).sendTriggerResponse(any(Trigger.class), eq(terminalOutput));
    }

    @Test
    void testProcessCommand_DangerousCommand_Reboot() throws IOException {
        String command = "reboot";
        doNothing().when(terminalResponseService).sendTriggerResponse(any(Trigger.class), any());

        boolean result = sshCommandProcessor.processCommand(connectedSystem, command, terminalOutput);

        assertFalse(result);
        verify(terminalResponseService).sendTriggerResponse(any(Trigger.class), eq(terminalOutput));
    }

    @Test
    void testProcessCommand_WarningCommand_Sudo() throws IOException {
        String command = "sudo apt update";
        doNothing().when(terminalResponseService).sendTriggerResponse(any(Trigger.class), any());

        boolean result = sshCommandProcessor.processCommand(connectedSystem, command, terminalOutput);

        assertTrue(result); // Allow but warn
        verify(terminalResponseService).sendTriggerResponse(any(Trigger.class), eq(terminalOutput));
    }

    @Test
    void testProcessCommand_WarningCommand_Su() throws IOException {
        String command = "su - root";
        doNothing().when(terminalResponseService).sendTriggerResponse(any(Trigger.class), any());

        boolean result = sshCommandProcessor.processCommand(connectedSystem, command, terminalOutput);

        assertTrue(result); // Allow but warn
        verify(terminalResponseService).sendTriggerResponse(any(Trigger.class), eq(terminalOutput));
    }

    @Test
    void testProcessCommand_WarningCommand_Passwd() throws IOException {
        String command = "passwd user1";
        doNothing().when(terminalResponseService).sendTriggerResponse(any(Trigger.class), any());

        boolean result = sshCommandProcessor.processCommand(connectedSystem, command, terminalOutput);

        assertTrue(result); // Allow but warn
        verify(terminalResponseService).sendTriggerResponse(any(Trigger.class), eq(terminalOutput));
    }

    @Test
    void testProcessCommand_WarningCommand_Chmod777() throws IOException {
        String command = "chmod 777 /etc/passwd";
        doNothing().when(terminalResponseService).sendTriggerResponse(any(Trigger.class), any());

        boolean result = sshCommandProcessor.processCommand(connectedSystem, command, terminalOutput);

        assertTrue(result); // Allow but warn
        verify(terminalResponseService).sendTriggerResponse(any(Trigger.class), eq(terminalOutput));
    }

    @Test
    void testProcessCommand_WarningCommand_Chown() throws IOException {
        String command = "chown user:group file.txt";
        doNothing().when(terminalResponseService).sendTriggerResponse(any(Trigger.class), any());

        boolean result = sshCommandProcessor.processCommand(connectedSystem, command, terminalOutput);

        assertTrue(result); // Allow but warn
        verify(terminalResponseService).sendTriggerResponse(any(Trigger.class), eq(terminalOutput));
    }

    @Test
    void testProcessCommand_Exception() throws IOException {
        String command = "ls -la";
        doThrow(new RuntimeException("Processing error")).when(sessionTrackingService).toString();

        boolean result = sshCommandProcessor.processCommand(connectedSystem, command, terminalOutput);

        // Should return false on exception
        assertFalse(result);
    }

    @Test
    void testProcessCommand_TerminalResponseException() throws IOException {
        String command = "rm -rf /";
        doThrow(new IOException("Terminal error")).when(terminalResponseService)
            .sendTriggerResponse(any(Trigger.class), any());

        boolean result = sshCommandProcessor.processCommand(connectedSystem, command, terminalOutput);

        assertFalse(result);
        verify(terminalResponseService).sendTriggerResponse(any(Trigger.class), eq(terminalOutput));
    }

    @Test
    void testProcessKeycode_Success() {
        int keyCode = 65; // 'A'

        boolean result = sshCommandProcessor.processKeycode(connectedSystem, keyCode, terminalOutput);

        assertTrue(result);
    }

    @Test
    void testProcessKeycode_Exception() {
        int keyCode = 65;
        // Force an exception by making connectedSystem throw when accessed
        when(connectedSystem.toString()).thenThrow(new RuntimeException("Keycode processing error"));

        boolean result = sshCommandProcessor.processKeycode(connectedSystem, keyCode, terminalOutput);

        // Should return false on exception
        assertFalse(result);
    }

    @Test
    void testCaseInsensitiveDangerousCommands() throws IOException {
        doNothing().when(terminalResponseService).sendTriggerResponse(any(Trigger.class), any());

        // Test uppercase variants
        assertFalse(sshCommandProcessor.processCommand(connectedSystem, "RM -RF /", terminalOutput));
        assertFalse(sshCommandProcessor.processCommand(connectedSystem, "SHUTDOWN", terminalOutput));
        assertFalse(sshCommandProcessor.processCommand(connectedSystem, "REBOOT", terminalOutput));

        verify(terminalResponseService, times(3))
            .sendTriggerResponse(any(Trigger.class), eq(terminalOutput));
    }

    @Test
    void testCaseInsensitiveWarningCommands() throws IOException {
        doNothing().when(terminalResponseService).sendTriggerResponse(any(Trigger.class), any());

        // Test uppercase variants
        assertTrue(sshCommandProcessor.processCommand(connectedSystem, "SUDO ls", terminalOutput));
        assertTrue(sshCommandProcessor.processCommand(connectedSystem, "CHMOD 777 file", terminalOutput));

        verify(terminalResponseService, times(2))
            .sendTriggerResponse(any(Trigger.class), eq(terminalOutput));
    }

    @Test
    void testCommandWithWhitespace() throws IOException {
        doNothing().when(terminalResponseService).sendTriggerResponse(any(Trigger.class), any());

        // Test commands with leading/trailing whitespace
        assertFalse(sshCommandProcessor.processCommand(connectedSystem, "  rm -rf /  ", terminalOutput));
        assertTrue(sshCommandProcessor.processCommand(connectedSystem, "  sudo ls  ", terminalOutput));

        verify(terminalResponseService, times(2))
            .sendTriggerResponse(any(Trigger.class), eq(terminalOutput));
    }
}