package io.sentrius.sso.websocket;

import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.server.WebSocketService;
import org.springframework.web.reactive.socket.server.support.HandshakeWebSocketService;
import org.springframework.web.reactive.socket.server.upgrade.ReactorNettyRequestUpgradeStrategy;
import org.springframework.web.socket.server.support.WebSocketHandlerMapping;

@Configuration
@RequiredArgsConstructor
public class WebSocketRouteConfig {

    private final AgentWebSocketProxyHandler agentWebSocketProxyHandler;

    @Bean
    public WebSocketHandlerMapping webSocketMapping() {
        Map<String, WebSocketHandler> map = new HashMap<>();
        map.put("/api/v1/agents/ws/{agentId}", agentWebSocketProxyHandler);

        WebSocketHandlerMapping mapping = new WebSocketHandlerMapping();
        mapping.setUrlMap(map);
        mapping.setOrder(-1); // Ensure it's picked up early
        return mapping;
    }

    @Bean
    public WebSocketService webSocketService() {
        return new HandshakeWebSocketService(new ReactorNettyRequestUpgradeStrategy());
    }
}
