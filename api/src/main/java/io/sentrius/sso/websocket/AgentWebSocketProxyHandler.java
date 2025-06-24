package io.sentrius.sso.websocket;

import java.net.URI;
import io.sentrius.sso.locator.KubernetesAgentLocator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class AgentWebSocketProxyHandler implements WebSocketHandler {

private final KubernetesAgentLocator agentLocator;

@Override
public Mono<Void> handle(WebSocketSession clientSession) {
    String agentId = (String) clientSession.getAttributes().get("agentId");
    URI agentUri = agentLocator.resolveWebSocketUri(agentId);

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
}
}