package io.sentrius.sso.core.services.agents;

import com.sentrius.sag.GuardrailValidator;
import com.sentrius.sag.MessageMinifier;
import com.sentrius.sag.SAGParseException;
import com.sentrius.sag.model.ActionStatement;
import com.sentrius.sag.model.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SAGMessageServiceTest {

    private SAGMessageService sagMessageService;

    @BeforeEach
    void setUp() {
        sagMessageService = new SAGMessageService();
    }

    @Test
    void testParseSimpleActionMessage() throws SAGParseException {
        String sagMessage = "H v 1 id=msg1 src=agent-a dst=agent-b ts=1234567890\nDO deploy(app=\"webapp\")";
        
        Message message = sagMessageService.parseMessage(sagMessage);
        
        assertNotNull(message);
        assertEquals("msg1", message.getHeader().getMessageId());
        assertEquals("agent-a", message.getHeader().getSource());
        assertEquals("agent-b", message.getHeader().getDestination());
        assertEquals(1, message.getStatements().size());
        
        List<ActionStatement> actions = sagMessageService.extractActions(message);
        assertEquals(1, actions.size());
        assertEquals("deploy", actions.get(0).getVerb());
    }

    @Test
    void testCreateSimpleAction() throws SAGParseException {
        Map<String, Object> args = Map.of(
            "app", "webapp",
            "version", "2.0"
        );
        
        String sagMessage = sagMessageService.createSimpleAction(
            "agent-a",
            "agent-b",
            "msg123",
            "deploy",
            args
        );
        
        assertNotNull(sagMessage);
        assertTrue(sagMessage.contains("DO deploy("));
        assertTrue(sagMessage.contains("app=\"webapp\""));
        assertTrue(sagMessage.contains("version=\"2.0\""));
        
        // Verify it can be parsed back
        Message message = sagMessageService.parseMessage(sagMessage);
        assertNotNull(message);
        
        List<ActionStatement> actions = sagMessageService.extractActions(message);
        assertEquals(1, actions.size());
        assertEquals("deploy", actions.get(0).getVerb());
    }

    @Test
    void testCreateActionWithPolicyAndPriority() throws SAGParseException {
        Map<String, Object> args = Map.of("app", "critical-service");
        
        String sagMessage = sagMessageService.createActionMessage(
            "agent-a",
            "agent-b",
            "msg456",
            "restart",
            null,
            args,
            "System health check failed",
            "prod-restart-policy",
            "HIGH"
        );
        
        assertNotNull(sagMessage);
        assertTrue(sagMessage.contains("DO restart("));
        assertTrue(sagMessage.contains("P:prod-restart-policy"));
        assertTrue(sagMessage.contains("PRIO=HIGH"));
        assertTrue(sagMessage.contains("BECAUSE"));
        
        // Verify parsing
        Message message = sagMessageService.parseMessage(sagMessage);
        List<ActionStatement> actions = sagMessageService.extractActions(message);
        assertEquals(1, actions.size());
        
        ActionStatement action = actions.get(0);
        assertEquals("restart", action.getVerb());
        assertEquals("prod-restart-policy", action.getPolicy());
        assertEquals("HIGH", action.getPriority());
        assertNotNull(action.getReason());
    }

    @Test
    void testValidateActionWithGuardrails() throws SAGParseException {
        // Create action with guardrail
        String sagMessage = "H v 1 id=msg1 src=agent-a dst=agent-b ts=1234567890\n" +
                           "DO deploy(app=\"webapp\") BECAUSE \"approved == true\"";
        
        Message message = sagMessageService.parseMessage(sagMessage);
        List<ActionStatement> actions = sagMessageService.extractActions(message);
        
        // Test with satisfied context
        Map<String, Object> validContext = Map.of("approved", true);
        GuardrailValidator.ValidationResult result = sagMessageService.validateAction(actions.get(0), validContext);
        assertTrue(result.isValid());
        
        // Test with unsatisfied context
        Map<String, Object> invalidContext = Map.of("approved", false);
        GuardrailValidator.ValidationResult failedResult = sagMessageService.validateAction(actions.get(0), invalidContext);
        assertFalse(failedResult.isValid());
        assertNotNull(failedResult.getErrorMessage());
    }

    @Test
    void testIsValidSAGMessage() {
        // Valid message
        String validMessage = "H v 1 id=msg1 src=a dst=b ts=123\nDO test()";
        assertTrue(sagMessageService.isValidSAGMessage(validMessage));
        
        // Invalid message
        String invalidMessage = "Not a SAG message";
        assertFalse(sagMessageService.isValidSAGMessage(invalidMessage));
        
        // Null message
        assertFalse(sagMessageService.isValidSAGMessage(null));
        
        // Empty message
        assertFalse(sagMessageService.isValidSAGMessage(""));
    }

    @Test
    void testCreateErrorMessage() throws SAGParseException {
        String errorMessage = sagMessageService.createErrorMessage(
            "agent-a",
            "agent-b",
            "msg789",
            "TIMEOUT",
            "Request timed out after 30 seconds"
        );
        
        assertNotNull(errorMessage);
        assertTrue(errorMessage.contains("ERR TIMEOUT"));
        assertTrue(errorMessage.contains("timed out"));
        
        // Verify parsing
        Message message = sagMessageService.parseMessage(errorMessage);
        assertNotNull(message);
        assertEquals(1, message.getStatements().size());
    }

    @Test
    void testFormatMessage() throws SAGParseException {
        // Parse a message
        String original = "H v 1 id=msg1 src=agent-a dst=agent-b ts=1234567890\nDO deploy(app=\"webapp\")";
        Message message = sagMessageService.parseMessage(original);
        
        // Format it back
        String formatted = sagMessageService.formatMessage(message);
        assertNotNull(formatted);
        assertTrue(formatted.contains("deploy"));
        assertTrue(formatted.contains("webapp"));
        
        // Should be able to parse the formatted message
        Message reparsed = sagMessageService.parseMessage(formatted);
        assertNotNull(reparsed);
    }

    @Test
    void testCompareTokenUsage() throws SAGParseException {
        String sagMessage = "H v 1 id=msg1 src=agent-a dst=agent-b ts=1234567890\n" +
                           "DO deploy(app=\"webapp\", version=\"2.0\", env=\"prod\")";
        
        Message message = sagMessageService.parseMessage(sagMessage);
        MessageMinifier.TokenComparison comparison = sagMessageService.compareTokenUsage(message);
        
        assertNotNull(comparison);
        assertTrue(comparison.getSagTokens() > 0);
        assertTrue(comparison.getJsonTokens() > 0);
        assertTrue(comparison.getSagTokens() < comparison.getJsonTokens(), 
                  "SAG should use fewer tokens than JSON");
        assertTrue(comparison.getPercentSaved() > 0, 
                  "SAG should save tokens compared to JSON");
    }

    @Test
    void testExtractActionsFromMultipleStatements() throws SAGParseException {
        String sagMessage = "H v 1 id=msg1 src=agent-a dst=agent-b ts=1234567890\n" +
                           "DO deploy(app=\"webapp\");EVT deployment_started();DO verify()";
        
        Message message = sagMessageService.parseMessage(sagMessage);
        List<ActionStatement> actions = sagMessageService.extractActions(message);
        
        assertEquals(2, actions.size());
        assertEquals("deploy", actions.get(0).getVerb());
        assertEquals("verify", actions.get(1).getVerb());
    }
}
