package io.sentrius.sso.provenance;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProvenanceEventTest {

    @Test
    void provenanceEventBuilderCreatesValidObject() {
        Instant timestamp = Instant.now();
        List<String> sourceDocs = Arrays.asList("doc1", "doc2");
        
        ProvenanceEvent event = ProvenanceEvent.builder()
            .eventId("test-event-id")
            .sessionId("test-session-id")
            .actor("test-agent")
            .triggeringUser("test-user")
            .eventType(ProvenanceEvent.EventType.USER_CHAT_AGENT)
            .input("test input")
            .outputSummary("test output")
            .sourceDocs(sourceDocs)
            .ztatTokenId("test-token")
            .timestamp(timestamp)
            .build();

        assertNotNull(event);
        assertEquals("test-event-id", event.getEventId());
        assertEquals("test-session-id", event.getSessionId());
        assertEquals("test-agent", event.getActor());
        assertEquals("test-user", event.getTriggeringUser());
        assertEquals(ProvenanceEvent.EventType.USER_CHAT_AGENT, event.getEventType());
        assertEquals("test input", event.getInput());
        assertEquals("test output", event.getOutputSummary());
        assertEquals(sourceDocs, event.getSourceDocs());
        assertEquals("test-token", event.getZtatTokenId());
        assertEquals(timestamp, event.getTimestamp());
    }

    @Test
    void provenanceEventCanBeCreatedWithNoArgsConstructor() {
        ProvenanceEvent event = new ProvenanceEvent();
        
        assertNotNull(event);
        assertNull(event.getEventId());
        assertNull(event.getSessionId());
        assertNotNull(event.getSourceDocs()); // Default initialized as empty list
        assertTrue(event.getSourceDocs().isEmpty());
    }

    @Test
    void provenanceEventCanBeCreatedWithAllArgsConstructor() {
        Instant timestamp = Instant.now();
        List<String> sourceDocs = Arrays.asList("doc1");
        
        ProvenanceEvent event = new ProvenanceEvent(
            "event-id", "session-id", "actor", "user", 
            ProvenanceEvent.EventType.COMMAND_EXECUTED, "input", "output",
            sourceDocs, "token", timestamp
        );

        assertEquals("event-id", event.getEventId());
        assertEquals("session-id", event.getSessionId());
        assertEquals("actor", event.getActor());
        assertEquals("user", event.getTriggeringUser());
        assertEquals(ProvenanceEvent.EventType.COMMAND_EXECUTED, event.getEventType());
        assertEquals("input", event.getInput());
        assertEquals("output", event.getOutputSummary());
        assertEquals(sourceDocs, event.getSourceDocs());
        assertEquals("token", event.getZtatTokenId());
        assertEquals(timestamp, event.getTimestamp());
    }

    @Test
    void provenanceEventSourceDocsDefaultsToEmptyList() {
        ProvenanceEvent event = ProvenanceEvent.builder()
            .eventId("test")
            .build();

        assertNotNull(event.getSourceDocs());
        assertTrue(event.getSourceDocs().isEmpty());
    }

    @Test
    void provenanceEventHandlesNullValues() {
        ProvenanceEvent event = ProvenanceEvent.builder()
            .eventId(null)
            .sessionId(null)
            .actor(null)
            .triggeringUser(null)
            .eventType(null)
            .input(null)
            .outputSummary(null)
            .sourceDocs(null)
            .ztatTokenId(null)
            .timestamp(null)
            .build();

        assertNull(event.getEventId());
        assertNull(event.getSessionId());
        assertNull(event.getActor());
        assertNull(event.getTriggeringUser());
        assertNull(event.getEventType());
        assertNull(event.getInput());
        assertNull(event.getOutputSummary());
        assertNull(event.getSourceDocs());
        assertNull(event.getZtatTokenId());
        assertNull(event.getTimestamp());
    }

    @Test
    void eventTypeEnumContainsExpectedValues() {
        ProvenanceEvent.EventType[] expectedTypes = {
            ProvenanceEvent.EventType.USER_CHAT_AGENT,
            ProvenanceEvent.EventType.INTERPRET_MESSAGE,
            ProvenanceEvent.EventType.AGENT_RESPOND,
            ProvenanceEvent.EventType.COMMAND_EXECUTED,
            ProvenanceEvent.EventType.POLICY_EVALUATION,
            ProvenanceEvent.EventType.KNOWLEDGE_USED,
            ProvenanceEvent.EventType.ENDPOINT_ACCESS,
            ProvenanceEvent.EventType.UNKNOWN,
            ProvenanceEvent.EventType.KNOWLEDGE_GENERATED,
            ProvenanceEvent.EventType.KNOWLEDGE_REQUESTED,
            ProvenanceEvent.EventType.KNOWLEDGE_DELETED
        };

        for (ProvenanceEvent.EventType type : expectedTypes) {
            assertNotNull(type);
        }
        
        assertEquals(11, ProvenanceEvent.EventType.values().length);
    }
}