package io.sentrius.agent.monitoring.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.socket.WebSocketSession;

/**
 * Model representing a WebSocket session for monitoring agent chat.
 * Simpler than the enterprise agent's WebSocky as it's read-only.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonitoringWebSocky {
    String sessionId;
    Long uniqueIdentifier;
    WebSocketSession webSocketSession;
}
