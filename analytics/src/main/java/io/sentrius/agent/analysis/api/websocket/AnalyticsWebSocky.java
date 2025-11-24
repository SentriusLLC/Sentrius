package io.sentrius.agent.analysis.api.websocket;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.socket.WebSocketSession;

/**
 * Represents a chat session for the analytics agent
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsWebSocky {
    private String sessionId;
    private WebSocketSession webSocketSession;
    
    public long getUniqueIdentifier() {
        if (sessionId != null) {
            try {
                return Long.parseLong(sessionId.replace("-", "").substring(0, 16), 16);
            } catch (Exception e) {
                return sessionId.hashCode();
            }
        }
        return 0;
    }
}
