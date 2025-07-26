package io.sentrius.sso.service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import io.sentrius.sso.core.dto.TerminalLogDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketSession;

@Slf4j
@Component
public class ActiveWebSocketSessionManager {
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, Timestamp> sessionStartTimes = new ConcurrentHashMap<>();
    private final List<Map<String, Object>> completedAgentSessions = new ArrayList<>();

    public void register(String sessionId, WebSocketSession session) {
        sessions.put(sessionId, session);
        sessionStartTimes.put(sessionId, new Timestamp(System.currentTimeMillis()));
    }

    public void unregister(String sessionId) {
        WebSocketSession session = sessions.remove(sessionId);
        Timestamp startTime = sessionStartTimes.remove(sessionId);
        
        if (startTime != null) {
            // Calculate duration and store completed session
            Timestamp endTime = new Timestamp(System.currentTimeMillis());
            long durationMinutes = ChronoUnit.MINUTES.between(
                startTime.toLocalDateTime(), 
                endTime.toLocalDateTime()
            );
            
            Map<String, Object> completedSession = new HashMap<>();
            completedSession.put("sessionId", sessionId);
            completedSession.put("startTime", startTime);
            completedSession.put("endTime", endTime);
            completedSession.put("durationMinutes", durationMinutes);
            completedSession.put("sessionType", "agent");
            
            synchronized (completedAgentSessions) {
                completedAgentSessions.add(completedSession);
            }
        }
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

    /**
     * Get session duration data for agent sessions
     * @return List of session duration data
     */
    public List<Map<String, Object>> getAgentSessionDurations() {
        synchronized (completedAgentSessions) {
            log.info("Returning {} completed agent sessions", completedAgentSessions.size());
            return new ArrayList<>(completedAgentSessions);
        }
    }

    /**
     * Get current active agent session durations (for sessions still in progress)
     * @return List of active session duration data
     */
    public List<Map<String, Object>> getActiveAgentSessionDurations() {
        List<Map<String, Object>> activeDurations = new ArrayList<>();
        
        for (Map.Entry<String, Timestamp> entry : sessionStartTimes.entrySet()) {
            String sessionId = entry.getKey();
            Timestamp startTime = entry.getValue();
            
            if (sessions.containsKey(sessionId)) {
                long durationMinutes = ChronoUnit.MINUTES.between(
                    startTime.toLocalDateTime(), 
                    LocalDateTime.now()
                );
                
                Map<String, Object> activeSession = new HashMap<>();
                activeSession.put("sessionId", sessionId);
                activeSession.put("startTime", startTime);
                activeSession.put("durationMinutes", durationMinutes);
                activeSession.put("sessionType", "agent");
                activeSession.put("active", true);
                
                activeDurations.add(activeSession);
            }
        }
        log.info("Returning {} active agent session durations", activeDurations.size());
        return activeDurations;
    }
}
