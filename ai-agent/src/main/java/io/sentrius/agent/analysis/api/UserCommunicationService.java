package io.sentrius.agent.analysis.api;

import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Optional;
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



    public void createSession(String sessionId, WebSocketSession session) {
        sessions.put(sessionId, WebSocky.builder().sessionId(sessionId).webSocketSession(session).build());
    }

    public Optional<WebSocky> getSession(String sessionId) {
        return Optional.of(sessions.get(sessionId));
    }

    public void remove(String sessionId) {
        sessions.remove(sessionId);
    }
}

