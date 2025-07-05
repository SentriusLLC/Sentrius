package io.sentrius.sso.service;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import io.sentrius.sso.core.dto.TerminalLogDTO;
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

    public List<TerminalLogDTO> getActiveSessions() {
        return sessions.values().stream()
            .map(session -> TerminalLogDTO.builder()
                .sessionId(session.getId())
                .user("Unknown")
                .host(Objects.requireNonNull(session.getHandshakeInfo().getRemoteAddress()).toString())
                .closed(!session.isOpen())
                .sessionTime(new Timestamp(System.currentTimeMillis()))
                .build())
            .collect(Collectors.toList());
    }
}
