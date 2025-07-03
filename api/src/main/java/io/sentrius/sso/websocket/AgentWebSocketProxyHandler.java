package io.sentrius.sso.websocket;

import java.net.URI;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.Map;

import io.sentrius.sso.locator.KubernetesAgentLocator;
import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;

import io.sentrius.sso.core.services.security.CryptoService;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Component
@Slf4j
@RequiredArgsConstructor
public class AgentWebSocketProxyHandler implements WebSocketHandler {

private final KubernetesAgentLocator agentLocator;
private final CryptoService cryptoService;

    @Override
    public Mono<Void> handle(WebSocketSession clientSession) {
        try {
            URI uri = clientSession.getHandshakeInfo().getUri();
            var queryParams = parseQueryParams(uri);

            String encryptedHost = queryParams.get("phost");
            String decryptedHost = cryptoService.decrypt(encryptedHost);
            String sessionId = queryParams.get("sessionId");
            String chatGroupId = queryParams.get("chatGroupId");
            String ztat = queryParams.get("jwt");

            log.info("Handling WebSocket connection for host: {}, sessionId: {}, chatGroupId: {}, ztat: {}",
                decryptedHost, sessionId, chatGroupId, ztat);

            URI agentUri = agentLocator.resolveWebSocketUri(decryptedHost, sessionId, chatGroupId, ztat);

            ReactorNettyWebSocketClient proxyClient = new ReactorNettyWebSocketClient();

            return proxyClient.execute(agentUri, agentSession -> {
                Mono<Void> clientToAgent = clientSession.receive()
                    .map(WebSocketMessage::getPayload)
                    .map(dataBuffer -> agentSession.binaryMessage(factory -> dataBuffer))
                    .as(agentSession::send);

                Mono<Void> agentToClient = agentSession.receive()
                    .map(WebSocketMessage::getPayload)
                    .map(dataBuffer -> clientSession.binaryMessage(factory -> dataBuffer))
                    .as(clientSession::send);

                return Mono.zip(clientToAgent, agentToClient).then();
            });

        } catch (Exception ex) {
            return Mono.error(new RuntimeException("WebSocket handshake failed", ex));
        }
    }

    private Map<String, String> parseQueryParams(URI uri) {
        Map<String, String> queryMap = new HashMap<>();
        String query = uri.getQuery();
        if (query != null) {
            String[] pairs = query.split("&");
            for (String pair : pairs) {
                int idx = pair.indexOf("=");
                if (idx > 0 && idx < pair.length() - 1) {
                    queryMap.put(
                        decode(pair.substring(0, idx)),
                        decode(pair.substring(idx + 1))
                    );
                }
            }
        }
        return queryMap;
    }

    private String decode(String value) {
        try {
            return java.net.URLDecoder.decode(value, java.nio.charset.StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            return "";
        }
    }

}