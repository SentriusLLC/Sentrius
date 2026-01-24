package io.sentrius.sso.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter;
import org.springframework.web.reactive.socket.server.WebSocketService;
import org.springframework.web.reactive.socket.server.support.HandshakeWebSocketService;
import org.springframework.web.reactive.socket.server.upgrade.ReactorNettyRequestUpgradeStrategy;
import reactor.netty.http.server.WebsocketServerSpec;

import java.util.Map;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class WebSocketRouteConfig {

    private final AgentWebSocketProxyHandler agentWebSocketProxyHandler;

    @Bean
    public HandlerMapping webSocketMapping() {
        return new SimpleUrlHandlerMapping(Map.of(
            "/api/v1/agents/ws", agentWebSocketProxyHandler
        ), -1); // -1 means high priority
    }

    @Bean
    public WebSocketHandlerAdapter webSocketHandlerAdapter() {
        return new WebSocketHandlerAdapter(webSocketService());
    }

    /**
     * Configure WebSocket service with generous timeouts and frame size limits.
     * This prevents disconnections due to idle timeout or large messages.
     */
    @Bean
    public WebSocketService webSocketService() {
        // Configure upgrade strategy with proper WebSocket settings using modern API
        ReactorNettyRequestUpgradeStrategy upgradeStrategy = new ReactorNettyRequestUpgradeStrategy(
            () -> WebsocketServerSpec.builder()
                .maxFramePayloadLength(1048576) // 1 MB max frame size
                .handlePing(true) // Handle ping frames automatically
                .compress(false) // Disable compression for lower latency
        );

        log.info("WebSocket service configured with 1MB max frame size and ping handling enabled");

        return new HandshakeWebSocketService(upgradeStrategy);
    }
}
