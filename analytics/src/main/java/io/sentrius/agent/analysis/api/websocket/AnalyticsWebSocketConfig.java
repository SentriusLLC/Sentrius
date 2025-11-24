package io.sentrius.agent.analysis.api.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket configuration for analytics agent chat
 */
@Slf4j
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
@ConditionalOnProperty(name = "agents.analytics.chat.enabled", havingValue = "true", matchIfMissing = false)
public class AnalyticsWebSocketConfig implements WebSocketConfigurer {
    
    private final AnalyticsChatWSHandler analyticsChatWSHandler;
    
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(analyticsChatWSHandler, "/api/v1/analytics/chat/subscribe")
            .setAllowedOrigins("*");
        log.info("Analytics agent chat WebSocket endpoint registered at /api/v1/analytics/chat/subscribe");
    }
}
