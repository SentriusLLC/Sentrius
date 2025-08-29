package io.sentrius.sso.mcp.websocket;

import io.sentrius.sso.core.services.security.CryptoService;
import io.sentrius.sso.core.services.security.KeycloakService;
import io.sentrius.sso.mcp.service.MCPProxyService;
import io.sentrius.sso.mcp.model.MCPRequest;
import io.sentrius.sso.mcp.model.MCPResponse;
import io.sentrius.sso.mcp.model.MCPError;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * WebSocket handler for MCP (Model Context Protocol) real-time communication
 * Provides secure WebSocket endpoints with zero trust validation
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class MCPWebSocketHandler implements WebSocketHandler {

    private final KeycloakService keycloakService;
    private final MCPProxyService mcpProxyService;
    private final CryptoService cryptoService;
    private final ObjectMapper objectMapper;
    
    // Track active sessions
    private final Map<String, WebSocketSession> activeSessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        log.info("MCP WebSocket connection established: {}", session.getId());
        
        // Validate connection parameters
        String token = getSessionAttribute(session, "token");
        String communicationId = getSessionAttribute(session, "communication_id");
        String userId = getSessionAttribute(session, "user_id");
        
        if (!validateConnection(token, userId)) {
            log.warn("Invalid connection attempt for session: {}", session.getId());
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("Invalid authentication"));
            return;
        }
        
        activeSessions.put(session.getId(), session);
        
        // Send welcome message
        sendWelcomeMessage(session, userId);
    }

    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
        String sessionId = session.getId();
        log.debug("Received MCP WebSocket message from session: {}", sessionId);
        
        if (message instanceof TextMessage textMessage) {
            handleTextMessage(session, textMessage);
        } else if (message instanceof BinaryMessage binaryMessage) {
            handleBinaryMessage(session, binaryMessage);
        } else {
            log.warn("Unsupported message type: {}", message.getClass());
            sendErrorMessage(session, "Unsupported message type");
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("MCP WebSocket transport error for session: {}", session.getId(), exception);
        activeSessions.remove(session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
        log.info("MCP WebSocket connection closed: {} with status: {}", session.getId(), closeStatus);
        activeSessions.remove(session.getId());
    }

    @Override
    public boolean supportsPartialMessages() {
        return false;
    }

    /**
     * Handle text-based MCP messages
     */
    private void handleTextMessage(WebSocketSession session, TextMessage textMessage) {
        try {
            String payload = textMessage.getPayload();
            log.debug("Processing MCP text message: {}", payload);
            
            // Parse MCP request
            MCPRequest mcpRequest = objectMapper.readValue(payload, MCPRequest.class);
            
            // Validate request
            if (mcpRequest.getMethod() == null || mcpRequest.getId() == null) {
                sendMCPResponse(session, MCPResponse.error(
                    mcpRequest.getId(), 
                    MCPError.invalidRequest("Missing required fields")
                ));
                return;
            }
            
            // Get session context
            String token = getSessionAttribute(session, "token");
            String communicationId = getSessionAttribute(session, "communication_id");
            String userId = getSessionAttribute(session, "user_id");
            
            // Process request through service layer
            MCPResponse response = mcpProxyService.processRequest(mcpRequest, token, communicationId, userId);
            
            // Send response back to client
            sendMCPResponse(session, response);
            
        } catch (Exception e) {
            log.error("Error handling MCP text message", e);
            sendErrorMessage(session, "Error processing message");
        }
    }

    /**
     * Handle binary MCP messages (for future binary protocol support)
     */
    private void handleBinaryMessage(WebSocketSession session, BinaryMessage binaryMessage) {
        log.warn("Binary MCP messages not yet supported");
        sendErrorMessage(session, "Binary messages not supported");
    }

    /**
     * Validate WebSocket connection parameters
     */
    private boolean validateConnection(String token, String userId) {
        if (token == null || userId == null) {
            log.warn("Missing required connection parameters");
            return false;
        }
        
        try {
            // Extract JWT from Bearer token
            String jwt = token.startsWith("Bearer ") ? token.substring(7) : token;
            return keycloakService.validateJwt(jwt);
        } catch (Exception e) {
            log.error("Error validating connection", e);
            return false;
        }
    }

    /**
     * Send welcome message when connection is established
     */
    private void sendWelcomeMessage(WebSocketSession session, String userId) {
        try {
            MCPResponse welcome = MCPResponse.success("welcome", Map.of(
                "message", "Connected to Sentrius MCP Proxy",
                "userId", userId,
                "protocolVersion", "2024-11-05",
                "capabilities", Map.of(
                    "tools", Map.of("listChanged", true),
                    "resources", Map.of("subscribe", true, "listChanged", true),
                    "prompts", Map.of("listChanged", true)
                )
            ));
            
            sendMCPResponse(session, welcome);
        } catch (Exception e) {
            log.error("Error sending welcome message", e);
        }
    }

    /**
     * Send MCP response message
     */
    private void sendMCPResponse(WebSocketSession session, MCPResponse response) {
        try {
            String json = objectMapper.writeValueAsString(response);
            session.sendMessage(new TextMessage(json));
        } catch (Exception e) {
            log.error("Error sending MCP response", e);
        }
    }

    /**
     * Send error message to client
     */
    private void sendErrorMessage(WebSocketSession session, String errorMessage) {
        try {
            MCPResponse error = MCPResponse.error("error", MCPError.internalError(errorMessage));
            sendMCPResponse(session, error);
        } catch (Exception e) {
            log.error("Error sending error message", e);
        }
    }

    /**
     * Get session attribute safely
     */
    private String getSessionAttribute(WebSocketSession session, String attributeName) {
        Object attribute = session.getAttributes().get(attributeName);
        return attribute != null ? attribute.toString() : null;
    }

    /**
     * Broadcast message to all active sessions (for notifications)
     */
    public void broadcastMessage(MCPResponse message) {
        activeSessions.values().forEach(session -> {
            try {
                sendMCPResponse(session, message);
            } catch (Exception e) {
                log.error("Error broadcasting message to session: {}", session.getId(), e);
            }
        });
    }
}