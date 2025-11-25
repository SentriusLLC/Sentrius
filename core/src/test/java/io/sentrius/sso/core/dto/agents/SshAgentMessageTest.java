package io.sentrius.sso.core.dto.agents;

import org.junit.jupiter.api.Test;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SSH Agent message DTOs
 */
class SshAgentMessageTest {

    @Test
    void testSshAgentQueryMessageCreation() {
        // Given
        String queryId = "test-query-123";
        String userId = "user-456";
        String username = "testuser";
        String sessionId = "session-789";
        String query = "How do I list files?";
        String chatGroupId = "chat-group-1";
        Instant timestamp = Instant.now();

        // When
        SshAgentQueryMessage message = SshAgentQueryMessage.builder()
            .queryId(queryId)
            .userId(userId)
            .username(username)
            .sessionId(sessionId)
            .query(query)
            .chatGroupId(chatGroupId)
            .timestamp(timestamp)
            .build();

        // Then
        assertNotNull(message);
        assertEquals(queryId, message.getQueryId());
        assertEquals(userId, message.getUserId());
        assertEquals(username, message.getUsername());
        assertEquals(sessionId, message.getSessionId());
        assertEquals(query, message.getQuery());
        assertEquals(chatGroupId, message.getChatGroupId());
        assertEquals(timestamp, message.getTimestamp());
    }

    @Test
    void testSshAgentResponseMessageCreation() {
        // Given
        String queryId = "test-query-123";
        String userId = "user-456";
        String sessionId = "session-789";
        String response = "Use the 'ls' command to list files.";
        String chatGroupId = "chat-group-1";
        String agentId = "ssh-response-agent";
        String status = "success";
        Instant timestamp = Instant.now();

        // When
        SshAgentResponseMessage message = SshAgentResponseMessage.builder()
            .queryId(queryId)
            .userId(userId)
            .sessionId(sessionId)
            .response(response)
            .chatGroupId(chatGroupId)
            .agentId(agentId)
            .status(status)
            .timestamp(timestamp)
            .build();

        // Then
        assertNotNull(message);
        assertEquals(queryId, message.getQueryId());
        assertEquals(userId, message.getUserId());
        assertEquals(sessionId, message.getSessionId());
        assertEquals(response, message.getResponse());
        assertEquals(chatGroupId, message.getChatGroupId());
        assertEquals(agentId, message.getAgentId());
        assertEquals(status, message.getStatus());
        assertEquals(timestamp, message.getTimestamp());
    }

    @Test
    void testSshAgentResponseMessageWithError() {
        // Given
        String queryId = "test-query-123";
        String userId = "user-456";
        String sessionId = "session-789";
        String status = "error";
        String errorMessage = "Agent processing failed";

        // When
        SshAgentResponseMessage message = SshAgentResponseMessage.builder()
            .queryId(queryId)
            .userId(userId)
            .sessionId(sessionId)
            .status(status)
            .errorMessage(errorMessage)
            .build();

        // Then
        assertNotNull(message);
        assertEquals(status, message.getStatus());
        assertEquals(errorMessage, message.getErrorMessage());
    }

    @Test
    void testNoArgsConstructor() {
        // Test that no-args constructors work (required for serialization)
        SshAgentQueryMessage queryMessage = new SshAgentQueryMessage();
        assertNotNull(queryMessage);

        SshAgentResponseMessage responseMessage = new SshAgentResponseMessage();
        assertNotNull(responseMessage);
    }
}
