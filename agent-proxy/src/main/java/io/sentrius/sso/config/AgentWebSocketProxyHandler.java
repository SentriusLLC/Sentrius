package io.sentrius.sso.config;

import java.net.URI;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import io.sentrius.sso.core.services.security.ZeroTrustAccessTokenService;
import io.sentrius.sso.locator.KubernetesAgentLocator;
import io.sentrius.sso.service.ActiveWebSocketSessionManager;
import lombok.RequiredArgsConstructor;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.NettyDataBuffer;
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
    private final ActiveWebSocketSessionManager sessionManager;
    private final ZeroTrustAccessTokenService ztatService;

    @Override
    public Mono<Void> handle(WebSocketSession clientSession) {
        try {
            URI uri = clientSession.getHandshakeInfo().getUri();
            var queryParams = parseQueryParams(uri);

            String agentHost = queryParams.get("phost");
            if (agentHost.startsWith("wss://")) {
                agentHost = agentHost.replace("wss://", "ws://");
            }
            String sessionId = queryParams.get("sessionId");
            sessionId = sessionId.replace(" ","+");
            String chatGroupId = queryParams.get("chatGroupId");
            chatGroupId = chatGroupId.replace(" ","+");
            String ztat = queryParams.get("ztat");
            String ztatForChat = queryParams.get("jwt");


            if (ztatForChat != null && !ztatForChat.isEmpty()) {
                log.info("ZTAT for chat: {}", ztatForChat);
                if ( !ztatService.isOpsActive(ztatForChat) ){
                    log.info("Invalid ZTAT token for sessionId: {}, ztat: {}", sessionId, ztatForChat);
                    //return Mono.error(new RuntimeException("Invalid ZTAT token for sessionId: " + sessionId));
                }
                ztatService.incremenOpsUses(ztatForChat);

            } else {
                log.info("Invalid ZTAT token for sessionId: {}", sessionId);
                return Mono.error(new RuntimeException("Invalid ZTAT token") );
            }
            log.info("Handling WebSocket connection for host: {}, sessionId: {}, chatGroupId: {}, ztat: {}",
                agentHost, sessionId, chatGroupId, ztat);

            URI agentUri = agentLocator.resolveWebSocketUri(agentHost, sessionId, chatGroupId, ztat);

            log.info("Resolved agent URI: {}", agentUri);

            ReactorNettyWebSocketClient proxyClient = new ReactorNettyWebSocketClient();

            sessionManager.register(sessionId, clientSession);
            String finalSessionId = sessionId;

            return proxyClient.execute(agentUri, agentSession -> {
                log.info("Proxy client connected to agent");

                Mono<Void> clientToAgent = clientSession.receive()
                    .doOnSubscribe(s -> log.info("client -> agent subscribed"))
                    .doOnNext(m -> log.debug("client -> agent: message type {}", m.getType()))
                    .flatMap(webSocketMessage -> {
                        if (webSocketMessage.getType() == WebSocketMessage.Type.TEXT) {
                            return Mono.just(agentSession.textMessage(webSocketMessage.getPayloadAsText()));
                        } else {
                            log.warn("Client sent a BINARY message to agent. Agent expects TEXT. Converting to Base64 Text.");
                            return DataBufferUtils.join(Mono.just(webSocketMessage.getPayload()))
                                .map(dataBuffer -> {
                                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                                    dataBuffer.read(bytes);
                                    DataBufferUtils.release(dataBuffer);
                                    return agentSession.textMessage(Base64.getEncoder().encodeToString(bytes));
                                });
                        }
                    })
                    .as(agentSession::send)
                    .doOnSuccess(aVoid -> log.info("client -> agent completed gracefully")) // Corrected for Mono
                    .doOnError(e -> log.error("Error in client -> agent stream", e))
                    .onErrorResume(e -> {
                        log.error("Client to agent stream error, closing client session.", e);
                        return clientSession.close().then(Mono.empty());
                    })
                    .doFinally(sig -> log.info("Client to agent stream finalized: {}", sig));

// Stream from agent to client (Agent -> Proxy -> Client)
                Mono<Void> agentToClient = agentSession.receive()
                    .doOnSubscribe(s -> log.info("agent -> client subscribed"))
                    .doOnNext(m -> log.debug("agent -> client: message type {}", m.getType()))
                    .flatMap(webSocketMessage -> {
                        if (webSocketMessage.getType() == WebSocketMessage.Type.TEXT) {
                            return Mono.just(clientSession.textMessage(webSocketMessage.getPayloadAsText()));
                        } else {
                            log.warn("Agent sent a BINARY message to client. Client expects TEXT. Converting to Base64 Text.");
                            return DataBufferUtils.join(Mono.just(webSocketMessage.getPayload()))
                                .map(dataBuffer -> {
                                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                                    dataBuffer.read(bytes);
                                    DataBufferUtils.release(dataBuffer);
                                    return clientSession.textMessage(Base64.getEncoder().encodeToString(bytes));
                                });
                        }
                    })
                    .as(clientSession::send)
                    .doOnSuccess(aVoid -> log.info("agent -> client completed gracefully")) // Corrected for Mono
                    .doOnError(e -> {
                        log.error("Error in agent -> client stream", e);
                        sessionManager.unregister(agentSession.getId());
                    })
                    .onErrorResume(e -> {
                        sessionManager.unregister(agentSession.getId());
                        log.error("Agent to client stream error, closing agent session.", e);
                        return agentSession.close().then(Mono.empty());
                    })
                    .doFinally(sig -> log.info("Agent to client stream finalized: {}", sig));

                    return Mono.when(clientToAgent, agentToClient)
                        .doOnTerminate(() -> {
                            log.info("WebSocket proxy connection terminated (client and agent " +
                                "streams completed/cancelled)");
                            sessionManager.unregister(agentSession.getId());

                        })
                        .doOnError(e -> {
                            log.error("Overall proxy connection failed", e);
                            sessionManager.unregister(agentSession.getId());

                        })
                        .doFinally(sig -> {
                            sessionManager.unregister(finalSessionId);
                            log.info("WebSocket proxy stream closed completely: {}. Final session ID: {}", sig, finalSessionId);
                        });
            }
            ).doOnError(e -> {
                log.error("Failed to establish proxy connection", e);
                sessionManager.unregister(finalSessionId);
            });


        } catch (Exception ex) {
            ex.printStackTrace();
            log.info("WebSocket handshake failed: {}", ex.getMessage());
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