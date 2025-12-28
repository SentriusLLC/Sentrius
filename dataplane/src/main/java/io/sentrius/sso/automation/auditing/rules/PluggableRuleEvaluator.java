package io.sentrius.sso.automation.auditing.rules;

import java.util.Optional;
import io.sentrius.sso.automation.auditing.AccessTokenEvaluator;
import io.sentrius.sso.automation.auditing.Trigger;
import io.sentrius.sso.automation.auditing.TriggerAction;
import io.sentrius.sso.core.config.SystemOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;

/**
 * Pluggable rule evaluator that allows users to define custom zero trust rules
 * using a safe expression language. This evaluator uses Spring Expression Language (SpEL)
 * with restricted context to prevent data exfiltration and ensure security.
 *
 * Configuration format: expression:action:description[:sanitized]
 * Example: "text.contains('sudo'):DENY:Sudo commands are not allowed:true"
 *
 * Available expression variables:
 * - text: The input text to evaluate
 * - length: The length of the input text
 * - isEmpty: Whether the text is empty
 * - toLowerCase: Convert text to lowercase
 * - toUpperCase: Convert text to uppercase
 *
 * Supported actions: DENY, JIT, WARN, ALERT, LOG, PROMPT, PERSISTENT
 */
@Slf4j
public class PluggableRuleEvaluator extends AccessTokenEvaluator {

    private Expression expression;
    private TriggerAction action;
    private String description;
    private boolean isSanitized = true;
    private final ExpressionParser parser = new SpelExpressionParser();

    public PluggableRuleEvaluator() {
        // Default constructor
    }

    @Override
    public Optional<Trigger> trigger(String text) {
        if (text == null || expression == null) {
            return Optional.empty();
        }

        try {
            // Create a safe evaluation context with restricted access
            EvaluationContext context = createSafeContext(text);

            // Evaluate the expression
            Boolean result = expression.getValue(context, Boolean.class);

            if (Boolean.TRUE.equals(result)) {
                log.debug("Pluggable rule triggered for text: {}", text);
                return Optional.of(new Trigger(action, description));
            }
        } catch (Exception e) {
            log.error("Error evaluating pluggable rule: {}", e.getMessage(), e);
            // Return empty on error to fail safe
        }

        return Optional.empty();
    }

    @Override
    public boolean configure(SystemOptions systemOptions, String configuration) {
        if (configuration == null || configuration.trim().isEmpty()) {
            log.error("Configuration cannot be null or empty");
            return false;
        }

        try {
            String[] parts = configuration.split(":", 4);

            if (parts.length < 3) {
                log.error("Invalid configuration format. Expected: expression:action:description[:sanitized]");
                return false;
            }

            String expressionStr = parts[0].trim();
            String actionStr = parts[1].trim();
            this.description = parts[2].trim();

            // Parse sanitized flag if present
            if (parts.length >= 4) {
                this.isSanitized = Boolean.parseBoolean(parts[3].trim());
            }

            // Validate and parse the expression
            if (!validateExpression(expressionStr)) {
                log.error("Expression validation failed: {}", expressionStr);
                return false;
            }

            this.expression = parser.parseExpression(expressionStr);
            this.action = TriggerAction.valueOfStr(actionStr);

            if (this.action == null || this.action == TriggerAction.NO_ACTION) {
                log.error("Invalid action: {}", actionStr);
                return false;
            }

            log.info("Pluggable rule configured: expression={}, action={}, description={}",
                expressionStr, action, description);
            return true;

        } catch (Exception e) {
            log.error("Failed to configure pluggable rule: {}", e.getMessage(), e);
            return false;
        }
    }

    @Override
    public TriggerAction describeAction() {
        return action != null ? action : TriggerAction.NO_ACTION;
    }

    @Override
    public boolean requiresSanitized() {
        return isSanitized;
    }

    /**
     * Validates the expression for security concerns.
     * Blocks potentially dangerous patterns like class access, reflection, etc.
     */
    private boolean validateExpression(String expressionStr) {
        if (expressionStr == null || expressionStr.trim().isEmpty()) {
            return false;
        }

        // Block dangerous patterns
        String[] forbiddenPatterns = {
            "T(", // Type reference
            "getClass", // Class access
            "@", // Bean reference
            "new ", // Object construction
            "Runtime", // Runtime access
            "System", // System access
            "Process", // Process access
            "Class", // Class reference
            "Reflection", // Reflection
            "invoke", // Method invocation
            "exec", // Execution
            ".class", // Class literal
            "java.lang", // Java core access
            "java.io", // IO access
            "java.net", // Network access
        };

        String lowerExpr = expressionStr.toLowerCase();
        for (String pattern : forbiddenPatterns) {
            if (lowerExpr.contains(pattern.toLowerCase())) {
                log.error("Expression contains forbidden pattern: {}", pattern);
                return false;
            }
        }

        return true;
    }

    /**
     * Creates a safe evaluation context that only exposes specific safe methods
     * and prevents access to dangerous operations.
     */
    private EvaluationContext createSafeContext(String text) {
        // Use SimpleEvaluationContext with property read access for method invocations
        // This prevents access to Java types, constructors, and bean references
        // but allows method calls on the root object
        EvaluationContext context = SimpleEvaluationContext
            .forReadOnlyDataBinding()
            .withInstanceMethods()
            .build();

        // Set the text as the root object so expressions can call methods on it
        context.setVariable("text", text);
        context.setVariable("length", text.length());
        context.setVariable("isEmpty", text.isEmpty());
        context.setVariable("isBlank", text.isBlank());

        return context;
    }
}
