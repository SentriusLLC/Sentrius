package com.sentrius.sag;

import com.sentrius.sag.model.ActionStatement;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class GuardrailValidatorTest {
    
    @Test
    void testValidateSuccessfulPrecondition() {
        MapContext context = new MapContext();
        context.set("balance", 1500);
        
        ActionStatement action = new ActionStatement(
            "transfer",
            Collections.emptyList(),
            Collections.singletonMap("amt", 500),
            null,
            null,
            null,
            "balance > 1000"
        );
        
        GuardrailValidator.ValidationResult result = GuardrailValidator.validate(action, context);
        
        assertTrue(result.isValid());
    }
    
    @Test
    void testValidateFailedPrecondition() {
        MapContext context = new MapContext();
        context.set("balance", 400);
        
        ActionStatement action = new ActionStatement(
            "transfer",
            Collections.emptyList(),
            Collections.singletonMap("amt", 500),
            null,
            null,
            null,
            "balance > 1000"
        );
        
        GuardrailValidator.ValidationResult result = GuardrailValidator.validate(action, context);
        
        assertFalse(result.isValid());
        assertEquals("PRECONDITION_FAILED", result.getErrorCode());
        assertNotNull(result.getErrorMessage());
    }
    
    @Test
    void testValidateNoReasonClause() {
        MapContext context = new MapContext();
        
        ActionStatement action = new ActionStatement(
            "transfer",
            Collections.emptyList(),
            Collections.singletonMap("amt", 500),
            null,
            null,
            null,
            null
        );
        
        GuardrailValidator.ValidationResult result = GuardrailValidator.validate(action, context);
        
        assertTrue(result.isValid());
    }
    
    @Test
    void testValidateStringReason() {
        MapContext context = new MapContext();
        
        ActionStatement action = new ActionStatement(
            "transfer",
            Collections.emptyList(),
            Collections.singletonMap("amt", 500),
            null,
            null,
            null,
            "security update"
        );
        
        GuardrailValidator.ValidationResult result = GuardrailValidator.validate(action, context);
        
        assertTrue(result.isValid());
    }
    
    @Test
    void testValidateComplexExpression() {
        MapContext context = new MapContext();
        context.set("balance", 1500);
        context.set("verified", true);
        
        ActionStatement action = new ActionStatement(
            "transfer",
            Collections.emptyList(),
            Collections.singletonMap("amt", 500),
            null,
            null,
            null,
            "(balance > 1000) && (verified == true)"
        );
        
        GuardrailValidator.ValidationResult result = GuardrailValidator.validate(action, context);
        
        assertTrue(result.isValid());
    }
    
    @Test
    void testValidationResultToErrorStatement() {
        GuardrailValidator.ValidationResult result = GuardrailValidator.ValidationResult.failure(
            "PRECONDITION_FAILED",
            "Balance too low"
        );
        
        assertNotNull(result.toErrorStatement());
        assertEquals("PRECONDITION_FAILED", result.toErrorStatement().getErrorCode());
        assertEquals("Balance too low", result.toErrorStatement().getMessage());
    }
}
