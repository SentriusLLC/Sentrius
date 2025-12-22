package com.sentrius.sag;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExpressionEvaluatorTest {
    
    @Test
    void testEvaluateSimpleComparison() throws SAGParseException {
        MapContext context = new MapContext();
        context.set("balance", 1500);
        
        Object result = ExpressionEvaluator.evaluate("balance > 1000", context);
        
        assertTrue(result instanceof Boolean);
        assertTrue((Boolean) result);
    }
    
    @Test
    void testEvaluateFailedComparison() throws SAGParseException {
        MapContext context = new MapContext();
        context.set("balance", 400);
        
        Object result = ExpressionEvaluator.evaluate("balance > 1000", context);
        
        assertTrue(result instanceof Boolean);
        assertFalse((Boolean) result);
    }
    
    @Test
    void testEvaluateEquality() throws SAGParseException {
        MapContext context = new MapContext();
        context.set("status", "active");
        
        Object result = ExpressionEvaluator.evaluate("status == \"active\"", context);
        
        assertTrue(result instanceof Boolean);
        assertTrue((Boolean) result);
    }
    
    @Test
    void testEvaluateLogicalAnd() throws SAGParseException {
        MapContext context = new MapContext();
        context.set("balance", 1500);
        context.set("verified", true);
        
        Object result = ExpressionEvaluator.evaluate("(balance > 1000) && (verified == true)", context);
        
        assertTrue(result instanceof Boolean);
        assertTrue((Boolean) result);
    }
    
    @Test
    void testEvaluateLogicalOr() throws SAGParseException {
        MapContext context = new MapContext();
        context.set("balance", 400);
        context.set("verified", true);
        
        Object result = ExpressionEvaluator.evaluate("(balance > 1000) || (verified == true)", context);
        
        assertTrue(result instanceof Boolean);
        assertTrue((Boolean) result);
    }
    
    @Test
    void testEvaluateArithmetic() throws SAGParseException {
        MapContext context = new MapContext();
        context.set("price", 100);
        context.set("quantity", 5);
        
        Object result = ExpressionEvaluator.evaluate("price * quantity", context);
        
        assertTrue(result instanceof Double);
        assertEquals(500.0, (Double) result);
    }
    
    @Test
    void testEvaluateNestedPath() throws SAGParseException {
        MapContext context = new MapContext();
        context.set("user.balance", 1500);
        
        Object result = ExpressionEvaluator.evaluate("user.balance > 1000", context);
        
        assertTrue(result instanceof Boolean);
        assertTrue((Boolean) result);
    }
    
    @Test
    void testEvaluateBooleanValue() throws SAGParseException {
        MapContext context = new MapContext();
        context.set("active", true);
        
        Object result = ExpressionEvaluator.evaluate("active", context);
        
        assertTrue(result instanceof Boolean);
        assertTrue((Boolean) result);
    }
    
    @Test
    void testEvaluateNullValue() throws SAGParseException {
        MapContext context = new MapContext();
        context.set("value", null);
        
        Object result = ExpressionEvaluator.evaluate("value == null", context);
        
        assertTrue(result instanceof Boolean);
        assertTrue((Boolean) result);
    }
}
