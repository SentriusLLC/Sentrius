package io.sentrius.sso.mcp.config;

import io.sentrius.sso.mcp.websocket.MCPWebSocketHandler;
import io.sentrius.sso.core.services.security.KeycloakService;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;

import java.util.Map;

/**
 * WebSocket configuration for MCP (Model Context Protocol) endpoints
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class MCPWebSocketConfig implements WebSocketConfigurer {

    private final MCPWebSocketHandler mcpWebSocketHandler;
    private final KeycloakService keycloakService;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(mcpWebSocketHandler, "/api/v1/mcp/ws")
                .addInterceptors(new MCPHandshakeInterceptor())
                .setAllowedOrigins("*"); // Configure as needed for security
    }

    /**
     * Handshake interceptor to validate authentication before WebSocket connection
     */
    private class MCPHandshakeInterceptor implements HandshakeInterceptor {

        @Override
        public boolean beforeHandshake(
                ServerHttpRequest request, 
                ServerHttpResponse response,
                WebSocketHandler wsHandler, 
                Map<String, Object> attributes) throws Exception {
            
            // Extract authentication parameters from query params or headers
            String token = extractToken(request);
            String communicationId = extractParameter(request, "communication_id");
            String userId = extractParameter(request, "user_id");
            
            if (token == null || communicationId == null || userId == null) {
                return false; // Reject connection
            }
            
            // Validate JWT token
            String jwt = token.startsWith("Bearer ") ? token.substring(7) : token;
            if (!keycloakService.validateJwt(jwt)) {
                return false; // Invalid token
            }
            
            // Store validated parameters in session attributes
            attributes.put("token", token);
            attributes.put("communication_id", communicationId);
            attributes.put("user_id", userId);
            
            return true; // Allow connection
        }

        @Override
        public void afterHandshake(
                ServerHttpRequest request, 
                ServerHttpResponse response,
                WebSocketHandler wsHandler, 
                Exception exception) {
            // No additional processing needed
        }

        private String extractToken(ServerHttpRequest request) {
            // Try Authorization header first
            String authHeader = request.getHeaders().getFirst("Authorization");
            if (authHeader != null) {
                return authHeader;
            }
            
            // Fall back to query parameter
            return extractParameter(request, "token");
        }

        private String extractParameter(ServerHttpRequest request, String paramName) {
            String query = request.getURI().getQuery();
            if (query == null) {
                return null;
            }
            
            for (String param : query.split("&")) {
                String[] parts = param.split("=", 2);
                if (parts.length == 2 && paramName.equals(parts[0])) {
                    return parts[1];
                }
            }
            return null;
        }
    }
}