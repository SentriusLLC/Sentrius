package io.sentrius.sso.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final TerminalWSHandler customWebSocketHandler;
    private final AuditSocketHandler auditSocketHandler;
    private final ChatWSHandler chatWSHandler;
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(customWebSocketHandler, "/api/v1/ssh/terminal/subscribe")
            .setAllowedOriginPatterns("*")
            .withSockJS();  // SockJS fallback if needed
        registry.addHandler(auditSocketHandler, "/api/v1/audit/attach/subscribe")
            .setAllowedOriginPatterns("*")
            .withSockJS();  // SockJS fallback if needed
        registry.addHandler(chatWSHandler, "/api/v1/chat/attach/subscribe")
            .setAllowedOriginPatterns("*")
            .withSockJS();  // SockJS fallback if needed

    }
}