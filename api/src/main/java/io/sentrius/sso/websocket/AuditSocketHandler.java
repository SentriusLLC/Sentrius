
package io.sentrius.sso.websocket;

import java.io.IOException;
import java.net.URI;
import java.security.GeneralSecurityException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import io.sentrius.sso.core.services.security.CryptoService;
import io.sentrius.sso.core.services.terminal.SessionTrackingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuditSocketHandler extends TextWebSocketHandler {


    final SessionTrackingService sessionTrackingService;
    final SshListenerService sshListenerService;
    final CryptoService cryptoService;


    // Store active sessions, using session ID or a custom identifier
    private final ConcurrentHashMap<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        // Extract query parameters from the URI
        URI uri = session.getUri();
        if (uri != null) {
            Map<String, String> queryParams = parseQueryParams(uri.getQuery());
            String sessionId = queryParams.get("sessionId");

            if (sessionId != null) {
                // Store the WebSocket session using the session ID from the query parameter
                sessions.put(sessionId, session);
                log.trace("*AUDITING New connection established, session ID: " + sessionId);
                sshListenerService.startAuditingSession(sessionId, session);
            } else {
                log.trace("Session ID not found in query parameters.");
                session.close(); // Close the session if no valid session ID is provided
            }
        } else {
            log.trace("No URI available for this session.");
            session.close(); // Close the session if URI is unavailable
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message)
        throws IOException, GeneralSecurityException {


    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, org.springframework.web.socket.CloseStatus status) throws Exception {
        URI uri = session.getUri();
        if (uri != null) {
            Map<String, String> queryParams = parseQueryParams(uri.getQuery());
            String sessionId = queryParams.get("sessionId");

            if (sessionId != null) {
                // Remove the session when connection is closed
                sessions.remove(sessionId);
                sshListenerService.endAuditingSession(sessionId);
                log.trace("**AIDT Connection closed, session ID: " + sessionId);
            }
        }
    }

    // Utility method to parse query parameters
    private Map<String, String> parseQueryParams(String query) {
        if (query == null || query.isEmpty()) {
            return Map.of();
        }
        return Stream.of(query.split("&"))
            .map(param -> param.split("="))
            .collect(Collectors.toMap(
                param -> param[0],
                param -> param.length > 1 ? param[1] : ""
            ));
    }

    // Utility method to send a message to a specific session
    public void sendMessageToSession(String sessionId, String message) {
        WebSocketSession session = sessions.get(sessionId);
        if (session != null && session.isOpen()) {
            try {
                session.sendMessage(new TextMessage(message));
            } catch (IOException e) {
                System.err.println("Error sending message to session " + sessionId);
                e.printStackTrace();
            }
        } else {
            System.err.println("Session not found or already closed: " + sessionId);
        }
    }
}
