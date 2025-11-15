package io.sentrius.agent.analysis.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.web.socket.WebSocketSession;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents an active SSH terminal session managed by the agent.
 * This model tracks the WebSocket connection, session metadata, and command history.
 */
@Data
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SSHTerminalSession {
    /**
     * Unique identifier for this terminal session (encrypted session ID)
     */
    private String sessionId;
    
    /**
     * The host system this terminal is connected to
     */
    private String hostConnection;
    
    /**
     * Display name of the host
     */
    private String displayName;
    
    /**
     * WebSocket session for communication
     */
    private transient WebSocketSession webSocketSession;
    
    /**
     * Whether the session is currently active
     */
    @Builder.Default
    private boolean active = false;
    
    /**
     * Commands sent to this terminal
     */
    @Builder.Default
    private List<String> commandHistory = new ArrayList<>();
    
    /**
     * Output received from the terminal
     */
    @Builder.Default
    private StringBuilder terminalOutput = new StringBuilder();
    
    /**
     * Timestamp when the session was created
     */
    private long createdAt;
    
    /**
     * Timestamp of last activity
     */
    private long lastActivityAt;
}
