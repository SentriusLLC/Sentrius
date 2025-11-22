package io.sentrius.agent.monitoring.api.websocket;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.sentrius.agent.monitoring.service.RegisteredMonitoringAgent;
import io.sentrius.sso.core.services.agents.AgentClientService;
import io.sentrius.sso.core.services.agents.ZeroTrustClientService;
import io.sentrius.sso.protobuf.Session;
import io.sentrius.sso.provenance.ProvenanceEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * WebSocket handler for monitoring agent chat sessions.
 * Provides read-only view into the monitoring agent's state without pausing its operations.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "agents.monitoring.chat.enabled", havingValue = "true", matchIfMissing = false)
public class MonitoringChatWSHandler extends TextWebSocketHandler {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final MonitoringUserCommunicationService userCommunicationService;
    private final ZeroTrustClientService zeroTrustClientService;
    private final RegisteredMonitoringAgent monitoringAgent;
    private final AgentClientService agentClientService;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        log.info("New monitoring chat connection established");
        URI uri = session.getUri();
        if (uri == null) {
            session.close(CloseStatus.BAD_DATA);
            return;
        }

        Map<String, String> queryParams = parseQueryParams(uri.getQuery());
        Long sessionId = UUID.fromString(queryParams.get("sessionId")).getMostSignificantBits();
        String chatGroupId = queryParams.get("chatGroupId");
        String ztatToken = queryParams.get("ztat");

        if (sessionId == null || ztatToken == null) {
            log.warn("Missing sessionId or ZTAT");
            session.close(CloseStatus.BAD_DATA);
            return;
        }

        // Store session
        var websocky = userCommunicationService.createSession(queryParams.get("sessionId"), session);
        log.info("Monitoring chat session {} created for incoming connection", sessionId);

        // Generate and store nonce for this session
        String nonce = UUID.randomUUID().toString();
        session.getAttributes().put("ztatNonce", nonce);
        session.getAttributes().put("ztatToken", ztatToken);
        session.getAttributes().put("sessionId", sessionId);

        // Send challenge to the client
        log.info("Sending challenge to client: {}", nonce);
        var challenge = Session.ChatMessage.newBuilder()
            .setMessage(String.format("{\"type\":\"challenge\",\"nonce\":\"%s\"}", nonce))
            .setSender("monitoring-agent")
            .setChatGroupId(chatGroupId)
            .setSessionId(sessionId)
            .setTimestamp(System.currentTimeMillis())
            .build();
        byte[] messageBytes = challenge.toByteArray();
        String base64Message = Base64.getEncoder().encodeToString(messageBytes);
        session.sendMessage(new TextMessage(base64Message));

        // Submit provenance event
        try {
            ProvenanceEvent provenanceEvent = ProvenanceEvent.builder()
                .eventType(ProvenanceEvent.EventType.USER_CHAT_AGENT)
                .actor("admin")
                .triggeringUser(monitoringAgent.getAgentName())
                .outputSummary("Monitoring agent chat session established (read-only)")
                .sessionId(session.getId())
                .build();

            agentClientService.submitProvenance(monitoringAgent.getAgentExecution(), provenanceEvent);
        } catch (Exception e) {
            log.error("Failed to submit provenance event", e);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message)
        throws IOException, GeneralSecurityException {

        URI uri = session.getUri();
        log.info("Received message on monitoring chat");
        
        try {
            if (uri != null) {
                Map<String, String> queryParams = parseQueryParams(uri.getQuery());
                String sessionId = queryParams.get("sessionId");

                var websocky = userCommunicationService.getSession(sessionId);

                if (sessionId != null && websocky.isPresent()) {
                    var websocketCommunication = websocky.get();
                    log.info("Processing message from monitoring chat session ID: {}", sessionId);

                    byte[] messageBytes = Base64.getDecoder().decode(message.getPayload());
                    Session.ChatMessage chatMessage = Session.ChatMessage.parseFrom(messageBytes);

                    if (chatMessage.getMessage().equals("heartbeat")) {
                        return;
                    }

                    var json = OBJECT_MAPPER.readTree(chatMessage.getMessage());
                    
                    if ("challenge-response".equals(json.get("type").asText())) {
                        String signature = json.get("signature").asText();
                        String publicKey = json.get("publicKey").asText();
                        String nonce = (String) session.getAttributes().get("ztatNonce");
                        String ztat = (String) session.getAttributes().get("ztatToken");

                        boolean verified = zeroTrustClientService.verifyZtatChallenge(
                            monitoringAgent.getAgentExecution(), ztat, nonce, signature, publicKey);

                        if (verified) {
                            session.getAttributes().put("verified", true);
                            log.info("ZTAT challenge verified for monitoring chat session {}", session.getId());
                        } else {
                            log.warn("ZTAT challenge failed for monitoring chat session {}", session.getId());
                            session.close();
                        }
                        return;
                    } else if ("get-status".equals(json.get("type").asText())) {
                        log.info("Received status query from session {}", sessionId);
                        String statusInfo = monitoringAgent.getStatusInfo();
                        
                        var statusResponse = Session.ChatMessage.newBuilder()
                            .setMessage(statusInfo)
                            .setSender("monitoring-agent")
                            .setChatGroupId("")
                            .setSessionId(websocketCommunication.getUniqueIdentifier())
                            .setTimestamp(System.currentTimeMillis())
                            .build();
                        messageBytes = statusResponse.toByteArray();
                        String base64Message = Base64.getEncoder().encodeToString(messageBytes);
                        session.sendMessage(new TextMessage(base64Message));
                        return;
                    } else if ("get-endpoint-health".equals(json.get("type").asText())) {
                        log.info("Received endpoint health query from session {}", sessionId);
                        String endpointHealth = monitoringAgent.getEndpointHealthInfo();
                        
                        var healthResponse = Session.ChatMessage.newBuilder()
                            .setMessage(endpointHealth)
                            .setSender("monitoring-agent")
                            .setChatGroupId("")
                            .setSessionId(websocketCommunication.getUniqueIdentifier())
                            .setTimestamp(System.currentTimeMillis())
                            .build();
                        messageBytes = healthResponse.toByteArray();
                        String base64Message = Base64.getEncoder().encodeToString(messageBytes);
                        session.sendMessage(new TextMessage(base64Message));
                        return;
                    } else if ("get-monitoring-config".equals(json.get("type").asText())) {
                        log.info("Received monitoring config query from session {}", sessionId);
                        String configInfo = monitoringAgent.getMonitoringConfigInfo();
                        
                        var configResponse = Session.ChatMessage.newBuilder()
                            .setMessage(configInfo)
                            .setSender("monitoring-agent")
                            .setChatGroupId("")
                            .setSessionId(websocketCommunication.getUniqueIdentifier())
                            .setTimestamp(System.currentTimeMillis())
                            .build();
                        messageBytes = configResponse.toByteArray();
                        String base64Message = Base64.getEncoder().encodeToString(messageBytes);
                        session.sendMessage(new TextMessage(base64Message));
                        return;
                    } else if ("user-message".equals(json.get("type").asText())) {
                        log.info("Received user message from session {}", sessionId);
                        String userMsg = json.get("message").asText();
                        
                        // For now, provide a simple response explaining this is read-only
                        String responseMsg = String.format(
                            "Monitoring Agent (Read-Only Mode)\n\n" +
                            "Your message: %s\n\n" +
                            "Available commands:\n" +
                            "- {\"type\":\"get-status\"} - Get current agent status\n" +
                            "- {\"type\":\"get-endpoint-health\"} - Get endpoint health information\n" +
                            "- {\"type\":\"get-monitoring-config\"} - Get monitoring configuration\n\n" +
                            "Note: The monitoring agent continues running while you chat.",
                            userMsg
                        );
                        
                        var response = Session.ChatMessage.newBuilder()
                            .setMessage(responseMsg)
                            .setSender("monitoring-agent")
                            .setChatGroupId("")
                            .setSessionId(websocketCommunication.getUniqueIdentifier())
                            .setTimestamp(System.currentTimeMillis())
                            .build();
                        messageBytes = response.toByteArray();
                        String base64Message = Base64.getEncoder().encodeToString(messageBytes);
                        session.sendMessage(new TextMessage(base64Message));
                        return;
                    } else {
                        log.info("Unknown message type, ignoring: {}", chatMessage.getMessage());
                    }
                } else {
                    log.info("Session ID not found in query parameters for message handling.");
                }
            }
        } catch (Exception e) {
            log.error("Error handling monitoring chat message", e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, org.springframework.web.socket.CloseStatus status) throws Exception {
        URI uri = session.getUri();
        if (uri != null) {
            Map<String, String> queryParams = parseQueryParams(uri.getQuery());
            String sessionId = queryParams.get("sessionId");

            if (sessionId != null) {
                userCommunicationService.remove(sessionId);
                log.info("Monitoring chat connection closed, session ID: {}", sessionId);
            }
        }
    }

    private Map<String, String> parseQueryParams(String query) {
        if (query == null || query.isEmpty()) {
            return Map.of();
        }
        return Stream.of(query.split("&"))
            .map(param -> param.split("="))
            .collect(Collectors.toMap(
                param -> URLDecoder.decode(param[0], StandardCharsets.UTF_8),
                param -> param.length > 1 ? URLDecoder.decode(param[1], StandardCharsets.UTF_8) : ""
            ));
    }

    public void sendMessageToSession(String sessionId, String message) {
        var websocket = userCommunicationService.getSession(sessionId);
        if (websocket.isPresent()) {
            WebSocketSession session = websocket.get().getWebSocketSession();

            if (session != null && session.isOpen()) {
                try {
                    session.sendMessage(new TextMessage(message));
                } catch (IOException e) {
                    log.error("Error sending message to monitoring chat session {}", sessionId, e);
                }
            } else {
                log.error("Monitoring chat session not found or already closed: {}", sessionId);
            }
        }
    }
}
