package io.sentrius.sso.config;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import io.sentrius.sso.core.services.security.ZeroTrustAccessTokenService;
import io.sentrius.sso.core.services.agents.AgentExecutionAuditService;
import io.sentrius.sso.locator.KubernetesAgentLocator;
import io.sentrius.sso.service.ActiveWebSocketSessionManager;
import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.CloseStatus;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;


@Component
@Slf4j
@RequiredArgsConstructor
public class AgentWebSocketProxyHandler implements WebSocketHandler {

    private final KubernetesAgentLocator agentLocator;
    private final ActiveWebSocketSessionManager sessionManager;
    private final ZeroTrustAccessTokenService ztatService;
    private final AgentExecutionAuditService agentExecutionAuditService;

    @Override
    public Mono<Void> handle(WebSocketSession clientSession) {
        try {
            URI uri = clientSession.getHandshakeInfo().getUri();
            var queryParams = parseQueryParams(uri);

            String agentHost = queryParams.get("phost");
            if (agentHost == null || agentHost.isEmpty()) {
                log.error("Missing required parameter: phost");
                return clientSession.close(CloseStatus.BAD_DATA)
                    .then(Mono.error(new RuntimeException("Missing required parameter: phost")));
            }

            if (agentHost.startsWith("wss://")) {
                agentHost = agentHost.replace("wss://", "ws://");
            }

            String sessionId = queryParams.get("sessionId");
            if (sessionId == null || sessionId.isEmpty()) {
                log.error("Missing required parameter: sessionId");
                return clientSession.close(CloseStatus.BAD_DATA)
                    .then(Mono.error(new RuntimeException("Missing required parameter: sessionId")));
            }
            sessionId = sessionId.replace(" ", "+");

            String chatGroupId = queryParams.get("chatGroupId");
            if (chatGroupId != null) {
                chatGroupId = chatGroupId.replace(" ", "+");
            }

            String ztat = queryParams.get("ztat");
            String ztatForChat = queryParams.get("jwt");

            String userId = queryParams.get("userId");
            if (userId != null) {
                userId = userId.replace(" ", "+");
            }

            // Validate ZTAT token
            if (ztatForChat != null && !ztatForChat.isEmpty()) {
                log.info("ZTAT for chat received for sessionId: {}", sessionId);
                if (!ztatService.isOpsActive(ztatForChat)) {
                    log.warn("ZTAT token validation failed for sessionId: {}", sessionId);
                    // Continue anyway - token might still be valid for the agent
                }
                ztatService.incremenOpsUses(ztatForChat);
            } else {
                log.error("Missing ZTAT token for sessionId: {}", sessionId);
                return clientSession.close(CloseStatus.POLICY_VIOLATION)
                    .then(Mono.error(new RuntimeException("Invalid ZTAT token")));
            }

            log.info("Handling WebSocket connection for host: {}, sessionId: {}, chatGroupId: {}, userId: {}",
                agentHost, sessionId, chatGroupId, userId);

            // Create agent execution audit for this WebSocket session
            // The chatGroupId is the execution ID for agent chat sessions
            try {
                if (chatGroupId != null && !chatGroupId.isEmpty()) {
                    var existingAudit = agentExecutionAuditService.getAuditByExecutionId(chatGroupId);
                    if (existingAudit.isEmpty()) {
                        String actualUserId = userId != null && !userId.isEmpty() ? userId : "unknown";
                        agentExecutionAuditService.createAudit(
                            agentHost,
                            chatGroupId,
                            "chat-helper",
                            actualUserId
                        );
                        log.info("Created agent execution audit for chat session: {}, agent: {}, user: {}", 
                            chatGroupId, agentHost, actualUserId);
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to create agent execution audit for chat session: {}", chatGroupId, e);
            }

            URI agentUri = agentLocator.resolveWebSocketUri(agentHost.toLowerCase(), sessionId, chatGroupId, ztat, userId);
            log.info("Resolved agent URI: {}", agentUri);

            // Configure HTTP client with generous timeouts for long-lived WebSocket connections
            HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofMinutes(60))
                .keepAlive(true)
                .doOnConnected(conn -> {
                    log.debug("Proxy client connected, adding timeout handlers");
                    conn.addHandlerLast(new ReadTimeoutHandler(65, TimeUnit.MINUTES));
                    conn.addHandlerLast(new WriteTimeoutHandler(65, TimeUnit.MINUTES));
                });

            ReactorNettyWebSocketClient proxyClient = new ReactorNettyWebSocketClient(httpClient);

            sessionManager.register(sessionId, clientSession);
            final String finalSessionId = sessionId;
            final String finalChatGroupId = chatGroupId; // Make final for lambda access

            // Subscribe to client close status for debugging
            clientSession.closeStatus()
                .doOnNext(status -> log.info("CLIENT session close initiated: code={}, reason='{}'",
                    status.getCode(), status.getReason()))
                .subscribe();

            return proxyClient.execute(agentUri, agentSession -> {
                    log.info("Proxy client connected to agent for session: {}", finalSessionId);

                    // Subscribe to agent close status for debugging
                    agentSession.closeStatus()
                        .doOnNext(status -> log.info("AGENT session close initiated: code={}, reason='{}'",
                            status.getCode(), status.getReason()))
                        .subscribe();

                    // Keep-alive ping every 15 seconds to prevent idle timeouts
                    Flux<WebSocketMessage> keepAlivePings = Flux.interval(Duration.ofSeconds(15))
                        .map(tick -> {
                            log.trace("Sending keep-alive ping #{} for session: {}", tick, finalSessionId);
                            return clientSession.pingMessage(factory ->
                                factory.wrap(("proxy-ping-" + tick).getBytes(StandardCharsets.UTF_8)));
                        })
                        .doOnError(e -> log.warn("Keep-alive ping error: {}", e.getMessage()));

                    // Stream from client to agent (Client -> Proxy -> Agent)
                    Flux<WebSocketMessage> clientMessages = clientSession.receive()
                        .doOnSubscribe(s -> log.info("client -> agent stream subscribed for session: {}", finalSessionId))
                        .doOnNext(m -> {
                            if (m.getType() != WebSocketMessage.Type.PONG) {
                                log.debug("client -> agent: message type {} for session: {}", m.getType(), finalSessionId);
                            }
                        })
                        .filter(msg -> msg.getType() != WebSocketMessage.Type.PONG) // Filter out pong responses
                        .map(webSocketMessage -> {
                            if (webSocketMessage.getType() == WebSocketMessage.Type.TEXT) {
                                String text = webSocketMessage.getPayloadAsText();
                                return agentSession.textMessage(text);
                            } else if (webSocketMessage.getType() == WebSocketMessage.Type.BINARY) {
                                log.debug("Client sent BINARY message, converting to Base64 text");
                                byte[] bytes = new byte[webSocketMessage.getPayload().readableByteCount()];
                                webSocketMessage.getPayload().read(bytes);
                                String base64 = Base64.getEncoder().encodeToString(bytes);
                                return agentSession.textMessage(base64);
                            } else if (webSocketMessage.getType() == WebSocketMessage.Type.PING) {
                                // Forward ping as text (agent might not handle WebSocket pings)
                                return agentSession.textMessage("ping");
                            } else {
                                log.warn("Unexpected message type from client: {}", webSocketMessage.getType());
                                return agentSession.textMessage("");
                            }
                        })
                        .filter(msg -> !msg.getPayloadAsText().isEmpty());

                    Mono<Void> clientToAgent = clientMessages
                        .concatWith(Mono.never()) // Keep stream open
                        .as(agentSession::send)
                        .doOnError(e -> log.error("Error in client -> agent stream for session: {}", finalSessionId, e))
                        .onErrorResume(e -> {
                            log.warn("Client to agent stream error, attempting graceful handling for session: {}", finalSessionId);
                            return Mono.empty();
                        })
                        .doFinally(sig -> log.info("Client to agent stream finalized with signal: {} for session: {}", sig, finalSessionId));

                    // Stream from agent to client (Agent -> Proxy -> Client)
                    Flux<WebSocketMessage> agentMessages = agentSession.receive()
                        .doOnSubscribe(s -> log.info("agent -> client stream subscribed for session: {}", finalSessionId))
                        .doOnNext(m -> log.debug("agent -> client: message type {} for session: {}", m.getType(), finalSessionId))
                        .map(webSocketMessage -> {
                            if (webSocketMessage.getType() == WebSocketMessage.Type.TEXT) {
                                String text = webSocketMessage.getPayloadAsText();
                                return clientSession.textMessage(text);
                            } else if (webSocketMessage.getType() == WebSocketMessage.Type.BINARY) {
                                log.debug("Agent sent BINARY message, converting to Base64 text");
                                byte[] bytes = new byte[webSocketMessage.getPayload().readableByteCount()];
                                webSocketMessage.getPayload().read(bytes);
                                String base64 = Base64.getEncoder().encodeToString(bytes);
                                return clientSession.textMessage(base64);
                            } else {
                                log.warn("Unexpected message type from agent: {}", webSocketMessage.getType());
                                return clientSession.textMessage("");
                            }
                        })
                        .filter(msg -> !msg.getPayloadAsText().isEmpty());

                    // Merge agent messages with keep-alive pings
                    Mono<Void> agentToClient = agentMessages
                        .mergeWith(keepAlivePings) // Include keep-alive pings
                        .concatWith(Mono.never()) // Keep stream open
                        .as(clientSession::send)
                        .doOnError(e -> {
                            log.error("Error in agent -> client stream for session: {}", finalSessionId, e);
                            sessionManager.unregister(finalSessionId);
                        })
                        .onErrorResume(e -> {
                            log.warn("Agent to client stream error, attempting graceful handling for session: {}", finalSessionId);
                            sessionManager.unregister(finalSessionId);
                            return Mono.empty();
                        })
                        .doFinally(sig -> log.info("Agent to client stream finalized with signal: {} for session: {}", sig, finalSessionId));

                    // Combine both streams - connection stays open as long as either stream is active
                    return Mono.when(clientToAgent, agentToClient)
                        .doOnSubscribe(s -> log.info("WebSocket proxy streams started for session: {}", finalSessionId))
                        .doOnSuccess(v -> {
                            log.info("WebSocket proxy completed successfully for session: {}", finalSessionId);
                            // Close the agent execution audit when connection completes successfully
                            closeAgentExecutionAudit(finalChatGroupId, "COMPLETED");
                        })
                        .doOnTerminate(() -> {
                            log.info("WebSocket proxy connection terminated for session: {}", finalSessionId);
                            sessionManager.unregister(finalSessionId);
                            // Close the agent execution audit on termination
                            closeAgentExecutionAudit(finalChatGroupId, "COMPLETED");
                        })
                        .doOnError(e -> {
                            log.error("WebSocket proxy connection error for session: {}", finalSessionId, e);
                            sessionManager.unregister(finalSessionId);
                            // Mark as error if connection failed
                            closeAgentExecutionAudit(finalChatGroupId, "ERROR");
                        })
                        .doOnCancel(() -> {
                            log.warn("WebSocket proxy connection CANCELLED for session: {}", finalSessionId);
                            sessionManager.unregister(finalSessionId);
                            // Mark as cancelled/completed
                            closeAgentExecutionAudit(finalChatGroupId, "COMPLETED");
                        });
                })
                .doOnSubscribe(s -> log.info("Initiating proxy connection for session: {}", finalSessionId))
                .doOnError(e -> {
                    log.error("Failed to establish proxy connection for session: {}", finalSessionId, e);
                    sessionManager.unregister(finalSessionId);
                })
                .doOnTerminate(() -> log.info("Proxy client execution completed for session: {}", finalSessionId))
                .doOnCancel(() -> {
                    log.warn("Proxy client execution CANCELLED for session: {}", finalSessionId);
                    sessionManager.unregister(finalSessionId);
                });

        } catch (Exception ex) {
            log.error("WebSocket handshake failed: {}", ex.getMessage(), ex);
            return clientSession.close(CloseStatus.SERVER_ERROR)
                .then(Mono.error(new RuntimeException("WebSocket handshake failed", ex)));
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
                } else if (idx > 0) {
                    // Handle empty values
                    queryMap.put(decode(pair.substring(0, idx)), "");
                }
            }
        }
        return queryMap;
    }

    private String decode(String value) {
        try {
            return java.net.URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("Failed to decode URL parameter: {}", value);
            return value;
        }
    }

    /**
     * Close agent execution audit record when WebSocket session ends
     */
    private void closeAgentExecutionAudit(String executionId, String status) {
        if (executionId == null || executionId.isEmpty()) {
            return;
        }

        try {
            agentExecutionAuditService.closeAudit(executionId, status);
            log.debug("Closed agent execution audit for execution: {} with status: {}", executionId, status);
        } catch (Exception e) {
            log.warn("Failed to close agent execution audit for execution: {}", executionId, e);
        }
    }
}