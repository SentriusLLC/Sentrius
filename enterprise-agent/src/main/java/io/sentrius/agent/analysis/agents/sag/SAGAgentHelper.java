package io.sentrius.agent.analysis.agents.sag;

import com.sentrius.sag.GuardrailValidator;
import com.sentrius.sag.MapContext;
import com.sentrius.sag.SAGMessageParser;
import com.sentrius.sag.SAGParseException;
import com.sentrius.sag.model.ActionStatement;
import com.sentrius.sag.model.Message;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Helper component for enterprise agents to send and receive SAG (Sentrius Agent Grammar) messages.
 * Provides utilities for structured agent-to-agent communication with validation and guardrails.
 */
@Component
@Slf4j
public class SAGAgentHelper {

    /**
     * Create a SAG-formatted action message.
     * 
     * @param targetAgent Target agent identifier
     * @param sourceAgent Source agent identifier
     * @param verb Action verb to execute
     * @param args Named arguments for the action
     * @param reason Optional reason for the action
     * @param policy Optional policy reference
     * @param priority Optional priority (LOW, NORMAL, HIGH, CRITICAL)
     * @return Tuple of (messageId, sagMessage)
     */
    public SAGMessage createAction(String targetAgent, String sourceAgent, String verb, 
                          Map<String, Object> args, String reason, String policy, String priority) {
        
        String messageId = UUID.randomUUID().toString();
        StringBuilder sagBuilder = new StringBuilder();
        
        // Build header
        sagBuilder.append("H v 1 id=").append(messageId)
                  .append(" src=").append(sourceAgent)
                  .append(" dst=").append(targetAgent)
                  .append(" ts=").append(System.currentTimeMillis())
                  .append("\n");
        
        // Build action statement
        sagBuilder.append("DO ").append(verb).append("(");
        
        // Add named arguments
        if (args != null && !args.isEmpty()) {
            int idx = 0;
            for (Map.Entry<String, Object> entry : args.entrySet()) {
                if (idx++ > 0) sagBuilder.append(", ");
                sagBuilder.append(entry.getKey()).append("=").append(formatValue(entry.getValue()));
            }
        }
        
        sagBuilder.append(")");
        
        // Add optional clauses
        if (policy != null && !policy.isEmpty()) {
            sagBuilder.append(" P:").append(policy);
        }
        
        if (priority != null && !priority.isEmpty()) {
            sagBuilder.append(" PRIO=").append(priority);
        }
        
        if (reason != null && !reason.isEmpty()) {
            sagBuilder.append(" BECAUSE ").append(formatValue(reason));
        }
        
        String sagMessage = sagBuilder.toString();
        log.info("Created SAG message for {}: {}", targetAgent, sagMessage);
        
        return new SAGMessage(messageId, sagMessage);
    }

    /**
     * Create a simple SAG action without policy or priority.
     */
    public SAGMessage createSimpleAction(String targetAgent, String sourceAgent, String verb, Map<String, Object> args) {
        return createAction(targetAgent, sourceAgent, verb, args, null, null, null);
    }

    /**
     * Parse and validate a received SAG message.
     * 
     * @param sagMessage The SAG message string
     * @param validationContext Optional context for guardrail validation
     * @return Parsed Message object
     * @throws SAGParseException if parsing fails
     */
    public Message parseAndValidate(String sagMessage, Map<String, Object> validationContext) throws SAGParseException {
        Message message = SAGMessageParser.parse(sagMessage);
        
        // If validation context is provided, validate all action statements
        if (validationContext != null && !validationContext.isEmpty()) {
            MapContext context = new MapContext(validationContext);
            
            for (var statement : message.getStatements()) {
                if (statement instanceof ActionStatement) {
                    ActionStatement action = (ActionStatement) statement;
                    GuardrailValidator.ValidationResult result = GuardrailValidator.validate(action, context);
                    
                    if (!result.isValid()) {
                        log.warn("Action validation failed: {} - {}", result.getErrorCode(), result.getErrorMessage());
                        throw new SAGParseException("Guardrail validation failed: " + result.getErrorMessage());
                    }
                }
            }
        }
        
        return message;
    }

    /**
     * Check if a message is in SAG format.
     */
    public boolean isSAGMessage(String message) {
        if (message == null || message.trim().isEmpty()) {
            return false;
        }
        
        try {
            SAGMessageParser.parse(message);
            return true;
        } catch (SAGParseException e) {
            return false;
        }
    }

    /**
     * Extract action statements from a SAG message.
     */
    public List<ActionStatement> extractActions(Message message) {
        return message.getStatements().stream()
            .filter(stmt -> stmt instanceof ActionStatement)
            .map(stmt -> (ActionStatement) stmt)
            .toList();
    }

    /**
     * Create a validation context from available data.
     */
    public Map<String, Object> createValidationContext(String userId, String sessionId, Map<String, Object> additionalData) {
        Map<String, Object> context = new HashMap<>();
        context.put("userId", userId);
        context.put("sessionId", sessionId);
        context.put("timestamp", System.currentTimeMillis());
        
        if (additionalData != null) {
            context.putAll(additionalData);
        }
        
        return context;
    }

    /**
     * Format a value for inclusion in a SAG message.
     */
    private String formatValue(Object value) {
        if (value == null) {
            return "null";
        } else if (value instanceof String) {
            return "\"" + value.toString().replace("\"", "\\\"") + "\"";
        } else if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        } else {
            return "\"" + value.toString().replace("\"", "\\\"") + "\"";
        }
    }

    /**
     * Container for SAG message and its ID.
     */
    public static class SAGMessage {
        private final String messageId;
        private final String message;

        public SAGMessage(String messageId, String message) {
            this.messageId = messageId;
            this.message = message;
        }

        public String getMessageId() {
            return messageId;
        }

        public String getMessage() {
            return message;
        }

        public UUID getMessageIdAsUUID() {
            return UUID.fromString(messageId);
        }
    }
}
