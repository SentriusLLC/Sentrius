package io.sentrius.agent.analysis.api.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service to manage analytics agent chat sessions
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "agents.analytics.chat.enabled", havingValue = "true", matchIfMissing = false)
public class AnalyticsUserCommunicationService {
    
    private final Map<String, AnalyticsWebSocky> sessions = new ConcurrentHashMap<>();
    
    public AnalyticsWebSocky createSession(String sessionId, WebSocketSession webSocketSession) {
        AnalyticsWebSocky websocky = AnalyticsWebSocky.builder()
            .sessionId(sessionId)
            .webSocketSession(webSocketSession)
            .build();
        
        sessions.put(sessionId, websocky);
        log.info("Created analytics chat session: {}", sessionId);
        
        return websocky;
    }
    
    public Optional<AnalyticsWebSocky> getSession(String sessionId) {
        return Optional.ofNullable(sessions.get(sessionId));
    }
    
    public void remove(String sessionId) {
        sessions.remove(sessionId);
        log.info("Removed analytics chat session: {}", sessionId);
    }
    
    public Map<String, AnalyticsWebSocky> getAllSessions() {
        return new ConcurrentHashMap<>(sessions);
    }
}
