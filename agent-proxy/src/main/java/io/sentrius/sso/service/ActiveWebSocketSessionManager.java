package io.sentrius.sso.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketSession;

@Component
public class ActiveWebSocketSessionManager {
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    public void register(String sessionId, WebSocketSession session) {
        sessions.put(sessionId, session);
    }

    public void unregister(String sessionId) {
        sessions.remove(sessionId);
    }

    public WebSocketSession get(String sessionId) {
        return sessions.get(sessionId);
    }
}
