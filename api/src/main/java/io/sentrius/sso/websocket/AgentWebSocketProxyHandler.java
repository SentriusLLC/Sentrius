package io.sentrius.sso.websocket;

import java.net.URI;
import java.security.GeneralSecurityException;

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
        String host = (String) clientSession.getAttributes().get("host");
        var decryptedHost = cryptoService.decrypt(host); // Ensure host is decrypted if necessary
        String sessionId = (String) clientSession.getAttributes().get("sessionId");
        String chatGroupId = (String) clientSession.getAttributes().get("chatGroupId");
        String ztat = (String) clientSession.getAttributes().get("ztat");
        log.info("Handling WebSocket connection for host: {}, sessionId: {}, chatGroupId: {}, ztat: {}",
                decryptedHost, sessionId, chatGroupId, ztat);
        URI agentUri = agentLocator.resolveWebSocketUri(decryptedHost, sessionId, chatGroupId, ztat);
        
        ReactorNettyWebSocketClient proxyClient = new ReactorNettyWebSocketClient();
        
        return proxyClient.execute(agentUri, agentSession -> {
            // Forward messages from client to agent
            Mono<Void> clientToAgent = clientSession.receive()
                    .map(WebSocketMessage::getPayload)
                    .map(dataBuffer -> agentSession.binaryMessage(factory -> dataBuffer))
                    .as(agentSession::send);
            
            // Forward messages from agent to client
            Mono<Void> agentToClient = agentSession.receive()
                    .map(WebSocketMessage::getPayload)
                    .map(dataBuffer -> clientSession.binaryMessage(factory -> dataBuffer))
                    .as(clientSession::send);
            
            // Run both directions in parallel, complete when both are done
            return Mono.zip(clientToAgent, agentToClient).then();
        });
    } catch (GeneralSecurityException ex) {
        throw new RuntimeException("Failed to decrypt host", ex);
    }
}
}