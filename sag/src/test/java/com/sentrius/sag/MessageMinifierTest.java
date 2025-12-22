package com.sentrius.sag;

import com.sentrius.sag.model.*;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class MessageMinifierTest {
    
    @Test
    void testMinifySimpleAction() throws SAGParseException {
        String input = "H v 1 id=msg1 src=svc1 dst=svc2 ts=1234567890\n" +
                       "DO deploy()";
        
        Message message = SAGMessageParser.parse(input);
        String minified = MessageMinifier.toMinifiedString(message);
        
        assertNotNull(minified);
        assertTrue(minified.startsWith("H v 1 id=msg1 src=svc1 dst=svc2 ts=1234567890\n"));
        assertTrue(minified.contains("DO deploy()"));
    }
    
    @Test
    void testMinifyActionWithArguments() throws SAGParseException {
        String input = "H v 1 id=msg1 src=svc1 dst=svc2 ts=1234567890\n" +
                       "DO deploy(\"app1\", 42)";
        
        Message message = SAGMessageParser.parse(input);
        String minified = MessageMinifier.toMinifiedString(message);
        
        assertTrue(minified.contains("DO deploy(\"app1\",42)"));
    }
    
    @Test
    void testMinifyActionWithNamedArgs() throws SAGParseException {
        String input = "H v 1 id=msg1 src=svc1 dst=svc2 ts=1234567890\n" +
                       "DO deploy(app=\"app1\", version=2)";
        
        Message message = SAGMessageParser.parse(input);
        String minified = MessageMinifier.toMinifiedString(message);
        
        assertTrue(minified.contains("DO deploy(app=\"app1\",version=2)"));
    }
    
    @Test
    void testMinifyActionWithPolicy() throws SAGParseException {
        String input = "H v 1 id=msg1 src=svc1 dst=svc2 ts=1234567890\n" +
                       "DO deploy() P:security PRIO=HIGH BECAUSE \"security update\"";
        
        Message message = SAGMessageParser.parse(input);
        String minified = MessageMinifier.toMinifiedString(message);
        
        assertTrue(minified.contains("P:security"));
        assertTrue(minified.contains("PRIO=HIGH"));
        assertTrue(minified.contains("BECAUSE \"security update\""));
    }
    
    @Test
    void testMinifyMultipleStatements() throws SAGParseException {
        String input = "H v 1 id=msg1 src=svc1 dst=svc2 ts=1234567890\n" +
                       "DO start(); A ready = true; Q status";
        
        Message message = SAGMessageParser.parse(input);
        String minified = MessageMinifier.toMinifiedString(message);
        
        assertTrue(minified.contains("DO start();"));
        assertTrue(minified.contains("A ready = true;"));
        assertTrue(minified.contains("Q status"));
    }
    
    @Test
    void testMinifyWithCorrelation() throws SAGParseException {
        String input = "H v 1 id=msg1 src=svc1 dst=svc2 ts=1234567890 corr=parent123\n" +
                       "DO test()";
        
        Message message = SAGMessageParser.parse(input);
        String minified = MessageMinifier.toMinifiedString(message);
        
        assertTrue(minified.contains("corr=parent123"));
    }
    
    @Test
    void testMinifyError() throws SAGParseException {
        String input = "H v 1 id=msg1 src=svc1 dst=svc2 ts=1234567890\n" +
                       "ERR TIMEOUT \"Connection timed out\"";
        
        Message message = SAGMessageParser.parse(input);
        String minified = MessageMinifier.toMinifiedString(message);
        
        assertTrue(minified.contains("ERR TIMEOUT \"Connection timed out\""));
    }
    
    @Test
    void testTokenCounting() {
        String message = "H v 1 id=msg1 src=svc1 dst=svc2 ts=1234567890\nDO deploy()";
        int tokens = MessageMinifier.countTokens(message);
        
        assertTrue(tokens > 0);
        // Message is 60 chars, so roughly 15 tokens at 4 chars/token
        assertTrue(tokens >= 13 && tokens <= 17);
    }
    
    @Test
    void testCompareWithJSON() throws SAGParseException {
        String input = "H v 1 id=msg1 src=svc1 dst=svc2 ts=1234567890\n" +
                       "DO deploy(\"app1\")";
        
        Message message = SAGMessageParser.parse(input);
        MessageMinifier.TokenComparison comparison = MessageMinifier.compareWithJSON(message);
        
        assertNotNull(comparison);
        assertTrue(comparison.getSagLength() > 0);
        assertTrue(comparison.getJsonLength() > 0);
        assertTrue(comparison.getSagTokens() > 0);
        assertTrue(comparison.getJsonTokens() > 0);
        
        // SAG should generally be more compact than JSON
        assertTrue(comparison.getSagLength() < comparison.getJsonLength());
        assertTrue(comparison.getTokensSaved() > 0);
        assertTrue(comparison.getPercentSaved() > 0);
    }
    
    @Test
    void testMinifyAndReparse() throws SAGParseException {
        String input = "H v 1 id=msg1 src=svc1 dst=svc2 ts=1234567890\n" +
                       "DO deploy(\"app1\", version=2)";
        
        Message message = SAGMessageParser.parse(input);
        String minified = MessageMinifier.toMinifiedString(message);
        
        // The minified version should be parseable
        Message reparsed = SAGMessageParser.parse(minified);
        
        assertNotNull(reparsed);
        assertEquals(message.getHeader().getMessageId(), reparsed.getHeader().getMessageId());
        assertEquals(message.getStatements().size(), reparsed.getStatements().size());
    }
}
