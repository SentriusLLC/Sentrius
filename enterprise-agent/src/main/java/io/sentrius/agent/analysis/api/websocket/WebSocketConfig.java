package io.sentrius.agent.analysis.api.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;
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

    /**
     * Configure WebSocket container with generous timeouts to prevent disconnections.
     * These settings prevent the server from closing idle connections.
     */
    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();

        // Set max idle timeout to 60 minutes (in milliseconds)
        // This must be longer than the heartbeat interval (5 seconds)
        container.setMaxSessionIdleTimeout(3600000L); // 60 minutes

        // Set max text message buffer size (1 MB)
        container.setMaxTextMessageBufferSize(1048576);

        // Set max binary message buffer size (1 MB)
        container.setMaxBinaryMessageBufferSize(1048576);

        log.info("WebSocket container configured with 60-minute idle timeout");
        return container;
    }
}

