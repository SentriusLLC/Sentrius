package io.sentrius.sso.rdpproxy.servlet;

import io.sentrius.sso.automation.auditing.AccessTokenEvaluator;
import io.sentrius.sso.automation.auditing.RuleFactory;
import io.sentrius.sso.automation.auditing.SessionTokenEvaluator;
import io.sentrius.sso.rdpproxy.security.AsymmetricJwtService;
import io.sentrius.sso.rdpproxy.service.GuacamoleRdpService;
import io.sentrius.sso.rdpproxy.service.RdpCommandProcessor;
import io.sentrius.sso.rdpproxy.service.RdpScreenshotCaptureService;
import io.sentrius.sso.core.config.SystemOptions;
import io.sentrius.sso.core.model.ConnectedSystem;
import io.sentrius.sso.core.model.hostgroup.HostGroup;
import io.sentrius.sso.core.model.hostgroup.ProfileRule;
import io.sentrius.sso.core.model.users.User;
import io.sentrius.sso.core.services.HostGroupService;
import io.sentrius.sso.core.services.PluggableServices;
import io.sentrius.sso.core.services.UserService;
import io.sentrius.sso.core.services.terminal.SessionTrackingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.stereotype.Component;
import org.apache.guacamole.net.GuacamoleTunnel;
import org.apache.guacamole.GuacamoleException;
import org.apache.guacamole.io.GuacamoleReader;
import org.apache.guacamole.io.GuacamoleWriter;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.io.IOException;
import java.io.ByteArrayOutputStream;

/**
 * Spring WebSocket handler that provides Guacamole tunnel functionality
 * with JWT authentication integration and full protocol support for screen streaming.
 * 
 * This implements the complete Guacamole protocol for bidirectional communication
 * between the web client and guacd daemon.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GuacamoleTunnelWebSocketHandler extends TextWebSocketHandler {

    private final GuacamoleRdpService guacamoleRdpService;
    private final AsymmetricJwtService asymmetricJwtService;
    private final RdpCommandProcessor rdpCommandProcessor;
    private final UserService userService;
    private final HostGroupService hostGroupService;
    private final SessionTrackingService sessionTrackingService;
    private final SystemOptions systemOptions;
    private final ApplicationContext applicationContext;
    private final RdpScreenshotCaptureService screenshotCaptureService;
    private final ConcurrentMap<String, GuacamoleTunnel> sessionTunnels = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, GuacamoleWriter> sessionWriters = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ConnectedSystem> sessionConnectedSystems = new ConcurrentHashMap<>();
    private final ExecutorService executorService = Executors.newCachedThreadPool();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        log.info("WebSocket connection established from: {}", session.getRemoteAddress());
        
        // Check if system is in lockdown mode
        if (systemOptions.getLockdownEnabled()) {
            log.warn("RDP access denied: system is in lockdown mode");
            String errorMsg = "RDP access is disabled by system lockdown";
            String errorInstruction = "5.error," + errorMsg.length() + "." + errorMsg + ",1.0;";
            session.sendMessage(new TextMessage(errorInstruction));
            session.close();
            return;
        }
        
        // Extract JWT token from query parameters
        String jwtToken = getTokenFromSession(session);
        if (jwtToken == null) {
            log.warn("No JWT token provided for Guacamole WebSocket connection");
            session.close();
            return;
        }
        
        try {
            // Validate JWT token with asymmetric cryptography
            asymmetricJwtService.validateJwtToken(jwtToken);
            
            // Create RDP connection through JWT authentication
            GuacamoleTunnel tunnel = guacamoleRdpService.createRdpTunnel(jwtToken);
            
            // Store tunnel for this session
            sessionTunnels.put(session.getId(), tunnel);
            
            // Create ConnectedSystem and initialize session rules for input analysis
            ConnectedSystem connectedSystem = createConnectedSystemFromJwt(jwtToken, session.getId());
            sessionConnectedSystems.put(session.getId(), connectedSystem);
            
            // Initialize session rules (like DeletePrevention, SudoPrevention) for keyboard input
            initializeSessionRules(connectedSystem);
            
            // Start screenshot capture for this session
            screenshotCaptureService.startCapture(session.getId(), connectedSystem);
            
            log.info("Successfully created Guacamole tunnel with JWT authentication for session: {}", session.getId());
            
            // Send ready instruction with tunnel UUID to client (Guacamole protocol)
            // UUID format: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx (36 characters)
            
            // Start reading from tunnel and sending to WebSocket
            startTunnelReader(session, tunnel);

            String readyInstruction = "5.ready,36." + tunnel.getUUID().toString() + ";";
            session.sendMessage(new TextMessage(readyInstruction));
            
        } catch (Exception e) {
            log.error("Failed to create Guacamole tunnel for WebSocket session", e);
            // Send error instruction in Guacamole protocol format
            String errorMsg = e.getMessage() != null ? e.getMessage() : "Connection failed";
            String errorInstruction = "5.error," + errorMsg.length() + "." + errorMsg + ",1.0;";
            session.sendMessage(new TextMessage(errorInstruction));
            session.close();
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        GuacamoleTunnel tunnel = sessionTunnels.get(session.getId());
        if (tunnel == null) {
            log.warn("No tunnel found for session: {}", session.getId());
            return;
        }
        
        try {
            String payload = message.getPayload();
            
            // Parse Guacamole protocol instruction to extract opcode
            GuacamoleInstruction instruction = parseGuacamoleInstruction(payload);
            
            // Check if this is an input event that should be analyzed by RdpCommandProcessor
            if (shouldAnalyzeInstruction(instruction)) {
                log.info("Analyzing input instruction: {} for session: {}", instruction.getOpcode(), session.getId());
                ConnectedSystem connectedSystem = sessionConnectedSystems.get(session.getId());
                if (connectedSystem != null) {
                    log.info("Found ConnectedSystem for session: {}", session.getId());
                    // Convert Guacamole instruction to RdpInputEvent
                    RdpCommandProcessor.RdpInputEvent inputEvent = convertToRdpInputEvent(instruction);
                    
                    if (inputEvent != null) {
                        // Process through RdpCommandProcessor for analysis and policy enforcement
                        ByteArrayOutputStream dummyOutput = new ByteArrayOutputStream();
                        boolean allowed = rdpCommandProcessor.processInputEvent(connectedSystem, inputEvent, dummyOutput);
                        
                        if (!allowed) {
                            log.warn("Input event blocked by RdpCommandProcessor: {} for session: {}", 
                                instruction.getOpcode(), session.getId());
                            // Send error message back to client
                            String errorMsg = "Input blocked by security policy";
                            String errorInstruction = "5.error," + errorMsg.length() + "." + errorMsg + ",1.0;";
                            session.sendMessage(new TextMessage(errorInstruction));
                            return; // Don't forward to tunnel
                        }
                        
                        log.debug("Input event allowed by RdpCommandProcessor: {} for session: {}", 
                            instruction.getOpcode(), session.getId());
                    }
                } else {
                    log.warn("No ConnectedSystem found for session: {}", session.getId());
                }
            } else {
                log.trace("Non-input instruction received: {} for session: {}", instruction.getOpcode(),
                    session.getId());
            }
            
            // Forward message to Guacamole tunnel
            var writer = sessionWriters.get(session.getId());
            if (null == writer) {
                writer = tunnel.acquireWriter();
                sessionWriters.put(session.getId(), writer);
            }
            log.trace("From browser -> tunnel: {}", payload);

            writer.write(payload.toCharArray());

            
            log.trace("Forwarded {} chars to tunnel", payload.length());
            
        } catch (GuacamoleException e) {
            log.error("Error forwarding message to tunnel for session: " + session.getId(), e);
            String errorMsg = e.getMessage() != null ? e.getMessage() : "Write error";
            String errorInstruction = "5.error," + errorMsg.length() + "." + errorMsg + ",1.0;";
            session.sendMessage(new TextMessage(errorInstruction));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, org.springframework.web.socket.CloseStatus status) throws Exception {
        GuacamoleTunnel tunnel = sessionTunnels.remove(session.getId());
        ConnectedSystem connectedSystem = sessionConnectedSystems.remove(session.getId());
        
        // Stop screenshot capture
        screenshotCaptureService.stopCapture(session.getId());
        
        // Clean up session rules and buffers in RdpCommandProcessor
        if (connectedSystem != null && connectedSystem.getSession() != null) {
            rdpCommandProcessor.clearSession(connectedSystem.getSession().getId());
        }
        
        if (tunnel != null) {
            try{
                tunnel.releaseWriter();
            }
            catch(Exception e){
                log.error("Error releasing Guacamole writer for session: " + session.getId(), e);
            }
            try{
                tunnel.close();
            }
            catch(Exception e){
                log.error("Error closing Guacamole tunnel for session: " + session.getId(), e);
            }
            try {



                guacamoleRdpService.disconnectTunnel(session.getId());
                log.info("Closed Guacamole tunnel for WebSocket session: {}", session.getId());
            } catch (Exception e) {
                log.error("Error closing Guacamole tunnel for session: " + session.getId(), e);
            }
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("WebSocket transport error for session: " + session.getId(), exception);
        afterConnectionClosed(session, org.springframework.web.socket.CloseStatus.SERVER_ERROR);
    }
    
    private String getTokenFromSession(WebSocketSession session) {
        // Extract JWT token from query parameters
        String query = session.getUri().getQuery();
        if (query != null) {
            String[] params = query.split("&");
            for (String param : params) {
                String[] keyValue = param.split("=", 2);
                if (keyValue.length == 2 && "token".equals(keyValue[0])) {
                    return keyValue[1];
                }
            }
        }
        return null;
    }
    
    /**
     * Start a background thread to continuously read from the Guacamole tunnel
     * and send protocol instructions to the WebSocket client
     */
    private void startTunnelReader(WebSocketSession session, GuacamoleTunnel tunnel) {
        executorService.submit(() -> {
            try {

                GuacamoleReader reader = tunnel.acquireReader();
                StringBuilder buffer = new StringBuilder();
                log.info("Tunnel reader started for session: " + session.getId());
                while (session.isOpen() && !Thread.currentThread().isInterrupted()) {
                    char[] chunk = reader.read();

                    if (chunk == null) {
                        log.info("Tunnel closed for session {}", session.getId());
                        break;
                    }

                    buffer.append(chunk);

                    // Flush all complete instructions (semicolon-terminated)
                    int semicolon;
                    while ((semicolon = buffer.indexOf(";")) != -1) {
                        String instruction = buffer.substring(0, semicolon + 1);
                        buffer.delete(0, semicolon + 1);

                        // Capture PNG/IMG instructions for screenshot analysis
                        if (instruction.startsWith("3.img,") ||
                            instruction.startsWith("4.blob,") ||
                            instruction.startsWith("3.end,")) {
                            screenshotCaptureService.processInstruction(session.getId(), instruction);
                        }


                        // Always log at least the type of instruction (first token)
                        String safeLog = instruction.length() > 200
                            ? instruction.substring(0, 200) + "..."
                            : instruction;
                        log.trace("Sending instruction to WS: {}", safeLog);

                        try {
                            session.sendMessage(new TextMessage(instruction));
                        } catch (IOException e) {
                            log.error("Failed to send instruction to WS", e);
                            session.close();
                            return;
                        }
                    }
                }

                log.info("Exiting tunnel reader for session: {}", session.getId());
                tunnel.releaseReader();

            } catch (GuacamoleException | IOException e) {
                log.error("Error reading from Guacamole tunnel", e);
                try {
                    session.close(org.springframework.web.socket.CloseStatus.SERVER_ERROR);
                } catch (IOException ex) {
                    log.error("Error closing WebSocket session", ex);
                }
            }

            log.info("Tunnel reader exited for session: " + session.getId());
        });
    }
    
    /**
     * Parse a Guacamole protocol instruction to extract opcode and arguments
     * Format: <length>.<opcode>,<length>.<arg1>,<length>.<arg2>,...;
     */
    private GuacamoleInstruction parseGuacamoleInstruction(String instruction) {
        try {
            if (instruction == null || instruction.isEmpty() || !instruction.endsWith(";")) {
                return new GuacamoleInstruction("unknown", new String[0]);
            }
            
            // Remove trailing semicolon
            String content = instruction.substring(0, instruction.length() - 1);
            
            // Split by comma to get elements
            String[] elements = content.split(",");
            if (elements.length == 0) {
                return new GuacamoleInstruction("unknown", new String[0]);
            }
            
            // First element is the opcode (format: <length>.<opcode>)
            String opcodeElement = elements[0];
            int dotIndex = opcodeElement.indexOf('.');
            if (dotIndex == -1) {
                return new GuacamoleInstruction("unknown", new String[0]);
            }
            
            String opcode = opcodeElement.substring(dotIndex + 1);
            
            // Extract arguments
            String[] args = new String[elements.length - 1];
            for (int i = 1; i < elements.length; i++) {
                String element = elements[i];
                int argDotIndex = element.indexOf('.');
                if (argDotIndex != -1) {
                    args[i - 1] = element.substring(argDotIndex + 1);
                } else {
                    args[i - 1] = element;
                }
            }
            
            return new GuacamoleInstruction(opcode, args);
            
        } catch (Exception e) {
            log.error("Error parsing Guacamole instruction: " + instruction, e);
            return new GuacamoleInstruction("unknown", new String[0]);
        }
    }
    
    /**
     * Check if a Guacamole instruction should be analyzed by RdpCommandProcessor
     */
    private boolean shouldAnalyzeInstruction(GuacamoleInstruction instruction) {
        String opcode = instruction.getOpcode();
        // Analyze keyboard and mouse input events
        return "key".equals(opcode) || 
               "mouse".equals(opcode) || 
               "clipboard".equals(opcode) ||
               "file".equals(opcode);
    }
    
    /**
     * Convert Guacamole instruction to RdpInputEvent for analysis
     */
    private RdpCommandProcessor.RdpInputEvent convertToRdpInputEvent(GuacamoleInstruction instruction) {
        try {
            String opcode = instruction.getOpcode();
            String[] args = instruction.getArgs();
            
            switch (opcode) {
                case "key":
                    // Guacamole key instruction: key,<keysym>,<pressed>
                    // args[0] = keysym (integer), args[1] = pressed (0 or 1)
                    if (args.length >= 2) {
                        String keyData = "keysym:" + args[0] + ",pressed:" + args[1];
                        return new RdpCommandProcessor.RdpInputEvent(
                            RdpCommandProcessor.RdpInputEvent.RdpInputType.KEYBOARD,
                            keyData
                        );
                    }
                    break;
                    
                case "mouse":
                    // Guacamole mouse instruction: mouse,<x>,<y>,<mask>
                    // args[0] = x, args[1] = y, args[2] = button mask
                    if (args.length >= 3) {
                        String mouseData = args[0] + ":" + args[1] + ":" + args[2];
                        
                        // Determine if it's a click or move based on button mask
                        int buttonMask = 0;
                        try {
                            buttonMask = Integer.parseInt(args[2]);
                        } catch (NumberFormatException e) {
                            // Ignore
                        }
                        
                        if (buttonMask > 0) {
                            // Button pressed - this is a click
                            return new RdpCommandProcessor.RdpInputEvent(
                                RdpCommandProcessor.RdpInputEvent.RdpInputType.MOUSE_CLICK,
                                mouseData
                            );
                        } else {
                            // No button - this is a move
                            return new RdpCommandProcessor.RdpInputEvent(
                                RdpCommandProcessor.RdpInputEvent.RdpInputType.MOUSE_MOVE,
                                mouseData
                            );
                        }
                    }
                    break;
                    
                case "clipboard":
                    // Clipboard access - treat as special keyboard input
                    String clipboardData = args.length > 0 ? args[0] : "clipboard-access";
                    return new RdpCommandProcessor.RdpInputEvent(
                        RdpCommandProcessor.RdpInputEvent.RdpInputType.KEYBOARD,
                        "clipboard:" + clipboardData
                    );
                    
                case "file":
                    // File transfer - treat as special input
                    String fileData = args.length > 0 ? args[0] : "file-transfer";
                    return new RdpCommandProcessor.RdpInputEvent(
                        RdpCommandProcessor.RdpInputEvent.RdpInputType.KEYBOARD,
                        "file:" + fileData
                    );
            }
            
            return null;
            
        } catch (Exception e) {
            log.error("Error converting Guacamole instruction to RdpInputEvent", e);
            return null;
        }
    }
    
    /**
     * Helper class to represent a parsed Guacamole instruction
     */
    private static class GuacamoleInstruction {
        private final String opcode;
        private final String[] args;
        
        public GuacamoleInstruction(String opcode, String[] args) {
            this.opcode = opcode;
            this.args = args;
        }
        
        public String getOpcode() {
            return opcode;
        }
        
        public String[] getArgs() {
            return args;
        }
    }
    
    /**
     * Create a minimal ConnectedSystem from JWT token for RDP session tracking
     * This is a placeholder until full session tracking is integrated
     */
    private ConnectedSystem createConnectedSystemFromJwt(String jwtToken, String sessionId) {
        try {
            // Extract claims from JWT
            var claims = asymmetricJwtService.extractClaims(jwtToken);
            String subject = claims.getSubject();
            String target = claims.get("target", String.class);
            
            // Create minimal session log using constructor
            io.sentrius.sso.core.model.sessions.SessionLog sessionLog = 
                new io.sentrius.sso.core.model.sessions.SessionLog();
            sessionLog.setId(System.currentTimeMillis()); // Use timestamp as temporary ID
            sessionLog.setSessionTm(new java.sql.Timestamp(System.currentTimeMillis()));
            sessionLog.setClosed(false);
            sessionLog.setUsername(subject != null ? subject : "rdp-user");
            
            // Create minimal user
            io.sentrius.sso.core.model.users.User user = 
                io.sentrius.sso.core.model.users.User.builder()
                    .id(1L) // Placeholder ID
                    .username(subject != null ? subject : "rdp-user")
                    .build();
            
            // Create minimal host system
            io.sentrius.sso.core.model.HostSystem hostSystem =
                hostGroupService.getHostSystem(Long.valueOf(target)).orElseThrow();

            var hostGroup = hostGroupService.getHostGroup(hostSystem.getId());

            // Build ConnectedSystem
            return ConnectedSystem.builder()
                .session(sessionLog)
                .user(user)
                .hostSystem(hostSystem)
                .enclave(hostGroup)
                .websocketSessionId(sessionId)
                .build();
                
        } catch (Exception e) {
            log.error("Error creating ConnectedSystem from JWT", e);
            // Return a minimal default ConnectedSystem
            io.sentrius.sso.core.model.sessions.SessionLog sessionLog = 
                new io.sentrius.sso.core.model.sessions.SessionLog();
            sessionLog.setId(System.currentTimeMillis());
            sessionLog.setSessionTm(new java.sql.Timestamp(System.currentTimeMillis()));
            sessionLog.setClosed(false);
            sessionLog.setUsername("rdp-user");
            
            return ConnectedSystem.builder()
                .session(sessionLog)
                .user(io.sentrius.sso.core.model.users.User.builder()
                    .id(1L)
                    .username("rdp-user")
                    .build())
                .hostSystem(io.sentrius.sso.core.model.HostSystem.builder()
                    .id(1L)
                    .host("rdp-server")
                    .build())
                .websocketSessionId(sessionId)
                .build();
        }
    }
    
    /**
     * Initialize session rules (like DeletePrevention, SudoPrevention) for command evaluation
     * Uses @Transactional to ensure database session is active for lazy-loaded collections
     */
    @Transactional(readOnly = true)
    public void initializeSessionRules(ConnectedSystem connectedSystem) {
        try {
            User user = connectedSystem.getUser();
            HostGroup hostGroup = connectedSystem.getEnclave();
            Long sessionId = connectedSystem.getSession().getId();
            
            // If we don't have a proper User, try to fetch from username
            if (user != null && user.getId() != null && user.getId() == 1L) {
                // This is a placeholder user, try to fetch the real one
                String username = connectedSystem.getSession().getUsername();
                if (username != null && !username.equals("rdp-user")) {
                    User realUser = userService.getUserByUsername(username);
                    if (realUser != null) {
                        user = realUser;
                        connectedSystem.setUser(user);
                    }
                }
            }

            
            // Initialize rules if we have a host group
            if (hostGroup != null && hostGroup.getRules() != null) {
                log.info("Initializing session rules for Guacamole RDP session {} {}", sessionId, hostGroup.getId());
                Set<ProfileRule> rules = hostGroup.getRules();
                
                // Get pluggable services for rule initialization
                Map<String, PluggableServices> servicesMap = applicationContext.getBeansOfType(PluggableServices.class);
                Map<String, PluggableServices> services = servicesMap.entrySet().stream()
                    .filter(entry -> entry.getValue().isEnabled())
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
                
                // Initialize rules using RuleFactory
                List<AccessTokenEvaluator> synchronousRules = new ArrayList<>();
                List<SessionTokenEvaluator> sessionTokenRules = new ArrayList<>();

                RuleFactory.createRules(systemOptions, connectedSystem, sessionTrackingService,
                    rules.stream().map(pr -> {
                        var rule = new io.sentrius.sso.core.model.auditing.Rule();
                        rule.setRuleClass(pr.getRuleClass());
                        rule.setRuleConfig(pr.getRuleConfig());
                        return rule;
                    }).collect(Collectors.toList()),
                    synchronousRules, sessionTokenRules, services);

                log.info("Initialized {} synchronous rules and {} session token rules for Guacamole RDP session {}", synchronousRules.size(), sessionTokenRules.size(), sessionId);
                
                // Register rules with RdpCommandProcessor for keyboard input evaluation
                // Pass both synchronous rules and startup actions (session token rules)
                rdpCommandProcessor.registerSessionRules(sessionId, synchronousRules, sessionTokenRules, connectedSystem);
                
                log.info("Initialized {} rules for Guacamole RDP session {}", synchronousRules.size(), sessionId);
            } else {
                log.warn("No host group or rules found for Guacamole RDP session {}", sessionId);
            }
            
        } catch (Exception e) {
            log.error("Error initializing session rules for Guacamole WebSocket session", e);
        }
    }
}