package io.sentrius.agent.analysis.api;

import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import io.sentrius.agent.analysis.model.WebSocky;
import io.sentrius.sso.core.model.AgentStatus;
import io.sentrius.sso.core.services.security.KeycloakService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.socket.WebSocketSession;

@Slf4j
@Service
public class UserCommunicationService {

    KeycloakService keycloakService;

    @Value("${agent.listen.websocket:false}") // Default is false
    private boolean listenWebSocket;

    private final ConcurrentHashMap<String, WebSocky> sessions = new ConcurrentHashMap<>();



    public WebSocky createSession(String sessionId, WebSocketSession session) {
        var websocky =
            WebSocky.builder().sessionId(sessionId).webSocketSession(session).uniqueIdentifier(UUID.fromString(sessionId).getMostSignificantBits()).build();
        sessions.put(sessionId, websocky);
        return websocky;
    }

    public Optional<WebSocky> getSession(String sessionId) {
        var websocky = sessions.get(sessionId);
        return Optional.of(websocky);
    }

    public void remove(String sessionId) {
        sessions.remove(sessionId);
    }
}

