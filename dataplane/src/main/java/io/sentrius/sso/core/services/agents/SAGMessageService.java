package io.sentrius.sso.core.services.agents;

import com.sentrius.sag.GuardrailValidator;
import com.sentrius.sag.MapContext;
import com.sentrius.sag.MessageMinifier;
import com.sentrius.sag.SAGMessageParser;
import com.sentrius.sag.SAGParseException;
import com.sentrius.sag.model.ActionStatement;
import com.sentrius.sag.model.ErrorStatement;
import com.sentrius.sag.model.Header;
import com.sentrius.sag.model.Message;
import com.sentrius.sag.model.Statement;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for handling SAG (Sentrius Agent Grammar) messages.
 * Provides parsing, validation, and formatting capabilities for structured agent communication.
 */
@Service
@Slf4j
public class SAGMessageService {

    /**
     * Parse a SAG message string into a structured Message object.
     * 
     * @param sagMessage The SAG message string to parse
     * @return Parsed Message object
     * @throws SAGParseException if the message cannot be parsed
     */
    public Message parseMessage(String sagMessage) throws SAGParseException {
        try {
            return SAGMessageParser.parse(sagMessage);
        } catch (SAGParseException e) {
            log.error("Failed to parse SAG message: {}", sagMessage, e);
            throw e;
        }
    }

    /**
     * Format a Message object as a minified SAG string.
     * 
     * @param message The Message to format
     * @return Minified SAG message string
     */
    public String formatMessage(Message message) {
        return MessageMinifier.toMinifiedString(message);
    }

    /**
     * Create a SAG message for an agent action.
     * 
     * @param source Source agent identifier
     * @param destination Destination agent identifier
     * @param messageId Unique message identifier
     * @param verb The action verb to execute
     * @param args Positional arguments
     * @param namedArgs Named arguments
     * @param reason Optional reason for the action
     * @param policy Optional policy reference
     * @param priority Optional priority (LOW, NORMAL, HIGH, CRITICAL)
     * @return SAG message string
     */
    public String createActionMessage(String source, String destination, String messageId,
                                     String verb, List<Object> args, Map<String, Object> namedArgs,
                                     String reason, String policy, String priority) {
        StringBuilder sagBuilder = new StringBuilder();
        
        // Build header
        sagBuilder.append("H v 1 id=").append(messageId)
                  .append(" src=").append(source)
                  .append(" dst=").append(destination)
                  .append(" ts=").append(System.currentTimeMillis())
                  .append("\n");
        
        // Build action statement
        sagBuilder.append("DO ").append(verb).append("(");
        
        // Add positional arguments
        if (args != null && !args.isEmpty()) {
            for (int i = 0; i < args.size(); i++) {
                if (i > 0) sagBuilder.append(", ");
                sagBuilder.append(formatValue(args.get(i)));
            }
        }
        
        // Add named arguments
        if (namedArgs != null && !namedArgs.isEmpty()) {
            if (args != null && !args.isEmpty()) sagBuilder.append(", ");
            int idx = 0;
            for (Map.Entry<String, Object> entry : namedArgs.entrySet()) {
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
        
        return sagBuilder.toString();
    }

    /**
     * Create a simple action message with just verb and arguments.
     * 
     * @param source Source agent identifier
     * @param destination Destination agent identifier
     * @param messageId Unique message identifier
     * @param verb The action verb to execute
     * @param args Arguments as map
     * @return SAG message string
     */
    public String createSimpleAction(String source, String destination, String messageId,
                                    String verb, Map<String, Object> args) {
        return createActionMessage(source, destination, messageId, verb, null, args, null, null, null);
    }

    /**
     * Validate an action statement against a context using guardrails.
     * 
     * @param action The action statement to validate
     * @param context Context data for validation
     * @return ValidationResult indicating success or failure
     */
    public GuardrailValidator.ValidationResult validateAction(ActionStatement action, Map<String, Object> context) {
        MapContext mapContext = new MapContext(context);
        return GuardrailValidator.validate(action, mapContext);
    }

    /**
     * Extract action statements from a parsed message.
     * 
     * @param message The parsed message
     * @return List of action statements
     */
    public List<ActionStatement> extractActions(Message message) {
        List<ActionStatement> actions = new ArrayList<>();
        if (message != null && message.getStatements() != null) {
            for (Statement stmt : message.getStatements()) {
                if (stmt instanceof ActionStatement) {
                    actions.add((ActionStatement) stmt);
                }
            }
        }
        return actions;
    }

    /**
     * Check if a string is a valid SAG message.
     * 
     * @param message The string to check
     * @return true if the message is valid SAG format
     */
    public boolean isValidSAGMessage(String message) {
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
     * Create an error message in SAG format.
     * 
     * @param source Source agent identifier
     * @param destination Destination agent identifier
     * @param messageId Unique message identifier
     * @param errorCode Error code
     * @param errorMessage Error description
     * @return SAG error message string
     */
    public String createErrorMessage(String source, String destination, String messageId,
                                    String errorCode, String errorMessage) {
        StringBuilder sagBuilder = new StringBuilder();
        
        // Build header
        sagBuilder.append("H v 1 id=").append(messageId)
                  .append(" src=").append(source)
                  .append(" dst=").append(destination)
                  .append(" ts=").append(System.currentTimeMillis())
                  .append("\n");
        
        // Build error statement
        sagBuilder.append("ERR ").append(errorCode);
        if (errorMessage != null && !errorMessage.isEmpty()) {
            sagBuilder.append(" ").append(formatValue(errorMessage));
        }
        
        return sagBuilder.toString();
    }

    /**
     * Compare token usage between SAG and JSON formats.
     * 
     * @param message The message to compare
     * @return TokenComparison showing the difference
     */
    public MessageMinifier.TokenComparison compareTokenUsage(Message message) {
        return MessageMinifier.compareWithJSON(message);
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
}
