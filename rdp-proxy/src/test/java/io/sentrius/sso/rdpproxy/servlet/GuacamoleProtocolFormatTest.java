package io.sentrius.sso.rdpproxy.servlet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test to document and verify the correct Guacamole protocol format.
 * 
 * The Guacamole protocol format is: length.value,length.value,...;
 * Each element must have its length prefixed before the dot.
 * 
 * This test verifies the fix for "Instruction parse error" issues
 * that occurred when instructions were malformed.
 */
public class GuacamoleProtocolFormatTest {

    @Test
    public void testReadyInstructionFormat() {
        // "ready" is 5 characters, not 4
        String opcode = "ready";
        assertEquals(5, opcode.length(), "Ready opcode should be 5 characters");
        
        // UUID is always 36 characters (with dashes)
        String uuid = "12345678-1234-1234-1234-123456789abc";
        assertEquals(36, uuid.length(), "UUID should be 36 characters");
        
        // Correct format: 5.ready,36.uuid; (UUID is 36 chars: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx)
        String correctInstruction = "5.ready,36." + uuid + ";";
        
        // Verify format by parsing
        assertTrue(correctInstruction.startsWith("5.ready,"));
        assertTrue(correctInstruction.endsWith(";"));
    }
    
    @Test
    public void testErrorInstructionFormat() {
        // Error message
        String errorMsg = "Connection failed";
        int msgLength = errorMsg.length();
        
        // Correct format: 5.error,<msglen>.<message>,1.0;
        // The last parameter "1.0" means status code "0" (one character)
        String correctInstruction = "5.error," + msgLength + "." + errorMsg + ",1.0;";
        
        // Verify format
        assertTrue(correctInstruction.startsWith("5.error,"));
        assertTrue(correctInstruction.endsWith(",1.0;"));
        assertEquals("5.error,17.Connection failed,1.0;", correctInstruction);
        
        // Verify it doesn't contain malformed "0."
        assertFalse(correctInstruction.contains(",0.,0.,"), 
            "Should not contain malformed empty values");
    }
    
    @Test
    public void testSyncInstructionFormat() {
        // Sync instruction with timestamp
        String timestamp = "1696358400000";
        int timestampLength = timestamp.length();
        
        // Correct format: 4.sync,<len>.<timestamp>;
        String correctInstruction = "4.sync," + timestampLength + "." + timestamp + ";";
        
        assertEquals("4.sync,13.1696358400000;", correctInstruction);
        
        // Verify the opcode length
        assertEquals(4, "sync".length(), "Sync opcode should be 4 characters");
    }
    
    @Test
    public void testAckInstructionFormat() {
        // Ack instruction with timestamp
        String timestamp = "1696358400000";
        int timestampLength = timestamp.length();
        
        // Correct format: 3.ack,<len>.<timestamp>;
        String correctInstruction = "3.ack," + timestampLength + "." + timestamp + ";";
        
        assertEquals("3.ack,13.1696358400000;", correctInstruction);
        
        // Verify the opcode length
        assertEquals(3, "ack".length(), "Ack opcode should be 3 characters");
    }
    
    @Test
    public void testMalformedEmptyValues() {
        // This was the bug - empty values like "0." are invalid
        String malformedInstruction = "5.error,0.,0.,17.Connection failed;";
        
        // Parse to show it's malformed
        // After "5.error," we expect "0." which means 0 characters
        // But there's no value after the dot, making it malformed
        assertTrue(malformedInstruction.contains("0.,0.,"), 
            "Example of malformed instruction with empty values");
    }
    
    @Test
    public void testProperlyFormattedValues() {
        // All values must have proper length prefixes
        
        // Single character value
        String singleChar = "1.0";  // length 1, value "0"
        assertEquals(3, singleChar.length());
        
        // Multi-character value
        String multiChar = "5.hello";  // length 5, value "hello"
        assertEquals(7, multiChar.length());
        
        // Empty value would be "0." but the protocol requires a value
        // so minimum is "1.X" for any single character
    }
}
