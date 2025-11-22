package io.sentrius.agent.monitoring.api.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket configuration for monitoring agent chat functionality.
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "agents.monitoring.chat.enabled", havingValue = "true", matchIfMissing = false)
public class MonitoringWebSocketConfig implements WebSocketConfigurer {

    @Value("${agent.listen.websocket:false}")
    private boolean listenWebSocket;

    private final MonitoringChatWSHandler monitoringChatWSHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        if (listenWebSocket) {
            log.info("WebSocket is enabled for monitoring agent, registering chat handler.");
            registry.addHandler(monitoringChatWSHandler, "/api/v1/monitoring/chat/subscribe")
                .setAllowedOriginPatterns("*");
        }
    }
}
