package com.sentrius.sag;

import com.sentrius.sag.model.*;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class CorrelationEngineTest {
    
    @Test
    void testCreateResponseHeader() {
        CorrelationEngine engine = new CorrelationEngine("agent1");
        
        Header header = engine.createResponseHeader("agent1", "agent2");
        
        assertNotNull(header);
        assertEquals("agent1", header.getSource());
        assertEquals("agent2", header.getDestination());
        assertTrue(header.getMessageId().startsWith("agent1-"));
    }
    
    @Test
    void testAutoCorrelation() throws SAGParseException {
        CorrelationEngine engine = new CorrelationEngine("agent1");
        
        // Parse an incoming message
        String input = "H v 1 id=msg1 src=agent2 dst=agent1 ts=1234567890\nDO query()";
        Message incoming = SAGMessageParser.parse(input);
        
        // Record it
        engine.recordIncoming(incoming);
        
        // Create a response header
        Header responseHeader = engine.createResponseHeader("agent1", "agent2");
        
        // Should have correlation set to the incoming message ID
        assertEquals("msg1", responseHeader.getCorrelation());
    }
    
    @Test
    void testCreateHeaderInResponseTo() throws SAGParseException {
        CorrelationEngine engine = new CorrelationEngine("agent1");
        
        String input = "H v 1 id=msg1 src=agent2 dst=agent1 ts=1234567890\nDO query()";
        Message incoming = SAGMessageParser.parse(input);
        
        Header responseHeader = engine.createHeaderInResponseTo("agent1", "agent2", incoming);
        
        assertEquals("msg1", responseHeader.getCorrelation());
    }
    
    @Test
    void testGenerateUniqueMessageIds() {
        CorrelationEngine engine = new CorrelationEngine("agent1");
        
        String id1 = engine.generateMessageId();
        String id2 = engine.generateMessageId();
        String id3 = engine.generateMessageId();
        
        assertNotEquals(id1, id2);
        assertNotEquals(id2, id3);
        assertNotEquals(id1, id3);
        
        assertTrue(id1.startsWith("agent1-"));
        assertTrue(id2.startsWith("agent1-"));
        assertTrue(id3.startsWith("agent1-"));
    }
    
    @Test
    void testTraceThread() throws SAGParseException {
        // Create a chain of messages: msg1 -> msg2 -> msg3
        Message msg1 = SAGMessageParser.parse(
            "H v 1 id=msg1 src=agent1 dst=agent2 ts=1000\nDO start()");
        
        Message msg2 = SAGMessageParser.parse(
            "H v 1 id=msg2 src=agent2 dst=agent3 ts=2000 corr=msg1\nDO process()");
        
        Message msg3 = SAGMessageParser.parse(
            "H v 1 id=msg3 src=agent3 dst=agent1 ts=3000 corr=msg2\nDO finish()");
        
        List<Message> allMessages = Arrays.asList(msg1, msg2, msg3);
        
        // Trace from msg3 back to msg1
        List<Message> thread = CorrelationEngine.traceThread(allMessages, "msg3");
        
        assertEquals(3, thread.size());
        assertEquals("msg1", thread.get(0).getHeader().getMessageId());
        assertEquals("msg2", thread.get(1).getHeader().getMessageId());
        assertEquals("msg3", thread.get(2).getHeader().getMessageId());
    }
    
    @Test
    void testFindResponses() throws SAGParseException {
        Message msg1 = SAGMessageParser.parse(
            "H v 1 id=msg1 src=agent1 dst=agent2 ts=1000\nDO start()");
        
        Message msg2 = SAGMessageParser.parse(
            "H v 1 id=msg2 src=agent2 dst=agent3 ts=2000 corr=msg1\nDO process()");
        
        Message msg3 = SAGMessageParser.parse(
            "H v 1 id=msg3 src=agent3 dst=agent1 ts=3000 corr=msg1\nDO finish()");
        
        List<Message> allMessages = Arrays.asList(msg1, msg2, msg3);
        
        // Find all responses to msg1
        List<Message> responses = CorrelationEngine.findResponses(allMessages, "msg1");
        
        assertEquals(2, responses.size());
        assertTrue(responses.stream().anyMatch(m -> m.getHeader().getMessageId().equals("msg2")));
        assertTrue(responses.stream().anyMatch(m -> m.getHeader().getMessageId().equals("msg3")));
    }
    
    @Test
    void testBuildConversationTree() throws SAGParseException {
        Message msg1 = SAGMessageParser.parse(
            "H v 1 id=msg1 src=agent1 dst=agent2 ts=1000\nDO start()");
        
        Message msg2 = SAGMessageParser.parse(
            "H v 1 id=msg2 src=agent2 dst=agent3 ts=2000 corr=msg1\nDO process()");
        
        Message msg3 = SAGMessageParser.parse(
            "H v 1 id=msg3 src=agent3 dst=agent1 ts=3000 corr=msg1\nDO finish()");
        
        Message msg4 = SAGMessageParser.parse(
            "H v 1 id=msg4 src=agent1 dst=agent2 ts=4000 corr=msg2\nDO acknowledge()");
        
        List<Message> allMessages = Arrays.asList(msg1, msg2, msg3, msg4);
        
        Map<String, List<String>> tree = CorrelationEngine.buildConversationTree(allMessages);
        
        // msg1 should have two direct responses: msg2 and msg3
        assertTrue(tree.containsKey("msg1"));
        assertEquals(2, tree.get("msg1").size());
        assertTrue(tree.get("msg1").contains("msg2"));
        assertTrue(tree.get("msg1").contains("msg3"));
        
        // msg2 should have one response: msg4
        assertTrue(tree.containsKey("msg2"));
        assertEquals(1, tree.get("msg2").size());
        assertTrue(tree.get("msg2").contains("msg4"));
    }
    
    @Test
    void testClear() {
        CorrelationEngine engine = new CorrelationEngine("agent1");
        
        Header header1 = engine.createResponseHeader("agent1", "agent2");
        assertNull(header1.getCorrelation());
        
        // Record a message
        Message msg = new Message(
            new Header(1, "msg1", "agent2", "agent1", 1000, null, null),
            Collections.emptyList()
        );
        engine.recordIncoming(msg);
        
        Header header2 = engine.createResponseHeader("agent1", "agent2");
        assertEquals("msg1", header2.getCorrelation());
        
        // Clear and verify correlation is gone
        engine.clear();
        Header header3 = engine.createResponseHeader("agent1", "agent2");
        assertNull(header3.getCorrelation());
    }
}
