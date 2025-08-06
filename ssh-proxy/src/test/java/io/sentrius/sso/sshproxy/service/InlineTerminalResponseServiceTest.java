package io.sentrius.sso.sshproxy.service;

import io.sentrius.sso.automation.auditing.Trigger;
import io.sentrius.sso.automation.auditing.TriggerAction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class InlineTerminalResponseServiceTest {

    @InjectMocks
    private InlineTerminalResponseService terminalResponseService;

    @Test
    void testFormatDenyMessage() {
        Trigger trigger = new Trigger(TriggerAction.DENY_ACTION, "Dangerous command detected");
        String message = terminalResponseService.formatTriggerMessage(trigger);
        
        assertNotNull(message);
        assertTrue(message.contains("COMMAND BLOCKED"));
        assertTrue(message.contains("Dangerous command detected"));
    }

    @Test
    void testFormatWarnMessage() {
        Trigger trigger = new Trigger(TriggerAction.WARN_ACTION, "Potentially risky operation");
        String message = terminalResponseService.formatTriggerMessage(trigger);
        
        assertNotNull(message);
        assertTrue(message.contains("WARNING"));
        assertTrue(message.contains("Potentially risky operation"));
    }

    @Test
    void testSendTriggerResponse() throws IOException {
        Trigger trigger = new Trigger(TriggerAction.RECORD_ACTION, "Recording session");
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        
        terminalResponseService.sendTriggerResponse(trigger, outputStream);
        
        String output = outputStream.toString();
        assertTrue(output.contains("RECORDING"));
        assertTrue(output.contains("Recording session"));
    }

    @Test
    void testNoActionTrigger() {
        Trigger trigger = new Trigger(TriggerAction.NO_ACTION, "No action needed");
        String message = terminalResponseService.formatTriggerMessage(trigger);
        
        assertEquals("", message);
    }

    @Test
    void testPromptMessage() {
        Trigger trigger = new Trigger(TriggerAction.PROMPT_ACTION, "Confirm operation", "Do you want to continue?");
        String message = terminalResponseService.formatTriggerMessage(trigger);
        
        assertNotNull(message);
        assertTrue(message.contains("PROMPT"));
        assertTrue(message.contains("Confirm operation"));
        assertTrue(message.contains("Do you want to continue?"));
    }
}