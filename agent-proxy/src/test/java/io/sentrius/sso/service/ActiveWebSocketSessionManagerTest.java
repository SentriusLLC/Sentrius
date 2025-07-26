package io.sentrius.sso.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.reactive.socket.HandshakeInfo;

import java.net.InetSocketAddress;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ActiveWebSocketSessionManagerTest {

    @Mock
    private WebSocketSession webSocketSession;

    @Mock
    private HandshakeInfo handshakeInfo;

    private ActiveWebSocketSessionManager sessionManager;

    @BeforeEach
    void setUp() {
        sessionManager = new ActiveWebSocketSessionManager();
    }

    @Test
    void testRegisterAndUnregisterSession() {
        // Given
        String sessionId = "test-session-1";
        when(webSocketSession.getId()).thenReturn(sessionId);
        when(webSocketSession.isOpen()).thenReturn(true);
        when(webSocketSession.getHandshakeInfo()).thenReturn(handshakeInfo);
        when(handshakeInfo.getRemoteAddress()).thenReturn(new InetSocketAddress("127.0.0.1", 8080));

        // When - register session
        sessionManager.register(sessionId, webSocketSession);

        // Then - session should be active
        assertEquals(webSocketSession, sessionManager.get(sessionId));
        assertEquals(1, sessionManager.getActiveSessions().size());
        assertEquals(0, sessionManager.getAgentSessionDurations().size());

        // When - unregister session
        sessionManager.unregister(sessionId);

        // Then - session should be removed and duration recorded
        assertNull(sessionManager.get(sessionId));
        assertEquals(0, sessionManager.getActiveSessions().size());
        assertEquals(1, sessionManager.getAgentSessionDurations().size());

        // Verify session duration data
        List<Map<String, Object>> completedSessions = sessionManager.getAgentSessionDurations();
        Map<String, Object> sessionData = completedSessions.get(0);
        assertEquals(sessionId, sessionData.get("sessionId"));
        assertEquals("agent", sessionData.get("sessionType"));
        assertNotNull(sessionData.get("startTime"));
        assertNotNull(sessionData.get("endTime"));
        assertNotNull(sessionData.get("durationMinutes"));
        assertTrue((Long) sessionData.get("durationMinutes") >= 0);
    }

    @Test
    void testGetActiveAgentSessionDurations() throws InterruptedException {
        // Given
        String sessionId = "active-session-1";

        // When
        sessionManager.register(sessionId, webSocketSession);
        
        // Wait a moment to ensure some time passes
        Thread.sleep(100);

        // Then
        List<Map<String, Object>> activeSessions = sessionManager.getActiveAgentSessionDurations();
        assertEquals(1, activeSessions.size());

        Map<String, Object> activeSession = activeSessions.get(0);
        assertEquals(sessionId, activeSession.get("sessionId"));
        assertEquals("agent", activeSession.get("sessionType"));
        assertEquals(true, activeSession.get("active"));
        assertNotNull(activeSession.get("startTime"));
        assertNotNull(activeSession.get("durationMinutes"));
        assertTrue((Long) activeSession.get("durationMinutes") >= 0);
    }

    @Test
    void testMultipleSessionsHandling() {
        // Given
        String sessionId1 = "session-1";
        String sessionId2 = "session-2";
        WebSocketSession session1 = mock(WebSocketSession.class);
        WebSocketSession session2 = mock(WebSocketSession.class);
        
        when(session1.getId()).thenReturn(sessionId1);
        when(session1.isOpen()).thenReturn(true);
        when(session1.getHandshakeInfo()).thenReturn(handshakeInfo);
        when(session2.getId()).thenReturn(sessionId2);
        when(session2.isOpen()).thenReturn(true);
        when(session2.getHandshakeInfo()).thenReturn(handshakeInfo);
        when(handshakeInfo.getRemoteAddress()).thenReturn(new InetSocketAddress("127.0.0.1", 8080));

        // When
        sessionManager.register(sessionId1, session1);
        sessionManager.register(sessionId2, session2);

        // Then
        assertEquals(2, sessionManager.getActiveSessions().size());
        assertEquals(2, sessionManager.getActiveAgentSessionDurations().size());

        // When - unregister one session
        sessionManager.unregister(sessionId1);

        // Then
        assertEquals(1, sessionManager.getActiveSessions().size());
        assertEquals(1, sessionManager.getActiveAgentSessionDurations().size());
        assertEquals(1, sessionManager.getAgentSessionDurations().size());
    }

    @Test
    void testUnregisterNonExistentSession() {
        // Given
        String nonExistentSessionId = "non-existent";

        // When
        sessionManager.unregister(nonExistentSessionId);

        // Then - should not throw exception and should not affect other data
        assertEquals(0, sessionManager.getActiveSessions().size());
        assertEquals(0, sessionManager.getAgentSessionDurations().size());
        assertEquals(0, sessionManager.getActiveAgentSessionDurations().size());
    }
}