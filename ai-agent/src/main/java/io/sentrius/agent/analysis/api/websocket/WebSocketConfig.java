package io.sentrius.agent.analysis.api.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    @Value("${agent.listen.websocket:false}") // Default is false
    private boolean listenWebSocket;

    private final ChatWSHandler chatWSHandler;
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        if (listenWebSocket) {
            registry.addHandler(chatWSHandler, "/api/v1/chat/attach/subscribe")
                .setAllowedOriginPatterns("*")
                .withSockJS();  // SockJS fallback if needed

        }
    }
}