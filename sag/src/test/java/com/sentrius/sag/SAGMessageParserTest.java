package com.sentrius.sag;

import com.sentrius.sag.model.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SAGMessageParserTest {

    @Test
    void testParseSimpleAction() throws SAGParseException {
        String input = "H v 1 id=msg1 src=svc1 dst=svc2 ts=1234567890\n" +
                       "DO deploy()";
        
        Message message = SAGMessageParser.parse(input);
        
        assertNotNull(message);
        assertNotNull(message.getHeader());
        assertEquals(1, message.getHeader().getVersion());
        assertEquals("msg1", message.getHeader().getMessageId());
        assertEquals("svc1", message.getHeader().getSource());
        assertEquals("svc2", message.getHeader().getDestination());
        assertEquals(1234567890L, message.getHeader().getTimestamp());
        
        assertEquals(1, message.getStatements().size());
        Statement stmt = message.getStatements().get(0);
        assertInstanceOf(ActionStatement.class, stmt);
        ActionStatement action = (ActionStatement) stmt;
        assertEquals("deploy", action.getVerb());
    }

    @Test
    void testParseActionWithArguments() throws SAGParseException {
        String input = "H v 1 id=msg1 src=svc1 dst=svc2 ts=1234567890\n" +
                       "DO deploy(\"app1\", 42)";
        
        Message message = SAGMessageParser.parse(input);
        
        assertEquals(1, message.getStatements().size());
        ActionStatement action = (ActionStatement) message.getStatements().get(0);
        assertEquals("deploy", action.getVerb());
        assertEquals(2, action.getArgs().size());
        assertEquals("app1", action.getArgs().get(0));
        assertEquals(42, action.getArgs().get(1));
    }

    @Test
    void testParseActionWithNamedArguments() throws SAGParseException {
        String input = "H v 1 id=msg1 src=svc1 dst=svc2 ts=1234567890\n" +
                       "DO deploy(app=\"app1\", version=2)";
        
        Message message = SAGMessageParser.parse(input);
        
        ActionStatement action = (ActionStatement) message.getStatements().get(0);
        assertEquals("deploy", action.getVerb());
        assertEquals(2, action.getNamedArgs().size());
        assertEquals("app1", action.getNamedArgs().get("app"));
        assertEquals(2, action.getNamedArgs().get("version"));
    }

    @Test
    void testParseActionWithPolicy() throws SAGParseException {
        String input = "H v 1 id=msg1 src=svc1 dst=svc2 ts=1234567890\n" +
                       "DO deploy() P:security PRIO=HIGH BECAUSE \"security update\"";
        
        Message message = SAGMessageParser.parse(input);
        
        ActionStatement action = (ActionStatement) message.getStatements().get(0);
        assertEquals("deploy", action.getVerb());
        assertEquals("security", action.getPolicy());
        assertEquals("HIGH", action.getPriority());
        assertEquals("security update", action.getReason());
    }

    @Test
    void testParseQueryStatement() throws SAGParseException {
        String input = "H v 1 id=msg1 src=svc1 dst=svc2 ts=1234567890\n" +
                       "Q status.health";
        
        Message message = SAGMessageParser.parse(input);
        
        assertEquals(1, message.getStatements().size());
        assertInstanceOf(QueryStatement.class, message.getStatements().get(0));
        QueryStatement query = (QueryStatement) message.getStatements().get(0);
        assertEquals("status.health", query.getExpression());
    }

    @Test
    void testParseQueryWithConstraint() throws SAGParseException {
        String input = "H v 1 id=msg1 src=svc1 dst=svc2 ts=1234567890\n" +
                       "Q status WHERE healthy==true";
        
        Message message = SAGMessageParser.parse(input);
        
        QueryStatement query = (QueryStatement) message.getStatements().get(0);
        assertNotNull(query.getExpression());
        assertNotNull(query.getConstraint());
    }

    @Test
    void testParseAssertStatement() throws SAGParseException {
        String input = "H v 1 id=msg1 src=svc1 dst=svc2 ts=1234567890\n" +
                       "A status.ready = true";
        
        Message message = SAGMessageParser.parse(input);
        
        assertEquals(1, message.getStatements().size());
        assertInstanceOf(AssertStatement.class, message.getStatements().get(0));
        AssertStatement assertStmt = (AssertStatement) message.getStatements().get(0);
        assertEquals("status.ready", assertStmt.getPath());
        assertEquals(true, assertStmt.getValue());
    }

    @Test
    void testParseControlStatement() throws SAGParseException {
        String input = "H v 1 id=msg1 src=svc1 dst=svc2 ts=1234567890\n" +
                       "IF ready==true THEN DO start() ELSE DO wait()";
        
        Message message = SAGMessageParser.parse(input);
        
        assertEquals(1, message.getStatements().size());
        assertInstanceOf(ControlStatement.class, message.getStatements().get(0));
        ControlStatement ctrl = (ControlStatement) message.getStatements().get(0);
        assertNotNull(ctrl.getCondition());
        assertNotNull(ctrl.getThenStatement());
        assertNotNull(ctrl.getElseStatement());
        assertInstanceOf(ActionStatement.class, ctrl.getThenStatement());
        assertInstanceOf(ActionStatement.class, ctrl.getElseStatement());
    }

    @Test
    void testParseEventStatement() throws SAGParseException {
        String input = "H v 1 id=msg1 src=svc1 dst=svc2 ts=1234567890\n" +
                       "EVT userLogin(\"user123\")";
        
        Message message = SAGMessageParser.parse(input);
        
        assertEquals(1, message.getStatements().size());
        assertInstanceOf(EventStatement.class, message.getStatements().get(0));
        EventStatement event = (EventStatement) message.getStatements().get(0);
        assertEquals("userLogin", event.getEventName());
        assertEquals(1, event.getArgs().size());
        assertEquals("user123", event.getArgs().get(0));
    }

    @Test
    void testParseErrorStatement() throws SAGParseException {
        String input = "H v 1 id=msg1 src=svc1 dst=svc2 ts=1234567890\n" +
                       "ERR TIMEOUT \"Connection timed out\"";
        
        Message message = SAGMessageParser.parse(input);
        
        assertEquals(1, message.getStatements().size());
        assertInstanceOf(ErrorStatement.class, message.getStatements().get(0));
        ErrorStatement error = (ErrorStatement) message.getStatements().get(0);
        assertEquals("TIMEOUT", error.getErrorCode());
        assertEquals("Connection timed out", error.getMessage());
    }

    @Test
    void testParseMultipleStatements() throws SAGParseException {
        String input = "H v 1 id=msg1 src=svc1 dst=svc2 ts=1234567890\n" +
                       "DO start(); A ready = true; Q status";
        
        Message message = SAGMessageParser.parse(input);
        
        assertEquals(3, message.getStatements().size());
        assertInstanceOf(ActionStatement.class, message.getStatements().get(0));
        assertInstanceOf(AssertStatement.class, message.getStatements().get(1));
        assertInstanceOf(QueryStatement.class, message.getStatements().get(2));
    }

    @Test
    void testParseHeaderWithCorrelation() throws SAGParseException {
        String input = "H v 1 id=msg1 src=svc1 dst=svc2 ts=1234567890 corr=parent123\n" +
                       "DO test()";
        
        Message message = SAGMessageParser.parse(input);
        
        assertEquals("parent123", message.getHeader().getCorrelation());
    }

    @Test
    void testParseHeaderWithTTL() throws SAGParseException {
        String input = "H v 1 id=msg1 src=svc1 dst=svc2 ts=1234567890 ttl=30\n" +
                       "DO test()";
        
        Message message = SAGMessageParser.parse(input);
        
        assertEquals(30, message.getHeader().getTtl());
    }

    @Test
    void testParseHeaderWithCorrelationAndTTL() throws SAGParseException {
        String input = "H v 1 id=msg1 src=svc1 dst=svc2 ts=1234567890 corr=parent123 ttl=30\n" +
                       "DO test()";
        
        Message message = SAGMessageParser.parse(input);
        
        assertEquals("parent123", message.getHeader().getCorrelation());
        assertEquals(30, message.getHeader().getTtl());
    }

    @Test
    void testParseValuesInAction() throws SAGParseException {
        String input = "H v 1 id=msg1 src=svc1 dst=svc2 ts=1234567890\n" +
                       "DO test(42, 3.14, true, false, null, \"string\")";
        
        Message message = SAGMessageParser.parse(input);
        
        ActionStatement action = (ActionStatement) message.getStatements().get(0);
        assertEquals(6, action.getArgs().size());
        assertEquals(42, action.getArgs().get(0));
        assertEquals(3.14, action.getArgs().get(1));
        assertEquals(true, action.getArgs().get(2));
        assertEquals(false, action.getArgs().get(3));
        assertNull(action.getArgs().get(4));
        assertEquals("string", action.getArgs().get(5));
    }

    @Test
    void testInvalidSyntax() {
        String input = "H v 1 invalid syntax\n" +
                       "DO test()";
        
        assertThrows(SAGParseException.class, () -> SAGMessageParser.parse(input));
    }
}
