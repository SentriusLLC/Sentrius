package com.sentrius.sag;

import com.sentrius.sag.model.ActionStatement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class SchemaValidatorTest {
    
    private SchemaRegistry registry;
    private SchemaValidator validator;
    
    @BeforeEach
    void setUp() {
        registry = new SchemaRegistry();
        validator = new SchemaValidator(registry);
        
        // Register a 'reorder' verb schema
        VerbSchema reorderSchema = new VerbSchema.Builder("reorder")
            .addNamedArg("item", VerbSchema.ArgType.STRING, true, "Item to reorder")
            .addNamedArg("qty", VerbSchema.ArgType.INTEGER, true, "Quantity")
            .build();
        registry.register(reorderSchema);
        
        // Register a 'deploy' verb schema with both positional and named args
        VerbSchema deploySchema = new VerbSchema.Builder("deploy")
            .addPositionalArg("app", VerbSchema.ArgType.STRING, true, "Application name")
            .addNamedArg("version", VerbSchema.ArgType.INTEGER, false, "Version number")
            .addNamedArg("env", VerbSchema.ArgType.STRING, false, "Environment")
            .build();
        registry.register(deploySchema);
    }
    
    @Test
    void testValidActionWithCorrectArgs() {
        ActionStatement action = new ActionStatement(
            "reorder",
            Collections.emptyList(),
            Map.of("item", "laptop", "qty", 5),
            null, null, null, null
        );
        
        SchemaValidator.ValidationResult result = validator.validate(action);
        
        assertTrue(result.isValid());
    }
    
    @Test
    void testInvalidActionWithWrongKeyName() {
        ActionStatement action = new ActionStatement(
            "reorder",
            Collections.emptyList(),
            Map.of("product", "laptop", "qty", 5), // Wrong key 'product' instead of 'item'
            null, null, null, null
        );
        
        SchemaValidator.ValidationResult result = validator.validate(action);
        
        assertFalse(result.isValid());
        assertEquals("INVALID_ARGS", result.getErrorCode());
        assertTrue(result.getErrorMessage().contains("product"));
    }
    
    @Test
    void testMissingRequiredArg() {
        ActionStatement action = new ActionStatement(
            "reorder",
            Collections.emptyList(),
            Map.of("item", "laptop"), // Missing required 'qty'
            null, null, null, null
        );
        
        SchemaValidator.ValidationResult result = validator.validate(action);
        
        assertFalse(result.isValid());
        assertEquals("MISSING_ARG", result.getErrorCode());
        assertTrue(result.getErrorMessage().contains("qty"));
    }
    
    @Test
    void testTypeMismatch() {
        ActionStatement action = new ActionStatement(
            "reorder",
            Collections.emptyList(),
            Map.of("item", "laptop", "qty", "five"), // qty should be Integer, not String
            null, null, null, null
        );
        
        SchemaValidator.ValidationResult result = validator.validate(action);
        
        assertFalse(result.isValid());
        assertEquals("TYPE_MISMATCH", result.getErrorCode());
        assertTrue(result.getErrorMessage().contains("qty"));
    }
    
    @Test
    void testUnregisteredVerbPassesValidation() {
        ActionStatement action = new ActionStatement(
            "unknownVerb",
            Collections.emptyList(),
            Map.of("any", "value"),
            null, null, null, null
        );
        
        SchemaValidator.ValidationResult result = validator.validate(action);
        
        // No schema registered, so validation passes
        assertTrue(result.isValid());
    }
    
    @Test
    void testPositionalArgsValidation() {
        ActionStatement action = new ActionStatement(
            "deploy",
            List.of("myapp"), // Correct positional arg
            Map.of("version", 2),
            null, null, null, null
        );
        
        SchemaValidator.ValidationResult result = validator.validate(action);
        
        assertTrue(result.isValid());
    }
    
    @Test
    void testMissingRequiredPositionalArg() {
        ActionStatement action = new ActionStatement(
            "deploy",
            Collections.emptyList(), // Missing required positional arg 'app'
            Map.of("version", 2),
            null, null, null, null
        );
        
        SchemaValidator.ValidationResult result = validator.validate(action);
        
        assertFalse(result.isValid());
        assertEquals("MISSING_ARG", result.getErrorCode());
    }
    
    @Test
    void testWrongTypeForPositionalArg() {
        ActionStatement action = new ActionStatement(
            "deploy",
            List.of(123), // Should be String, not Integer
            Map.of("version", 2),
            null, null, null, null
        );
        
        SchemaValidator.ValidationResult result = validator.validate(action);
        
        assertFalse(result.isValid());
        assertEquals("TYPE_MISMATCH", result.getErrorCode());
    }
    
    @Test
    void testOptionalArgNotRequired() {
        ActionStatement action = new ActionStatement(
            "deploy",
            List.of("myapp"),
            Collections.emptyMap(), // Optional args not provided
            null, null, null, null
        );
        
        SchemaValidator.ValidationResult result = validator.validate(action);
        
        assertTrue(result.isValid());
    }
    
    @Test
    void testToErrorStatement() {
        SchemaValidator.ValidationResult result = SchemaValidator.ValidationResult.failure(
            "INVALID_ARGS",
            "Test error message"
        );
        
        assertNotNull(result.toErrorStatement());
        assertEquals("INVALID_ARGS", result.toErrorStatement().getErrorCode());
        assertEquals("Test error message", result.toErrorStatement().getMessage());
    }
    
    @Test
    void testSchemaWithAllowExtraArgs() {
        VerbSchema flexibleSchema = new VerbSchema.Builder("flexibleVerb")
            .addNamedArg("required", VerbSchema.ArgType.STRING, true, "Required arg")
            .allowExtraArgs(true)
            .build();
        registry.register(flexibleSchema);
        
        ActionStatement action = new ActionStatement(
            "flexibleVerb",
            Collections.emptyList(),
            Map.of("required", "value", "extra", "allowed"),
            null, null, null, null
        );
        
        SchemaValidator.ValidationResult result = validator.validate(action);
        
        assertTrue(result.isValid());
    }
    
    @Test
    void testRegistryOperations() {
        assertEquals(2, registry.size());
        assertTrue(registry.hasSchema("reorder"));
        assertTrue(registry.hasSchema("deploy"));
        
        VerbSchema schema = registry.getSchema("reorder");
        assertNotNull(schema);
        assertEquals("reorder", schema.getVerbName());
        
        registry.unregister("reorder");
        assertFalse(registry.hasSchema("reorder"));
        assertEquals(1, registry.size());
        
        registry.clear();
        assertEquals(0, registry.size());
    }
}
