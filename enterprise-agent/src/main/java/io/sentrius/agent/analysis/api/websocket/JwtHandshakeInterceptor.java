package io.sentrius.agent.analysis.api.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.socket.WebSocketHandler;

import java.net.URI;
import java.util.Arrays;
import java.util.Map;

@Slf4j
@Component
@ConditionalOnProperty(name = "agents.ai.chat.agent.enabled", havingValue = "true", matchIfMissing = false)
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    private final JwtDecoder jwtDecoder;

    @Autowired
    public JwtHandshakeInterceptor(JwtDecoder jwtDecoder) {
        this.jwtDecoder = jwtDecoder;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {
        log.info("Handshake attempt: {}", request.getURI());

        URI uri = request.getURI();
        String query = uri.getQuery();
        String token = null;

        if (query != null && query.contains("ztat=")) {
            token = Arrays.stream(query.split("&"))
                .filter(s -> s.startsWith("ztat="))
                .map(s -> s.substring("ztat=".length()))
                .findFirst()
                .orElse(null);
        }

        log.info("Token from query: {}", token);

        if (token != null) {
            try {
                Jwt jwt = jwtDecoder.decode(token);
                log.info("JWT decoded: {}", jwt.getClaims());
                attributes.put("jwt", jwt);
                return true;
            } catch (JwtException e) {
                log.warn("JWT validation failed: {}", e.getMessage());
                return false;
            }
        }

        log.warn("No token found in query string.");
        return false;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        log.info("After handshake.");
    }
}