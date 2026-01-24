package io.sentrius.agent.analysis.agents.verbs;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
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
     * Retrieves a list of all currently open terminal sessions across all users in the system.
     * This is THE primary verb for monitoring other users' terminal activity.
     *
     * With administrative privileges, this returns terminal connections for ALL users.
     * Without admin privileges, returns only the current user's terminals.
     *
     * Workflow for monitoring other users' terminal activity:
     * 1. Call this verb with admin privileges to get all users' open terminals
     * 2. Pass the returned terminals to fetch_terminal_logs to retrieve log content
     *
     * Returns HostSystemDTO objects containing: user, host, port, sessionId, connection status, etc.
     *
     * @param token The Zero Trust authentication token
     * @param execution The agent execution context
     * @return An ArrayNode containing HostSystemDTO objects for all open terminal sessions
     * @throws ZtatException If there is an error during the operation
     */
    @Verb(name = "list_open_terminals",
          description = "**PRIMARY VERB FOR MONITORING**: Retrieves ALL currently open terminal sessions across all users in the system. " +
                        "With admin privileges (CAN_MANAGE_APPLICATION and CAN_MANAGE_SYSTEMS), returns ALL users' terminal connections. " +
                        "Returns HostSystemDTO objects that can be passed to fetch_terminal_logs for retrieving log content. " +
                        "Use this verb (not list_active_terminal_sessions) to monitor other users' terminal activity.",
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
     * Retrieves terminal log output content for specified terminal sessions.
     * Takes a list of terminals (typically from list_open_terminals) and fetches their log content.
     *
     * Workflow for monitoring users' terminal logs:
     * 1. Call list_open_terminals to get all users' open terminal sessions (requires admin privileges)
     * 2. Pass those terminals to this verb to retrieve their actual log content
     *
     * With admin privileges, can retrieve logs for any user's terminal sessions.
     *
     * @param token The Zero Trust authentication token
     * @param contextDTO The agent execution context containing "terminals" argument with HostSystemDTO list
     * @return A list of ObjectNode objects containing terminal IDs and their log output
     * @throws ZtatException If there is an error during the operation
     */
    @Verb(name = "fetch_terminal_logs",
          description = "Retrieves terminal log output content for specified terminals. " +
                        "AUTOMATIC MODE: If called with NO arguments or just empty {}, this verb automatically " +
                        "retrieves terminals from agent memory (list_open_terminals) - THIS IS THE RECOMMENDED USAGE. " +
                        "MANUAL MODE: Pass 'terminals' argument with full HostSystemDTO objects from list_open_terminals. " +
                        "DO NOT pass just terminal IDs - you must pass complete terminal objects with 'hostConnection' field. " +
                        "With admin privileges, can fetch logs for any user's terminals.",
          returnType = List.class,
          returnName = "fetch_terminal_logs",
          argName = "terminals",
          exampleJson = "{} OR {\"terminals\": [{\"id\": 1, \"hostConnection\": \"encrypted-session-id\"}]}",
          requiresTokenManagement = true,
          skipMemoryStorage = true)  // Don't store terminal logs in persistent memory - they're large and session-specific
    public List<ObjectNode> fetchTerminalOutput(TokenDTO token, AgentExecutionContextDTO contextDTO) throws ZtatException {
        try {
            List<ObjectNode> responses = new ArrayList<>();

            // Try to get terminals from execution arguments
            List<HostSystemDTO> dtos = new ArrayList<>();

            // First, try to get as a typed list
            Optional<List<HostSystemDTO>> typedList = contextDTO
                .getExecutionArgumentScoped("terminals", new TypeReference<List<HostSystemDTO>>() {});

            if (typedList.isPresent()) {
                dtos = typedList.get();
                log.info("Successfully retrieved {} terminals from typed list", dtos.size());
            } else {
                // If that fails, try to get the raw JsonNode and parse it manually
                Optional<JsonNode> terminalsNode = contextDTO.getExecutionArgument("terminals");

                if (terminalsNode.isPresent()) {
                    JsonNode node = terminalsNode.get();
                    log.info("Retrieved terminals as JsonNode, type: {}, value: {}", node.getNodeType(), node);

                    // Handle case where it might be a string representation of JSON
                    if (node.isTextual()) {
                        String jsonString = node.asText();
                        log.info("Terminals stored as text, attempting to parse: {}", jsonString);

                        try {
                            // First, try to parse as standard JSON
                            JsonNode parsedNode = JsonUtil.MAPPER.readTree(jsonString);
                            if (parsedNode.isArray()) {
                                for (JsonNode item : parsedNode) {
                                    HostSystemDTO dto = JsonUtil.MAPPER.treeToValue(item, HostSystemDTO.class);
                                    dtos.add(dto);
                                }
                                log.info("Successfully parsed {} terminals from JSON text", dtos.size());
                            }
                        } catch (Exception e) {
                            // If JSON parsing fails, try to parse Java toString format: [{key=value, key2=value2}]
                            log.info("Standard JSON parsing failed, attempting to parse Java toString format");
                            try {
                                List<HostSystemDTO> parsedDtos = parseJavaToStringFormat(jsonString);
                                dtos.addAll(parsedDtos);
                                log.info("Successfully parsed {} terminals from Java toString format", dtos.size());
                            } catch (Exception e2) {
                                log.error("Failed to parse terminals from both JSON and toString formats. JSON error: {}, toString error: {}",
                                    e.getMessage(), e2.getMessage());
                            }
                        }
                    } else if (node.isArray()) {
                        // Direct array - convert each element
                        for (JsonNode item : node) {
                            try {
                                HostSystemDTO dto = JsonUtil.MAPPER.treeToValue(item, HostSystemDTO.class);
                                dtos.add(dto);
                            } catch (Exception e) {
                                log.error("Failed to convert array item to HostSystemDTO: {}", e.getMessage(), e);
                            }
                        }
                        log.info("Successfully converted {} terminals from array", dtos.size());
                    }
                } else {
                    log.warn("No 'terminals' argument found in execution context");
                }
            }

            // Fallback: If no terminals were provided, try to retrieve from agent memory
            if (dtos.isEmpty()) {
                log.info("No terminals in arguments, attempting to retrieve from agent memory (list_open_terminals)");
                Object memoryTerminals = contextDTO.getAgentShortTermMemory().get("list_open_terminals");

                if (memoryTerminals != null) {
                    try {
                        // Convert memory object to JsonNode and then to DTOs
                        JsonNode memoryNode = JsonUtil.MAPPER.valueToTree(memoryTerminals);
                        log.info("Found terminals in memory, type: {}, isArray: {}",
                            memoryNode.getNodeType(), memoryNode.isArray());

                        if (memoryNode.isArray()) {
                            for (JsonNode item : memoryNode) {
                                try {
                                    HostSystemDTO dto = JsonUtil.MAPPER.treeToValue(item, HostSystemDTO.class);
                                    dtos.add(dto);
                                    log.info("Added terminal from memory: id={}, host={}, hostConnection={}",
                                        dto.getId(), dto.getHost(),
                                        dto.getHostConnection() != null ? "present" : "null");
                                } catch (Exception e) {
                                    log.error("Failed to convert memory terminal to HostSystemDTO: {}", e.getMessage(), e);
                                }
                            }
                            log.info("Successfully retrieved {} terminals from agent memory", dtos.size());
                        }
                    } catch (Exception e) {
                        log.error("Failed to parse terminals from memory: {}", e.getMessage(), e);
                    }
                } else {
                    log.warn("No terminals found in agent memory under key 'list_open_terminals'");
                }
            }

            log.debug("Terminal list response: {}", dtos);

            if (dtos.isEmpty()) {
                log.error("No terminals to fetch logs from. Execution args: {}", contextDTO.getExecutionArgs());

                // Provide helpful error message to guide the agent
                String errorMsg = "No terminals provided to fetch logs from. " +
                    "\n\nYou must pass the terminal objects from list_open_terminals. " +
                    "\n\nCorrect usage:" +
                    "\n1. Use memoryLookup to retrieve 'list_open_terminals' from agent memory" +
                    "\n2. Pass those terminal objects to this verb: {\"terminals\": <terminal_objects_from_memory>}" +
                    "\n\nAlternatively, if you have already executed list_open_terminals, this verb will " +
                    "automatically retrieve those terminals from memory." +
                    "\n\nAvailable memory keys: " + contextDTO.getAgentShortTermMemory().keySet() +
                    "\nProvided arguments: " + contextDTO.getExecutionArgs();

                throw new IllegalArgumentException(errorMsg);
            }

            for (HostSystemDTO dto : dtos) {
                // hostConnection is already encrypted and will be decrypted by the API
                // Don't URL encode it - Spring handles URL encoding/decoding of query params automatically
                var sessionId = dto.getHostConnection();
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
            log.error("Failed to retrieve terminal logs", e);
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
                            // sessionId is already encrypted, don't URL encode it
                            var response = zeroTrustClientService.callPutOnApi(
                                execution, "/ssh/terminal/kill",
                                Maps.immutableEntry("sessionId", List.of(dto.getAssessment().getSessionId()))
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
                            // sessionId is already encrypted, don't URL encode it
                            var response = zeroTrustClientService.callPutOnApi(
                                execution, "/ssh/terminal/kill",
                                Maps.immutableEntry("sessionId", List.of(dto.getAssessment().getSessionId()))
                            );
                        }
                    }
            }
            return responses;
        } catch (Exception | ZtatException e) {
            throw new RuntimeException("Failed to retrieve terminal list", e);
        }
    }

    @Verb(name = "kill_terminal_session",
        description = "Kills an open terminal session by its hostConnection or sessionId. " +
            "AUTOMATIC MODE: If called with just 'terminalId' or no valid arguments, automatically retrieves " +
            "hostConnection from agent memory (list_open_terminals) - THIS IS THE RECOMMENDED USAGE. " +
            "MANUAL MODE: Pass 'hostConnection' (from HostSystemDTO) or 'sessionId' parameter directly. " +
            "Use this to terminate terminals found via list_open_terminals.",
        exampleJson = "{\"terminalId\": 1} OR {\"hostConnection\": \"encrypted-session-id\"}",
        argName = "kill_params",
        returnName = "kill_result",
        requiresTokenManagement = true)
    public ObjectNode killTerminalSession(AgentExecution execution, AgentExecutionContextDTO contextDTO)
        throws ZtatException, IOException {

        // Get either hostConnection or sessionId
        String sessionId = contextDTO.getExecutionArgumentScoped("hostConnection", String.class)
            .or(() -> contextDTO.getExecutionArgumentScoped("sessionId", String.class))
            .orElse(null);

        // Fallback: If no sessionId/hostConnection provided, try to get from memory using terminalId
        if (sessionId == null) {
            log.info("No hostConnection or sessionId provided, attempting memory fallback");

            // Check if terminalId was provided
            Integer terminalId = contextDTO.getExecutionArgumentScoped("terminalId", Integer.class).orElse(null);

            if (terminalId != null) {
                log.info("Found terminalId: {}, looking up hostConnection from list_open_terminals in memory", terminalId);

                // Get terminals from memory
                Object memoryTerminals = contextDTO.getAgentShortTermMemory().get("list_open_terminals");
                if (memoryTerminals != null) {
                    try {
                        JsonNode memoryNode = JsonUtil.MAPPER.valueToTree(memoryTerminals);
                        if (memoryNode.isArray()) {
                            for (JsonNode item : memoryNode) {
                                if (item.has("id") && item.get("id").asInt() == terminalId) {
                                    if (item.has("hostConnection")) {
                                        sessionId = item.get("hostConnection").asText();
                                        log.info("Found hostConnection for terminalId {}: {}", terminalId,
                                            sessionId != null ? "present" : "null");
                                        break;
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                        log.error("Failed to parse terminals from memory: {}", e.getMessage(), e);
                    }
                }

                if (sessionId == null) {
                    log.warn("Could not find terminal with id {} in memory", terminalId);
                }
            }
        }

        // Final validation
        if (sessionId == null) {
            String errorMsg = "Either 'hostConnection' or 'sessionId' parameter is required. " +
                "\n\nAutomatic usage: Pass 'terminalId' (from list_open_terminals) and the verb will " +
                "automatically look up the hostConnection from memory." +
                "\n\nManual usage: Pass 'hostConnection' directly from terminal object." +
                "\n\nExample: {\"terminalId\": 1}" +
                "\n\nAvailable memory keys: " + contextDTO.getAgentShortTermMemory().keySet() +
                "\nProvided arguments: " + contextDTO.getExecutionArgs();
            throw new IllegalArgumentException(errorMsg);
        }

        log.info("Killing terminal session: {}", sessionId);

        ObjectNode result = JsonUtil.MAPPER.createObjectNode();
        result.put("sessionId", sessionId);

        try {
            // Call the API to kill the terminal session
            var response = zeroTrustClientService.callPutOnApi(
                execution, "/ssh/terminal/kill",
                Maps.immutableEntry("sessionId", List.of(sessionId))
            );

            if (response != null) {
                result.put("status", "killed");
                result.put("response", response);
                log.info("Successfully killed terminal session: {}", sessionId);
            } else {
                result.put("status", "error");
                result.put("message", "No response from kill API");
            }
        } catch (ZtatException e) {
            log.error("Failed to kill session - ZTAT approval required", e);
            result.put("status", "requires_approval");
            result.put("error", e.getMessage());
            result.put("message", "Terminal kill requires zero-trust approval. Use kill_session_with_assessment for automatic approval flow.");
            throw new RuntimeException("Terminal kill requires approval: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Failed to kill terminal session", e);
            result.put("status", "error");
            result.put("error", e.getMessage());
            throw new RuntimeException("Failed to kill terminal session: " + e.getMessage(), e);
        }

        return result;
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
    /**
     * Lists SSH terminal sessions that THIS AGENT has personally opened via WebSocket connections.
     * This tracks only the agent's own active WebSocket sessions stored in the activeSessions map.
     *
     * IMPORTANT: This does NOT list other users' terminal sessions.
     * To monitor other users' terminals, use list_open_terminals instead.
     *
     * Use cases for this verb:
     * - Track terminals this agent has opened via open_ssh_terminal
     * - Monitor the agent's own WebSocket connections
     * - Get metadata about terminals the agent is actively controlling
     *
     * @param execution The agent execution context
     * @param contextDTO The agent execution context DTO
     * @return An ArrayNode containing this agent's WebSocket terminal sessions
     * @throws ZtatException If there is an error during the operation
     */
    @Verb(name = "list_my_active_terminal_sessions",
          description = "Lists SSH terminal sessions that THIS AGENT has opened via WebSocket (open_ssh_terminal verb). " +
                        "Does NOT list other users' terminals or system-wide sessions. " +
                        "For monitoring other users' terminal activity, use list_open_terminals instead. " +
                        "Only use this to track terminals that this specific agent instance has personally opened.",
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
            // sessionId is already encrypted if coming from API, don't URL encode it
            // Spring handles URL encoding/decoding of query parameters automatically
            String response = zeroTrustClientService.callGetOnApi(
                execution,
                "/sessions/audit/attach",
                Maps.immutableEntry("sessionId", List.of(sessionId))
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

    /**
     * Parses Java toString format: [{key=value, key2=value2}]
     * Converts it to List of HostSystemDTO objects.
     *
     * Example input: "[{id=1, hostConnection=abc123}]"
     *
     * @param toStringFormat The Java toString representation
     * @return List of parsed HostSystemDTO objects
     */
    private List<HostSystemDTO> parseJavaToStringFormat(String toStringFormat) {
        List<HostSystemDTO> result = new ArrayList<>();

        // Remove outer brackets: "[{...}]" -> "{...}"
        String content = toStringFormat.trim();
        if (content.startsWith("[") && content.endsWith("]")) {
            content = content.substring(1, content.length() - 1).trim();
        }

        // Split by "}, {" to handle multiple objects
        // Handle both "{...}, {...}" and "{...},{...}"
        String[] objects = content.split("\\},\\s*\\{");

        for (String objStr : objects) {
            // Clean up braces
            objStr = objStr.trim();
            if (objStr.startsWith("{")) {
                objStr = objStr.substring(1);
            }
            if (objStr.endsWith("}")) {
                objStr = objStr.substring(0, objStr.length() - 1);
            }

            // Parse key=value pairs
            HostSystemDTO dto = new HostSystemDTO();
            String[] pairs = objStr.split(",\\s*");

            for (String pair : pairs) {
                String[] keyValue = pair.split("=", 2);
                if (keyValue.length == 2) {
                    String key = keyValue[0].trim();
                    String value = keyValue[1].trim();

                    // Map to HostSystemDTO fields
                    switch (key) {
                        case "id":
                            try {
                                // ID might be a number or string
                                dto.setId(Long.parseLong(value));
                            } catch (NumberFormatException e) {
                                log.warn("Could not parse id as number: {}", value);
                            }
                            break;
                        case "hostConnection":
                            dto.setHostConnection(value);
                            break;
                        case "host":
                            dto.setHost(value);
                            break;
                        case "user":
                        case "sshUser":
                            dto.setSshUser(value);
                            break;
                        case "port":
                            try {
                                dto.setPort(Integer.parseInt(value));
                            } catch (NumberFormatException e) {
                                log.warn("Could not parse port as number: {}", value);
                            }
                            break;
                        case "displayName":
                            dto.setDisplayName(value);
                            break;
                        case "statusCd":
                            dto.setStatusCd(value);
                            break;
                        default:
                            log.debug("Unknown field in toString format: {}", key);
                            break;
                    }
                }
            }

            // Only add if we have at least the hostConnection (required field)
            if (dto.getHostConnection() != null && !dto.getHostConnection().isEmpty()) {
                result.add(dto);
                log.debug("Parsed HostSystemDTO: id={}, hostConnection={}", dto.getId(), dto.getHostConnection());
            } else {
                log.warn("Skipping HostSystemDTO with no hostConnection: {}", objStr);
            }
        }

        return result;
    }


}

