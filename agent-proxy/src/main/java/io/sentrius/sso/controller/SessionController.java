package io.sentrius.sso.controller;

import java.util.List;
import io.sentrius.sso.core.dto.TerminalLogDTO;
import io.sentrius.sso.service.ActiveWebSocketSessionManager;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/sessions")
public class SessionController {

    private final WebClient webClient;
    private final ActiveWebSocketSessionManager activeWebSocketSessionManager;

    public SessionController(WebClient.Builder builder, ActiveWebSocketSessionManager activeWebSocketSessionManager) {
        this.webClient = builder.baseUrl("http://sentrius-agent-proxy").build();
        this.activeWebSocketSessionManager = activeWebSocketSessionManager;
    }

    @GetMapping("/list")
    public List<TerminalLogDTO> listSessions() {
        return activeWebSocketSessionManager.getActiveSessions();
    }

}