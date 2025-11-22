package io.sentrius.agent.monitoring.api.websocket;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import io.sentrius.agent.monitoring.model.MonitoringWebSocky;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

/**
 * Service to manage WebSocket sessions for monitoring agent chat functionality.
 */
@Slf4j
@Service
public class MonitoringUserCommunicationService {

    private final ConcurrentHashMap<String, MonitoringWebSocky> sessions = new ConcurrentHashMap<>();

    public MonitoringWebSocky createSession(String sessionId, WebSocketSession session) {
        var websocky = MonitoringWebSocky.builder()
            .sessionId(sessionId)
            .webSocketSession(session)
            .uniqueIdentifier(UUID.fromString(sessionId).getMostSignificantBits())
            .build();
        sessions.put(sessionId, websocky);
        log.info("Created monitoring chat session: {}", sessionId);
        return websocky;
    }

    public Optional<MonitoringWebSocky> getSession(String sessionId) {
        var websocky = sessions.get(sessionId);
        return Optional.ofNullable(websocky);
    }

    public void remove(String sessionId) {
        sessions.remove(sessionId);
        log.info("Removed monitoring chat session: {}", sessionId);
    }
}
