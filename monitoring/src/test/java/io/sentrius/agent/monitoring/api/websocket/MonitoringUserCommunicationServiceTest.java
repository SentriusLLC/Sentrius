package io.sentrius.agent.monitoring.api.websocket;

import io.sentrius.agent.monitoring.model.MonitoringWebSocky;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketSession;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test for MonitoringUserCommunicationService
 */
class MonitoringUserCommunicationServiceTest {

    private MonitoringUserCommunicationService service;

    @BeforeEach
    void setUp() {
        service = new MonitoringUserCommunicationService();
    }

    @Test
    void testCreateSession() {
        // Arrange
        String sessionId = UUID.randomUUID().toString();
        WebSocketSession mockSession = mock(WebSocketSession.class);

        // Act
        MonitoringWebSocky result = service.createSession(sessionId, mockSession);

        // Assert
        assertNotNull(result);
        assertEquals(sessionId, result.getSessionId());
        assertEquals(mockSession, result.getWebSocketSession());
        assertNotNull(result.getUniqueIdentifier());
    }

    @Test
    void testGetSession() {
        // Arrange
        String sessionId = UUID.randomUUID().toString();
        WebSocketSession mockSession = mock(WebSocketSession.class);
        service.createSession(sessionId, mockSession);

        // Act
        var result = service.getSession(sessionId);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(sessionId, result.get().getSessionId());
    }

    @Test
    void testGetSessionNotFound() {
        // Arrange
        String sessionId = UUID.randomUUID().toString();

        // Act
        var result = service.getSession(sessionId);

        // Assert
        assertFalse(result.isPresent());
    }

    @Test
    void testRemoveSession() {
        // Arrange
        String sessionId = UUID.randomUUID().toString();
        WebSocketSession mockSession = mock(WebSocketSession.class);
        service.createSession(sessionId, mockSession);

        // Act
        service.remove(sessionId);
        var result = service.getSession(sessionId);

        // Assert
        assertFalse(result.isPresent());
    }
}
