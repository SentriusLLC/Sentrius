package io.sentrius.sso.rdpproxy.config;

import io.sentrius.sso.rdpproxy.servlet.GuacamoleTunnelWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket configuration for Guacamole tunnel communication.
 * This replaces the traditional servlet approach with Spring WebSocket support.
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class GuacamoleWebSocketConfig implements WebSocketConfigurer {

    private final GuacamoleTunnelWebSocketHandler tunnelWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // Register WebSocket endpoint for Guacamole tunnel communication
        registry.addHandler(tunnelWebSocketHandler, "/guacamole/websocket")
                .setAllowedOrigins("*") // In production, restrict to specific origins
                .withSockJS(); // Enable SockJS fallback for older browsers
        
        // Also register without SockJS for native WebSocket clients
        registry.addHandler(tunnelWebSocketHandler, "/guacamole/tunnel")
                .setAllowedOrigins("*");
    }
}