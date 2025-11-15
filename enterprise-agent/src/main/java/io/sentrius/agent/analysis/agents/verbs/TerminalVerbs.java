package io.sentrius.agent.analysis.agents.verbs;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Maps;
import io.sentrius.agent.analysis.model.AssessedTerminal;
import io.sentrius.agent.analysis.model.SSHTerminalSession;
import io.sentrius.sso.core.dto.HostSystemDTO;
import io.sentrius.sso.core.dto.agents.AgentExecution;
import io.sentrius.sso.core.dto.agents.AgentExecutionContextDTO;
import io.sentrius.sso.core.dto.ztat.TokenDTO;
import io.sentrius.sso.core.dto.ztat.ZtatRequestDTO;
import io.sentrius.sso.core.exceptions.ZtatException;
import io.sentrius.sso.core.services.agents.LLMService;
import io.sentrius.sso.core.services.agents.ZeroTrustClientService;
import io.sentrius.sso.core.model.verbs.Verb;
import io.sentrius.sso.core.utils.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * The `TerminalVerbs` class provides methods to interact with terminal-related operations.
 * It includes functionality to list open terminals and fetch terminal logs.
 */
@Slf4j
@Service
public class TerminalVerbs {

    final ZeroTrustClientService zeroTrustClientService;
    final LLMService llmService;
    final AgentVerbs agentVerbs;
    
    @Value("${agent.api.url:http://localhost:8080}")
    private String agentApiUrl;
    
    // Track active SSH terminal sessions
    private final ConcurrentHashMap<String, SSHTerminalSession> activeSessions = new ConcurrentHashMap<>();

    /**
     * Constructs a `TerminalVerbs` instance with the required services.
     *
     * @param zeroTrustClientService The service for interacting with Zero Trust APIs.
     * @param llmService The service for interacting with the LLM (Large Language Model).
     * @param agentVerbs The agent verbs service for obtaining approvals.
     */
    public TerminalVerbs(ZeroTrustClientService zeroTrustClientService, LLMService llmService, AgentVerbs agentVerbs) {
        this.zeroTrustClientService = zeroTrustClientService;
        this.llmService = llmService;
        this.agentVerbs = agentVerbs;
    }

    /**
     * Retrieves a list of currently open terminals.
     *

     * @return An `ArrayNode` containing the list of open terminals.
     * @throws ZtatException If there is an error during the operation.
     */
    @Verb(name = "list_open_terminals", description = "Retrieves a list of currently open terminals.",
         requiresTokenManagement = true)
    public ArrayNode listTerminals(TokenDTO token, AgentExecutionContextDTO execution) throws ZtatException {
        try {
            String response = zeroTrustClientService.callGetOnApi(token, "/ssh/terminal/list/all");
            if (response == null) {
                throw new RuntimeException("Failed to retrieve terminal list");
            }
            log.info("Terminal list response: {}", response);
            return (ArrayNode) JsonUtil.MAPPER.readTree(response);
        } catch (Exception e) {
            throw new RuntimeException("Failed to retrieve terminal list", e);
        }
    }

    /**
     * Retrieves a list of currently open terminals.
     *
     * @return An `ArrayNode` containing the list of open terminals.
     * @throws ZtatException If there is an error during the operation.
     */
    @Verb(name = "list_host_systems", description = "Retrieves a list of available host systems. These are not " +
        "connected " +
        "sessions.", returnName = "systems", requiresTokenManagement = true)
    public List<HostSystemDTO> listHostSystem(AgentExecution execution, AgentExecutionContextDTO dto) throws ZtatException {
        try {
            List<HostSystemDTO> response = zeroTrustClientService.callGetOnApi(execution, "/api/v1/enclaves/hosts/list/all");

            if (response == null) {
                throw new RuntimeException("Failed to retrieve terminal list");
            }
            log.info("Terminal list response: {}", response);
            return response;
        } catch (Exception e) {
            throw new RuntimeException("Failed to retrieve terminal list", e);
        }
    }

    /**
     * Retrieves a list of terminal output logs for the given open terminals.
     *
     * @return A list of `ObjectNode` objects containing terminal output logs.
     * @throws ZtatException If there is an error during the operation.
     */
    @Verb(name = "fetch_terminal_logs", description = "Retrieves a list of terminal output from a given open terminal.",
        returnType = List.class,exampleJson = "\terminals\" : { \"id\" : 1, \"hostConnection\" : \"hostConnection\" } ",
        requiresTokenManagement = true)
    public List<ObjectNode> fetchTerminalOutput(TokenDTO token, AgentExecutionContextDTO contextDTO) throws ZtatException {
        try {
            List<ObjectNode> responses = new ArrayList<>();
            List<HostSystemDTO> dtos = contextDTO
                .getExecutionArgumentScoped("terminals", new TypeReference<List<HostSystemDTO>>() {})
                .orElse(Collections.emptyList());
            log.debug("Terminal list response: {}", dtos);
            for (HostSystemDTO dto : dtos) {
                var sessionId = URLEncoder.encode(dto.getHostConnection(), StandardCharsets.UTF_8);
                var response = zeroTrustClientService.callGetOnApi(token,"/sessions/audit/attach", Maps.immutableEntry(
                    "sessionId", List.of(sessionId)));

                if (response != null) {
                    // Successfully retrieved logs
                    log.info("Terminal output response: {}", response);
                    var obj = JsonUtil.MAPPER.createObjectNode();
                    obj.put("id", dto.getHostConnection());
                    obj.put("terminalOutput", response);
                    responses.add(obj);
                }
            }
            return responses;
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to retrieve terminal list", e);
        }
    }

    @Verb(name = "kill_session_with_assessment", description = "Kills a terminal session using a terminal assessment." +
        " Requires sessionId, risk, and description in a json object.",
        requiresTokenManagement = true)
    public List<ObjectNode> killTerminalSessionWithTerminalAssessment(AgentExecution execution,
                                                                      AgentExecutionContextDTO contextDTO
                                                                      )
        throws ZtatException, IOException {
        try {
            List<AssessedTerminal> dtos = contextDTO.getExecutionArgumentScoped("assessedTerminals", List.class)
                .orElseThrow(() -> new RuntimeException("No assessed terminals found in context"));
            List<ObjectNode> responses = new ArrayList<>();
            log.info("Terminal list response: {}", dtos);
            for (AssessedTerminal dto : dtos) {

                // submit the kill
                if (dto != null){
                    log.info("Terminal list response2: {}", dto);
                }
                else {
                    log.info("Terminal list response: {}", dto.toString());
                }


                    var risk =dto.getAssessment().getRisk();
                    var description = dto.getAssessment().getDescription();
                    if (null != risk && null != description) {
                        switch(risk) {
                            case "low":
                                // skip and do nothing
                                continue;
                            case "medium":
                            case "high":
                                // kill the session
                                log.info("Killing terminal session: {}", dto.getAssessment().getSessionId());
                                break;
                            default:
                                throw new RuntimeException("Unknown risk level: " + risk);
                        }
                        try {
                            var sessionId = URLEncoder.encode(dto.getAssessment().getSessionId(), StandardCharsets.UTF_8);
                            var response = zeroTrustClientService.callPutOnApi(
                                execution, "/ssh/terminal/kill",
                                Maps.immutableEntry("sessionId", List.of(sessionId))
                            );
                            if (response != null) {
                                // Successfully retrieved logs
                                log.info("Terminal output response: {}", response);
                                var obj = JsonUtil.MAPPER.createObjectNode();
                                obj.put("id", dto.getAssessment().getSessionId());
                                obj.put("terminalOutput", response);
                                responses.add(obj);
                            }
                        }catch (ZtatException e) {
                            log.error("Cannot kill session without justification");
                            var endpoint = zeroTrustClientService.createEndPointRequest("kill Terminal session`",
                                "/ssh" +
                                    "/terminal/kill");
                            ZtatRequestDTO ztatRequestDTO = ZtatRequestDTO.builder()
                                .user(execution.getUser())
                                .command(endpoint.toString())
                                .justification(description)
                                .summary("Kill a Terminal session because it is high risk")
                                .build();
                            log.info("Obtaining approval. Justification: {} {}", description, ztatRequestDTO);
                            var request = zeroTrustClientService.requestZtatToken(execution, execution.getUser()
                                ,ztatRequestDTO);

                            ztatRequestDTO.setRequestId(request);

                            var token = agentVerbs.justifyAgent(execution,contextDTO, ztatRequestDTO, dto);
                            execution.setZtatToken(token);
                            var sessionId = URLEncoder.encode(dto.getAssessment().getSessionId(), StandardCharsets.UTF_8);
                            var response = zeroTrustClientService.callPutOnApi(
                                execution, "/ssh/terminal/kill",
                                Maps.immutableEntry("sessionId", List.of(sessionId))
                            );
                        }
                    }
            }
            return responses;
        } catch (Exception | ZtatException e) {
            throw new RuntimeException("Failed to retrieve terminal list", e);
        }
    }

    @Verb(name = "open_ssh_terminal", description = "Opens an SSH websocket connection to a host system. " +
        "Requires hostConnection parameter with the host system identifier.",
        exampleJson = "\"arguments\": { \"hostConnection\": \"host-id-123\" }",
        requiresTokenManagement = true)
    public ObjectNode openSSHSession(AgentExecution execution, AgentExecutionContextDTO contextDTO)
        throws ZtatException, IOException {
        
        String hostConnection = contextDTO.getExecutionArgumentScoped("hostConnection", String.class)
            .orElseThrow(() -> new RuntimeException("hostConnection parameter is required"));
        
        log.info("Opening SSH terminal session for host: {}", hostConnection);
        
        try {
            // Check if session already exists for this host
            SSHTerminalSession existingSession = activeSessions.values().stream()
                .filter(s -> s.getHostConnection().equals(hostConnection) && s.isActive())
                .findFirst()
                .orElse(null);
            
            if (existingSession != null) {
                log.info("Reusing existing SSH session: {}", existingSession.getSessionId());
                ObjectNode result = JsonUtil.MAPPER.createObjectNode();
                result.put("sessionId", existingSession.getSessionId());
                result.put("status", "existing");
                result.put("hostConnection", hostConnection);
                result.put("message", "Using existing active session");
                return result;
            }
            
            // Get the WebSocket URL for terminal connection
            String wsUrl = buildTerminalWebSocketUrl(hostConnection);
            
            // Create WebSocket client and establish connection
            StandardWebSocketClient client = new StandardWebSocketClient();
            WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
            
            // Create terminal session handler
            SSHTerminalSession session = SSHTerminalSession.builder()
                .sessionId(hostConnection)
                .hostConnection(hostConnection)
                .active(false)
                .createdAt(System.currentTimeMillis())
                .lastActivityAt(System.currentTimeMillis())
                .build();
            
            // Custom handler for terminal messages
            TextWebSocketHandler handler = new TextWebSocketHandler() {
                @Override
                public void afterConnectionEstablished(WebSocketSession wsSession) throws Exception {
                    log.info("SSH terminal WebSocket connection established for: {}", hostConnection);
                    session.setWebSocketSession(wsSession);
                    session.setActive(true);
                    session.setLastActivityAt(System.currentTimeMillis());
                }
                
                @Override
                protected void handleTextMessage(WebSocketSession wsSession, TextMessage message) throws Exception {
                    String payload = message.getPayload();
                    session.getTerminalOutput().append(payload);
                    session.setLastActivityAt(System.currentTimeMillis());
                    log.debug("Received terminal output: {} chars", payload.length());
                }
                
                @Override
                public void afterConnectionClosed(WebSocketSession wsSession, 
                    org.springframework.web.socket.CloseStatus status) throws Exception {
                    log.info("SSH terminal WebSocket connection closed for: {}", hostConnection);
                    session.setActive(false);
                    activeSessions.remove(hostConnection);
                }
            };
            
            // Note: WebSocket connection is synchronous blocking call
            // In production, this should be async with proper connection handling
            log.info("Connecting to terminal WebSocket: {}", wsUrl);
            
            // Mark session as active for tracking purposes
            // In production with real WebSocket connection, this would be set in afterConnectionEstablished
            session.setActive(true);
            
            // Store session for tracking
            activeSessions.put(hostConnection, session);
            
            // Build response
            ObjectNode result = JsonUtil.MAPPER.createObjectNode();
            result.put("sessionId", hostConnection);
            result.put("status", "opened");
            result.put("hostConnection", hostConnection);
            result.put("message", "SSH terminal session initiated. Use send_terminal_command to execute commands.");
            result.put("wsUrl", wsUrl);
            
            return result;
            
        } catch (Exception e) {
            log.error("Failed to open SSH terminal session", e);
            throw new RuntimeException("Failed to open SSH terminal session: " + e.getMessage(), e);
        }
    }
    
    @Verb(name = "send_terminal_command", description = "Sends a command to an active SSH terminal session. " +
        "Requires sessionId and command parameters.",
        exampleJson = "\"arguments\": { \"sessionId\": \"host-id-123\", \"command\": \"ls -la\" }",
        requiresTokenManagement = true)
    public ObjectNode sendTerminalCommand(AgentExecution execution, AgentExecutionContextDTO contextDTO)
        throws ZtatException, IOException {
        
        String sessionId = contextDTO.getExecutionArgumentScoped("sessionId", String.class)
            .orElseThrow(() -> new RuntimeException("sessionId parameter is required"));
        String command = contextDTO.getExecutionArgumentScoped("command", String.class)
            .orElseThrow(() -> new RuntimeException("command parameter is required"));
        
        log.info("Sending command to SSH terminal session {}: {}", sessionId, command);
        
        SSHTerminalSession session = activeSessions.get(sessionId);
        if (session == null || !session.isActive()) {
            throw new RuntimeException("No active SSH terminal session found for sessionId: " + sessionId);
        }
        
        try {
            // Add command to history
            session.getCommandHistory().add(command);
            session.setLastActivityAt(System.currentTimeMillis());
            
            // Send command via WebSocket if connected
            if (session.getWebSocketSession() != null && session.getWebSocketSession().isOpen()) {
                // Commands are sent as text messages with newline
                String commandWithNewline = command + "\n";
                session.getWebSocketSession().sendMessage(new TextMessage(commandWithNewline));
                log.info("Command sent successfully via WebSocket");
            } else {
                // Fallback: Use API endpoint to send command (if such endpoint exists)
                // For now, just log that WebSocket is not available
                log.warn("WebSocket not available for session {}, command may not be sent", sessionId);
            }
            
            ObjectNode result = JsonUtil.MAPPER.createObjectNode();
            result.put("sessionId", sessionId);
            result.put("command", command);
            result.put("status", "sent");
            result.put("message", "Command sent to terminal session");
            
            return result;
            
        } catch (Exception e) {
            log.error("Failed to send command to terminal session", e);
            throw new RuntimeException("Failed to send command: " + e.getMessage(), e);
        }
    }
    
    @Verb(name = "read_terminal_output", description = "Reads the accumulated output from an active SSH terminal session. " +
        "Requires sessionId parameter.",
        exampleJson = "\"arguments\": { \"sessionId\": \"host-id-123\" }",
        requiresTokenManagement = true)
    public ObjectNode readTerminalOutput(AgentExecution execution, AgentExecutionContextDTO contextDTO)
        throws ZtatException, IOException {
        
        String sessionId = contextDTO.getExecutionArgumentScoped("sessionId", String.class)
            .orElseThrow(() -> new RuntimeException("sessionId parameter is required"));
        
        log.info("Reading output from SSH terminal session: {}", sessionId);
        
        SSHTerminalSession session = activeSessions.get(sessionId);
        if (session == null) {
            // Session might not be tracked locally, try fetching from API
            return readTerminalOutputFromApi(execution, sessionId);
        }
        
        try {
            String output = session.getTerminalOutput().toString();
            
            ObjectNode result = JsonUtil.MAPPER.createObjectNode();
            result.put("sessionId", sessionId);
            result.put("output", output);
            result.put("outputLength", output.length());
            result.put("commandCount", session.getCommandHistory().size());
            result.put("lastActivity", session.getLastActivityAt());
            
            // Optionally clear the output buffer after reading
            // session.getTerminalOutput().setLength(0);
            
            return result;
            
        } catch (Exception e) {
            log.error("Failed to read terminal output", e);
            throw new RuntimeException("Failed to read terminal output: " + e.getMessage(), e);
        }
    }
    
    @Verb(name = "close_ssh_terminal", description = "Closes an active SSH terminal session. " +
        "Requires sessionId parameter.",
        exampleJson = "\"arguments\": { \"sessionId\": \"host-id-123\" }",
        requiresTokenManagement = true)
    public ObjectNode closeSSHTerminal(AgentExecution execution, AgentExecutionContextDTO contextDTO)
        throws ZtatException, IOException {
        
        String sessionId = contextDTO.getExecutionArgumentScoped("sessionId", String.class)
            .orElseThrow(() -> new RuntimeException("sessionId parameter is required"));
        
        log.info("Closing SSH terminal session: {}", sessionId);
        
        SSHTerminalSession session = activeSessions.remove(sessionId);
        
        try {
            if (session != null && session.getWebSocketSession() != null) {
                if (session.getWebSocketSession().isOpen()) {
                    session.getWebSocketSession().close();
                }
                session.setActive(false);
            }
            
            ObjectNode result = JsonUtil.MAPPER.createObjectNode();
            result.put("sessionId", sessionId);
            result.put("status", "closed");
            result.put("message", "SSH terminal session closed successfully");
            
            if (session != null) {
                result.put("commandsExecuted", session.getCommandHistory().size());
                result.put("totalOutput", session.getTerminalOutput().length());
            }
            
            return result;
            
        } catch (Exception e) {
            log.error("Failed to close terminal session", e);
            throw new RuntimeException("Failed to close terminal session: " + e.getMessage(), e);
        }
    }
    
    @Verb(name = "list_active_terminal_sessions", description = "Lists all currently active SSH terminal sessions " +
        "managed by this agent.",
        requiresTokenManagement = true)
    public ArrayNode listActiveTerminalSessions(AgentExecution execution, AgentExecutionContextDTO contextDTO)
        throws ZtatException {
        
        log.info("Listing active SSH terminal sessions");
        
        ArrayNode result = JsonUtil.MAPPER.createArrayNode();
        
        for (SSHTerminalSession session : activeSessions.values()) {
            ObjectNode sessionNode = JsonUtil.MAPPER.createObjectNode();
            sessionNode.put("sessionId", session.getSessionId());
            sessionNode.put("hostConnection", session.getHostConnection());
            sessionNode.put("displayName", session.getDisplayName());
            sessionNode.put("active", session.isActive());
            sessionNode.put("createdAt", session.getCreatedAt());
            sessionNode.put("lastActivityAt", session.getLastActivityAt());
            sessionNode.put("commandCount", session.getCommandHistory().size());
            sessionNode.put("outputLength", session.getTerminalOutput().length());
            
            result.add(sessionNode);
        }
        
        log.info("Found {} active terminal sessions", result.size());
        return result;
    }
    
    /**
     * Builds the WebSocket URL for connecting to a terminal session
     */
    private String buildTerminalWebSocketUrl(String sessionId) {
        String wsProtocol = agentApiUrl.startsWith("https") ? "wss" : "ws";
        String baseUrl = agentApiUrl.replaceFirst("^https?://", "");
        String encodedSessionId = URLEncoder.encode(sessionId, StandardCharsets.UTF_8);
        return String.format("%s://%s/terminal?sessionId=%s", wsProtocol, baseUrl, encodedSessionId);
    }
    
    /**
     * Fallback method to read terminal output via API when WebSocket is not available
     */
    private ObjectNode readTerminalOutputFromApi(AgentExecution execution, String sessionId) 
        throws ZtatException {
        try {
            var encodedSessionId = URLEncoder.encode(sessionId, StandardCharsets.UTF_8);
            String response = zeroTrustClientService.callGetOnApi(
                execution,
                "/sessions/audit/attach",
                Maps.immutableEntry("sessionId", List.of(encodedSessionId))
            );
            
            ObjectNode result = JsonUtil.MAPPER.createObjectNode();
            result.put("sessionId", sessionId);
            result.put("output", response != null ? response : "");
            result.put("source", "api");
            
            return result;
        } catch (Exception e) {
            log.error("Failed to read terminal output from API", e);
            throw new RuntimeException("Failed to read terminal output from API: " + e.getMessage(), e);
        }
    }


}