package io.sentrius.agent.analysis.api.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.beans.factory.annotation.Autowired;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "agents.ai.chat.agent.enabled", havingValue = "true", matchIfMissing = false)
public class WebSocketConfig implements WebSocketConfigurer {

    @Value("${agent.listen.websocket:false}") // Default is false
    private boolean listenWebSocket;

    private final ChatWSHandler chatWSHandler;

    @Autowired
    private JwtHandshakeInterceptor jwtHandshakeInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        if (listenWebSocket) {
            log.info("WebSocket is enabled, registering handlers.");
            registry.addHandler(chatWSHandler, "/api/v1/chat/attach/subscribe")
                .setAllowedOriginPatterns("*");
        }
    }
}

