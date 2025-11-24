package io.sentrius.agent.analysis.api.websocket;

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

import io.sentrius.agent.analysis.model.AgentConfigurationChange;
import io.sentrius.agent.analysis.service.AgentConfigurationApprovalService;
import io.sentrius.agent.analysis.service.RegisteredAnalyticsAgent;
import io.sentrius.sso.core.services.agents.AgentClientService;
import io.sentrius.sso.core.services.agents.ZeroTrustClientService;
import io.sentrius.sso.protobuf.Session;
import io.sentrius.sso.provenance.ProvenanceEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * WebSocket handler for analytics agent chat sessions.
 * Provides interface to query agent state and request configuration changes.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "agents.analytics.chat.enabled", havingValue = "true", matchIfMissing = false)
public class AnalyticsChatWSHandler extends TextWebSocketHandler {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final AnalyticsUserCommunicationService userCommunicationService;
    private final ZeroTrustClientService zeroTrustClientService;
    private final RegisteredAnalyticsAgent analyticsAgent;
    private final AgentClientService agentClientService;
    
    @Autowired(required = false)
    private AgentConfigurationApprovalService approvalService;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        log.info("New analytics chat connection established");
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
        log.info("Analytics chat session {} created for incoming connection", sessionId);

        // Generate and store nonce for this session
        String nonce = UUID.randomUUID().toString();
        session.getAttributes().put("ztatNonce", nonce);
        session.getAttributes().put("ztatToken", ztatToken);
        session.getAttributes().put("sessionId", sessionId);

        // Send challenge to the client
        log.info("Sending challenge to client: {}", nonce);
        var challenge = Session.ChatMessage.newBuilder()
            .setMessage(String.format("{\"type\":\"challenge\",\"nonce\":\"%s\"}", nonce))
            .setSender("analytics-agent")
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
                .triggeringUser(analyticsAgent.getAgentName())
                .outputSummary("Analytics agent chat session established")
                .sessionId(session.getId())
                .build();

            agentClientService.submitProvenance(analyticsAgent.getAgentExecution(), provenanceEvent);
        } catch (Exception e) {
            log.error("Failed to submit provenance event", e);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message)
        throws IOException, GeneralSecurityException {

        URI uri = session.getUri();
        log.info("Received message on analytics chat");
        
        try {
            if (uri != null) {
                Map<String, String> queryParams = parseQueryParams(uri.getQuery());
                String sessionId = queryParams.get("sessionId");

                var websocky = userCommunicationService.getSession(sessionId);

                if (sessionId != null && websocky.isPresent()) {
                    var websocketCommunication = websocky.get();
                    log.info("Processing message from analytics chat session ID: {}", sessionId);

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
                            analyticsAgent.getAgentExecution(), ztat, nonce, signature, publicKey);

                        if (verified) {
                            session.getAttributes().put("verified", true);
                            log.info("ZTAT challenge verified for analytics chat session {}", session.getId());
                        } else {
                            log.warn("ZTAT challenge failed for analytics chat session {}", session.getId());
                            session.close();
                        }
                        return;
                    } else if ("get-status".equals(json.get("type").asText())) {
                        log.info("Received status query from session {}", sessionId);
                        String statusInfo = getStatusInfo();
                        sendTextResponse(session, websocketCommunication, statusInfo);
                        return;
                    } else if ("request-config-change".equals(json.get("type").asText())) {
                        if (approvalService == null) {
                            sendErrorResponse(session, websocketCommunication, "Configuration approval service not available");
                            return;
                        }
                        
                        log.info("Received config change request from session {}", sessionId);
                        String changeType = json.get("changeType").asText();
                        String configKey = json.get("configKey").asText();
                        String newValue = json.get("newValue").asText();
                        String reason = json.get("reason").asText();
                        String requestedBy = json.has("requestedBy") ? json.get("requestedBy").asText() : "admin";
                        
                        try {
                            AgentConfigurationChange change = approvalService.requestChange(
                                AgentConfigurationChange.ChangeType.valueOf(changeType),
                                configKey,
                                "current", // oldValue - could be fetched from current config
                                newValue,
                                requestedBy,
                                reason
                            );
                            
                            String responseMsg = String.format(
                                "Configuration change requested successfully.\n\n" +
                                "Change ID: %s\n" +
                                "Type: %s\n" +
                                "Status: %s\n\n" +
                                "This change requires approval from a second party with a valid ZTAT token.\n" +
                                "Use {\"type\":\"approve-config-change\",\"changeId\":\"%s\",\"approver\":\"username\",\"ztat\":\"token\"} to approve.",
                                change.getChangeId(),
                                change.getChangeType(),
                                change.getStatus(),
                                change.getChangeId()
                            );
                            
                            sendTextResponse(session, websocketCommunication, responseMsg);
                        } catch (Exception e) {
                            log.error("Error requesting config change", e);
                            sendErrorResponse(session, websocketCommunication, "Error requesting config change: " + e.getMessage());
                        }
                        return;
                    } else if ("approve-config-change".equals(json.get("type").asText())) {
                        if (approvalService == null) {
                            sendErrorResponse(session, websocketCommunication, "Configuration approval service not available");
                            return;
                        }
                        
                        log.info("Received config change approval from session {}", sessionId);
                        String changeId = json.get("changeId").asText();
                        String approver = json.get("approver").asText();
                        String ztat = json.get("ztat").asText();
                        
                        try {
                            AgentConfigurationChange change = approvalService.approveChange(changeId, approver, ztat);
                            
                            String responseMsg = String.format(
                                "Configuration change approved and applied successfully.\n\n" +
                                "Change ID: %s\n" +
                                "Type: %s\n" +
                                "Approved by: %s\n" +
                                "Status: %s\n",
                                change.getChangeId(),
                                change.getChangeType(),
                                change.getApprovedBy(),
                                change.getStatus()
                            );
                            
                            sendTextResponse(session, websocketCommunication, responseMsg);
                        } catch (io.sentrius.sso.core.exceptions.ZtatException e) {
                            log.error("Error approving config change - ZTAT", e);
                            sendErrorResponse(session, websocketCommunication, "ZTAT validation failed: " + e.getMessage());
                        } catch (Exception e) {
                            log.error("Error approving config change", e);
                            sendErrorResponse(session, websocketCommunication, "Error approving config change: " + e.getMessage());
                        }
                        return;
                    } else if ("list-pending-changes".equals(json.get("type").asText())) {
                        if (approvalService == null) {
                            sendErrorResponse(session, websocketCommunication, "Configuration approval service not available");
                            return;
                        }
                        
                        log.info("Received list pending changes request from session {}", sessionId);
                        Map<String, AgentConfigurationChange> pending = approvalService.getPendingChanges();
                        
                        StringBuilder responseMsg = new StringBuilder("Pending Configuration Changes\n");
                        responseMsg.append("================================\n\n");
                        
                        if (pending.isEmpty()) {
                            responseMsg.append("No pending changes.\n");
                        } else {
                            pending.forEach((id, change) -> {
                                responseMsg.append(String.format(
                                    "Change ID: %s\n" +
                                    "Type: %s\n" +
                                    "Requested by: %s\n" +
                                    "Requested at: %s\n" +
                                    "Reason: %s\n\n",
                                    change.getChangeId(),
                                    change.getChangeType(),
                                    change.getRequestedBy(),
                                    change.getRequestedAt(),
                                    change.getReason()
                                ));
                            });
                        }
                        
                        sendTextResponse(session, websocketCommunication, responseMsg.toString());
                        return;
                    } else if ("user-message".equals(json.get("type").asText())) {
                        log.info("Received user message from session {}", sessionId);
                        String userMsg = json.get("message").asText();
                        
                        String responseMsg = String.format(
                            "Analytics Agent\n\n" +
                            "Your message: %s\n\n" +
                            "Available commands:\n" +
                            "- {\"type\":\"get-status\"} - Get current agent status\n" +
                            "- {\"type\":\"list-pending-changes\"} - List pending configuration changes\n" +
                            "- {\"type\":\"request-config-change\",\"changeType\":\"TYPE\",\"configKey\":\"key\",\"newValue\":\"value\",\"reason\":\"reason\"} - Request a configuration change\n" +
                            "- {\"type\":\"approve-config-change\",\"changeId\":\"id\",\"approver\":\"username\",\"ztat\":\"token\"} - Approve a configuration change (requires ZTAT)\n\n" +
                            "Note: Configuration changes require two-party approval via ZTAT tokens.",
                            userMsg
                        );
                        
                        sendTextResponse(session, websocketCommunication, responseMsg);
                        return;
                    } else {
                        log.info("Unknown message type, ignoring: {}", chatMessage.getMessage());
                    }
                } else {
                    log.info("Session ID not found in query parameters for message handling.");
                }
            }
        } catch (Exception e) {
            log.error("Error handling analytics chat message", e);
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
                log.info("Analytics chat connection closed, session ID: {}", sessionId);
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
                arr -> URLDecoder.decode(arr[0], StandardCharsets.UTF_8),
                arr -> arr.length > 1 ? URLDecoder.decode(arr[1], StandardCharsets.UTF_8) : ""
            ));
    }
    
    private String getStatusInfo() {
        StringBuilder status = new StringBuilder();
        status.append("Analytics Agent Status\n");
        status.append("======================\n\n");
        status.append("Agent Name: ").append(analyticsAgent.getAgentName()).append("\n");
        status.append("Running: Yes\n");
        status.append("\nThe analytics agent performs trust evaluation, session analysis, and automation suggestions.\n");
        return status.toString();
    }
    
    private void sendTextResponse(WebSocketSession session, AnalyticsWebSocky websocky, String message) throws IOException {
        var response = Session.ChatMessage.newBuilder()
            .setMessage(message)
            .setSender("analytics-agent")
            .setChatGroupId("")
            .setSessionId(websocky.getUniqueIdentifier())
            .setTimestamp(System.currentTimeMillis())
            .build();
        byte[] messageBytes = response.toByteArray();
        String base64Message = Base64.getEncoder().encodeToString(messageBytes);
        session.sendMessage(new TextMessage(base64Message));
    }
    
    private void sendErrorResponse(WebSocketSession session, AnalyticsWebSocky websocky, String errorMessage) throws IOException {
        String message = "Error: " + errorMessage;
        sendTextResponse(session, websocky, message);
    }
}
