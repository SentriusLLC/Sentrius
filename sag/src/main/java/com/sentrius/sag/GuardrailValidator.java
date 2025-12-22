package com.sentrius.sag;

import com.sentrius.sag.model.ActionStatement;
import com.sentrius.sag.model.ErrorStatement;

/**
 * Validates ActionStatements against their BECAUSE clauses using a Context.
 * This is the Semantic Guardrail feature that prevents actions from executing
 * when their preconditions are not met.
 */
public class GuardrailValidator {
    
    /**
     * Validate an ActionStatement against a context.
     * If the action has a BECAUSE clause that contains an expression,
     * it will be evaluated against the context.
     * 
     * @param action The action to validate
     * @param context The context to evaluate against
     * @return A ValidationResult indicating success or failure
     */
    public static ValidationResult validate(ActionStatement action, Context context) {
        if (action == null) {
            return ValidationResult.failure("INVALID_ACTION", "Action cannot be null");
        }
        
        String reason = action.getReason();
        if (reason == null || reason.trim().isEmpty()) {
            return ValidationResult.success();
        }
        
        // If the reason is just a string (not an expression), we consider it valid
        // An expression would contain operators like >, <, ==, etc.
        if (!isExpression(reason)) {
            return ValidationResult.success();
        }
        
        try {
            Object result = ExpressionEvaluator.evaluate(reason, context);
            
            if (result instanceof Boolean) {
                boolean passed = (Boolean) result;
                if (!passed) {
                    return ValidationResult.failure("PRECONDITION_FAILED", 
                        "Precondition not met: " + reason);
                }
                return ValidationResult.success();
            } else {
                // Non-boolean results are considered truthy if not null
                return result != null ? ValidationResult.success() : 
                    ValidationResult.failure("PRECONDITION_FAILED", "Expression evaluated to null");
            }
        } catch (SAGParseException e) {
            return ValidationResult.failure("INVALID_EXPRESSION", 
                "Failed to evaluate precondition: " + e.getMessage());
        }
    }
    
    private static boolean isExpression(String reason) {
        // Simple heuristic: if it contains operators, it's likely an expression
        return reason.contains(">") || reason.contains("<") || reason.contains("==") ||
               reason.contains("!=") || reason.contains(">=") || reason.contains("<=") ||
               reason.contains("&&") || reason.contains("||");
    }
    
    /**
     * Result of a validation check.
     */
    public static class ValidationResult {
        private final boolean valid;
        private final String errorCode;
        private final String errorMessage;
        
        private ValidationResult(boolean valid, String errorCode, String errorMessage) {
            this.valid = valid;
            this.errorCode = errorCode;
            this.errorMessage = errorMessage;
        }
        
        public static ValidationResult success() {
            return new ValidationResult(true, null, null);
        }
        
        public static ValidationResult failure(String errorCode, String errorMessage) {
            return new ValidationResult(false, errorCode, errorMessage);
        }
        
        public boolean isValid() {
            return valid;
        }
        
        public String getErrorCode() {
            return errorCode;
        }
        
        public String getErrorMessage() {
            return errorMessage;
        }
        
        /**
         * Convert validation failure to an ErrorStatement.
         * @return An ErrorStatement if validation failed, null otherwise
         */
        public ErrorStatement toErrorStatement() {
            if (valid) {
                return null;
            }
            return new ErrorStatement(errorCode, errorMessage);
        }
        
        @Override
        public String toString() {
            if (valid) {
                return "ValidationResult{valid=true}";
            }
            return "ValidationResult{valid=false, errorCode='" + errorCode + 
                   "', errorMessage='" + errorMessage + "'}";
        }
    }
}
