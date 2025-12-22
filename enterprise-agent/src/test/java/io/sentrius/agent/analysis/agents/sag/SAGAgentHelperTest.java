package io.sentrius.agent.analysis.agents.sag;

import com.sentrius.sag.SAGParseException;
import com.sentrius.sag.model.ActionStatement;
import com.sentrius.sag.model.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SAGAgentHelperTest {

    private SAGAgentHelper sagHelper;

    @BeforeEach
    void setUp() {
        sagHelper = new SAGAgentHelper();
    }

    @Test
    void testCreateSimpleAction() {
        Map<String, Object> args = Map.of(
            "target", "service-a",
            "operation", "restart"
        );
        
        SAGAgentHelper.SAGMessage result = sagHelper.createSimpleAction(
            "target-agent",
            "source-agent",
            "restart",
            args
        );
        
        assertNotNull(result);
        assertNotNull(result.getMessageId());
        assertNotNull(result.getMessage());
        
        String message = result.getMessage();
        assertTrue(message.contains("DO restart("));
        assertTrue(message.contains("target=\"service-a\""));
        assertTrue(message.contains("operation=\"restart\""));
        assertTrue(message.contains("src=source-agent"));
        assertTrue(message.contains("dst=target-agent"));
    }

    @Test
    void testCreateActionWithAllOptions() {
        Map<String, Object> args = Map.of(
            "app", "webapp",
            "version", "2.0"
        );
        
        SAGAgentHelper.SAGMessage result = sagHelper.createAction(
            "target-agent",
            "source-agent",
            "deploy",
            args,
            "Critical security patch",
            "prod-deployment-policy",
            "HIGH"
        );
        
        assertNotNull(result);
        String message = result.getMessage();
        
        assertTrue(message.contains("DO deploy("));
        assertTrue(message.contains("app=\"webapp\""));
        assertTrue(message.contains("version=\"2.0\""));
        assertTrue(message.contains("P:prod-deployment-policy"));
        assertTrue(message.contains("PRIO=HIGH"));
        assertTrue(message.contains("BECAUSE \"Critical security patch\""));
    }

    @Test
    void testIsSAGMessage() {
        // Valid SAG message
        String validMessage = "H v 1 id=msg1 src=a dst=b ts=123\nDO test()";
        assertTrue(sagHelper.isSAGMessage(validMessage));
        
        // Invalid message
        String invalidMessage = "This is not a SAG message";
        assertFalse(sagHelper.isSAGMessage(invalidMessage));
        
        // Null message
        assertFalse(sagHelper.isSAGMessage(null));
        
        // Empty message
        assertFalse(sagHelper.isSAGMessage(""));
    }

    @Test
    void testParseAndValidate() throws SAGParseException {
        String sagMessage = "H v 1 id=msg1 src=agent-a dst=agent-b ts=1234567890\n" +
                           "DO deploy(app=\"webapp\")";
        
        Message message = sagHelper.parseAndValidate(sagMessage, null);
        
        assertNotNull(message);
        assertEquals("msg1", message.getHeader().getMessageId());
        assertEquals("agent-a", message.getHeader().getSource());
        assertEquals("agent-b", message.getHeader().getDestination());
    }

    @Test
    void testParseAndValidateWithGuardrails() throws SAGParseException {
        String sagMessage = "H v 1 id=msg1 src=agent-a dst=agent-b ts=1234567890\n" +
                           "DO deploy(app=\"webapp\") BECAUSE \"approved == true\"";
        
        // Should pass with valid context
        Map<String, Object> validContext = Map.of("approved", true);
        Message message = sagHelper.parseAndValidate(sagMessage, validContext);
        assertNotNull(message);
        
        // Should fail with invalid context
        Map<String, Object> invalidContext = Map.of("approved", false);
        assertThrows(SAGParseException.class, () -> {
            sagHelper.parseAndValidate(sagMessage, invalidContext);
        });
    }

    @Test
    void testExtractActions() throws SAGParseException {
        String sagMessage = "H v 1 id=msg1 src=agent-a dst=agent-b ts=1234567890\n" +
                           "DO deploy(app=\"webapp\");DO verify()";
        
        Message message = sagHelper.parseAndValidate(sagMessage, null);
        List<ActionStatement> actions = sagHelper.extractActions(message);
        
        assertEquals(2, actions.size());
        assertEquals("deploy", actions.get(0).getVerb());
        assertEquals("verify", actions.get(1).getVerb());
    }

    @Test
    void testCreateValidationContext() {
        Map<String, Object> additional = Map.of(
            "environment", "production",
            "approved", true
        );
        
        Map<String, Object> context = sagHelper.createValidationContext(
            "user123",
            "session456",
            additional
        );
        
        assertNotNull(context);
        assertEquals("user123", context.get("userId"));
        assertEquals("session456", context.get("sessionId"));
        assertTrue(context.containsKey("timestamp"));
        assertEquals("production", context.get("environment"));
        assertEquals(true, context.get("approved"));
    }

    @Test
    void testSAGMessageContainer() {
        String validUUID = "550e8400-e29b-41d4-a716-446655440000";
        SAGAgentHelper.SAGMessage sagMessage = new SAGAgentHelper.SAGMessage(
            validUUID,
            "H v 1 id=" + validUUID + " src=a dst=b ts=123\nDO test()"
        );
        
        assertEquals(validUUID, sagMessage.getMessageId());
        assertNotNull(sagMessage.getMessage());
        assertEquals(validUUID, sagMessage.getMessageIdAsUUID().toString());
    }

    @Test
    void testCreateActionWithNumbersAndBooleans() {
        Map<String, Object> args = Map.of(
            "count", 42,
            "enabled", true,
            "rate", 3.14
        );
        
        SAGAgentHelper.SAGMessage result = sagHelper.createSimpleAction(
            "target-agent",
            "source-agent",
            "configure",
            args
        );
        
        String message = result.getMessage();
        assertTrue(message.contains("count=42"));
        assertTrue(message.contains("enabled=true"));
        assertTrue(message.contains("rate=3.14"));
    }

    @Test
    void testCreateActionWithNullValue() {
        Map<String, Object> args = Map.of(
            "value", "test"
        );
        
        SAGAgentHelper.SAGMessage result = sagHelper.createSimpleAction(
            "target-agent",
            "source-agent",
            "test",
            args
        );
        
        assertNotNull(result);
        assertNotNull(result.getMessage());
    }
}
