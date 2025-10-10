package io.sentrius.sso.rdpproxy.service;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.sentrius.sso.automation.auditing.AccessTokenEvaluator;
import io.sentrius.sso.automation.auditing.RuleFactory;
import io.sentrius.sso.automation.auditing.SessionTokenEvaluator;
import io.sentrius.sso.core.config.SystemOptions;
import io.sentrius.sso.core.model.ConnectedSystem;
import io.sentrius.sso.core.model.hostgroup.HostGroup;
import io.sentrius.sso.core.model.hostgroup.ProfileConfiguration;
import io.sentrius.sso.core.model.hostgroup.ProfileRule;
import io.sentrius.sso.core.model.users.User;
import io.sentrius.sso.core.services.HostGroupService;
import io.sentrius.sso.core.services.PluggableServices;
import io.sentrius.sso.core.services.UserService;
import io.sentrius.sso.core.services.agents.AgentService;
import io.sentrius.sso.core.services.terminal.SessionTrackingService;
import io.sentrius.sso.rdpproxy.config.RdpProxyConfig;
import io.sentrius.sso.rdpproxy.service.RdpJwtAuthenticationService;
import io.sentrius.sso.rdpproxy.streams.RdpSessionRoute;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

/**
 * Manages RDP connections and applies Sentrius monitoring and rules.
 * This is a complete turnkey service that integrates with agents and rule evaluation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RdpConnectionManager {

    private final RdpProxyConfig config;
    private final UserService userService;
    private final HostGroupService hostGroupService;
    private final SessionTrackingService sessionTrackingService;
    private final SystemOptions systemOptions;
    private final ApplicationContext applicationContext;
    private final AgentService agentService;
    private final RdpCommandProcessor rdpCommandProcessor;
    private final RdpTerminalResponseService terminalResponseService;
    private final RdpJwtAuthenticationService jwtAuthenticationService;
    private final RdpScreenshotCaptureService screenshotCaptureService;
    
    private final ConcurrentMap<String, ConnectedSystem> activeConnections = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, List<AccessTokenEvaluator>> sessionRules = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, RdpJwtAuthenticationService.RdpAuthenticationResult> sessionJwtResults = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, RdpJwtAuthenticationService.RdpTargetInfo> sessionTargetInfo = new ConcurrentHashMap<>();

    /**
     * Process incoming RDP data and apply Sentrius monitoring with full rule evaluation
     */
    public void processRdpData(RdpSessionRoute sessionRoute, ByteBuf data, ChannelHandlerContext ctx) {
        try {
            String sessionId = ctx.channel().id().asShortText();
            ConnectedSystem connectedSystem = activeConnections.get(sessionId);
            
            if (connectedSystem == null) {
                log.warn("No connected system found for RDP session: {}", sessionId);
                ctx.close();
                return;
            }

            // Extract RDP protocol information
            RdpPacketInfo packetInfo = parseRdpPacket(data);
            
            if (packetInfo.isConnectionRequest()) {
                handleConnectionRequest(sessionRoute, packetInfo, ctx);
            } else if (packetInfo.isAuthenticationData()) {
                handleAuthentication(sessionRoute, packetInfo, ctx);
            } else {
                // Regular RDP data - apply full monitoring rules with agent integration
                handleRdpDataWithRules(connectedSystem, packetInfo, ctx);
            }
            
        } catch (Exception e) {
            log.error("Error processing RDP data", e);
            ctx.close();
        }
    }

    /**
     * Handle RDP connection request with rule initialization
     */
    private void handleConnectionRequest(RdpSessionRoute sessionRoute, RdpPacketInfo packetInfo, ChannelHandlerContext ctx) {
        log.info("Handling RDP connection request for session: {}", ctx.channel().id().asShortText());
        
        try {
            // Initialize session with default rules
            String sessionId = ctx.channel().id().asShortText();
            
            // For now, use a default user and host group - in a real implementation,
            // this would be extracted from the RDP connection negotiation
            initializeSessionRules(sessionId, getDefaultUser(), getDefaultHostGroup());
            
            log.debug("RDP connection request processed and rules initialized");
            
        } catch (Exception e) {
            log.error("Error handling RDP connection request", e);
            ctx.close();
        }
    }

    /**
     * Handle RDP authentication with JWT validation and target resolution
     */
    private void handleAuthentication(RdpSessionRoute sessionRoute, RdpPacketInfo packetInfo, ChannelHandlerContext ctx) {
        String sessionId = ctx.channel().id().asShortText();
        log.info("Handling RDP authentication for session: {}", sessionId);
        
        try {
            // Extract credentials from RDP authentication data
            String username = extractUsernameFromRdpAuth(packetInfo);
            String password = extractPasswordFromRdpAuth(packetInfo);
            
            if (username == null) {
                log.warn("Could not extract username from RDP authentication data for session: {}", sessionId);
                ctx.close();
                return;
            }
            
            // First priority: Check if password contains JWT token (user's preference)
            Optional<String> jwtTokenOpt = Optional.empty();
            if (password != null) {
                jwtTokenOpt = jwtAuthenticationService.extractJwtFromPassword(password);
                if (jwtTokenOpt.isPresent()) {
                    log.debug("Found JWT token in password field for session: {}", sessionId);
                }
            }
            
            // Fallback: Check if username contains JWT token (legacy support)
            if (jwtTokenOpt.isEmpty()) {
                jwtTokenOpt = jwtAuthenticationService.extractJwtFromUsername(username);
                if (jwtTokenOpt.isPresent()) {
                    log.debug("Found JWT token in username field for session: {}", sessionId);
                }
            }
            
            if (jwtTokenOpt.isEmpty()) {
                log.info("Neither password nor username contains JWT token for user '{}', falling back to traditional authentication", 
                    username.length() > 20 ? username.substring(0, 20) + "..." : username);
                
                // Fall back to traditional Sentrius user authentication
                handleTraditionalAuthentication(sessionRoute, username, sessionId, ctx);
                return;
            }

            // JWT-based authentication flow
            String jwtToken = jwtTokenOpt.get();
            log.info("Processing JWT authentication for session: {} (token source: {})", 
                sessionId, password != null && jwtAuthenticationService.extractJwtFromPassword(password).isPresent() ? "password" : "username");
            
            // Validate JWT token
            Optional<RdpJwtAuthenticationService.RdpAuthenticationResult> authResultOpt = 
                jwtAuthenticationService.validateJwt(jwtToken);
            
            if (authResultOpt.isEmpty()) {
                log.warn("JWT validation failed for session: {}", sessionId);
                logSessionMetadata(sessionId, null, null, ctx.channel().remoteAddress().toString(), null, false);
                ctx.close();
                return;
            }
            
            RdpJwtAuthenticationService.RdpAuthenticationResult authResult = authResultOpt.get();
            String subject = authResult.getSubject();
            String target = authResult.getTarget();
            String jti = authResult.getJti();
            
            // Resolve target to backend host
            Optional<RdpJwtAuthenticationService.RdpTargetInfo> targetInfoOpt = 
                jwtAuthenticationService.resolveTarget(target);
            
            if (targetInfoOpt.isEmpty()) {
                log.warn("Cannot resolve target '{}' for session: {}", target, sessionId);
                logSessionMetadata(sessionId, subject, target, ctx.channel().remoteAddress().toString(), jti, false);
                ctx.close();
                return;
            }
            
            RdpJwtAuthenticationService.RdpTargetInfo targetInfo = targetInfoOpt.get();
            
            // Check if target is authorized for the user
            if (!jwtAuthenticationService.isTargetAuthorized(target, subject)) {
                log.warn("Target '{}' not authorized for user '{}' in session: {}", target, subject, sessionId);
                logSessionMetadata(sessionId, subject, target, ctx.channel().remoteAddress().toString(), jti, false);
                ctx.close();
                return;
            }
            
            // Store session information
            sessionJwtResults.put(sessionId, authResult);
            sessionTargetInfo.put(sessionId, targetInfo);
            
            // Create or get Sentrius user for monitoring (optional, create minimal user if needed)
            User sentriusUser = getOrCreateUserForJwtSubject(subject);
            
            // Get or create a default host group for JWT-based connections
            HostGroup defaultHostGroup = getOrCreateJwtHostGroup();
            
            // Create ConnectedSystem for session tracking
            ConnectedSystem connectedSystem = ConnectedSystem.builder()
                .user(sentriusUser)
                .enclave(defaultHostGroup)
                .build();
            
            activeConnections.put(sessionId, connectedSystem);
            
            // Initialize session rules if needed (can be minimal for JWT-based connections)
            initializeJwtSessionRules(sessionId, sentriusUser, defaultHostGroup, targetInfo);
            
            // Notify agents of new RDP session
            notifyAgentsOfJwtRdpSession(sentriusUser, target, targetInfo, connectedSystem, authResult);
            
            // Start screenshot capture for this session
            screenshotCaptureService.startCapture(sessionId, connectedSystem);
            
            // Log successful authentication
            logSessionMetadata(sessionId, subject, target, ctx.channel().remoteAddress().toString(), jti, true);
            
            log.info("JWT RDP authentication successful for session: {}, user: {}, target: {}:{}", 
                sessionId, subject, targetInfo.getHost(), targetInfo.getPort());
                
        } catch (Exception e) {
            log.error("Error handling JWT RDP authentication for session: " + sessionId, e);
            ctx.close();
        }
    }

    /**
     * Handle RDP data with full rule evaluation and agent integration
     */
    private void handleRdpDataWithRules(ConnectedSystem connectedSystem, RdpPacketInfo packetInfo, ChannelHandlerContext ctx) {
        String sessionId = ctx.channel().id().asShortText();
        log.debug("Processing RDP data with full rules for session: {}", sessionId);
        
        try {
            // Extract RDP actions and input events from packet data
            List<RdpCommandProcessor.RdpAction> actions = extractRdpActions(packetInfo);
            List<RdpCommandProcessor.RdpInputEvent> inputEvents = extractInputEvents(packetInfo);
            
            ByteArrayOutputStream rdpOutput = new ByteArrayOutputStream();
            List<AccessTokenEvaluator> rules = sessionRules.get(sessionId);
            
            // Process input events for behavioral analysis
            for (RdpCommandProcessor.RdpInputEvent inputEvent : inputEvents) {
                boolean inputAllowed = rdpCommandProcessor.processInputEvent(connectedSystem, inputEvent, rdpOutput);
                
                if (!inputAllowed) {
                    log.warn("RDP input event blocked for session {}: {}", sessionId, inputEvent.getType());
                    // Continue processing other events even if one is blocked
                }
            }
            
            // Process RDP actions with rule evaluation
            for (RdpCommandProcessor.RdpAction action : actions) {
                // Apply session rules to each action
                boolean allowed = true;
                
                if (rules != null) {
                    for (AccessTokenEvaluator rule : rules) {
                        try {
                            // Convert RDP action to string format for rule evaluation
                            String actionString = String.format("%s:%s", action.getType(), action.getTarget());
                            var triggerResult = rule.trigger(actionString);
                            
                            if (triggerResult.isPresent()) {
                                switch (triggerResult.get().getAction()) {
                                    case DENY_ACTION:
                                        allowed = false;
                                        log.warn("RDP action denied by rule {}: {}", rule.getClass().getSimpleName(), action.getDescription());
                                        break;
                                    case WARN_ACTION:
                                        log.warn("RDP action warning from rule {}: {}", rule.getClass().getSimpleName(), action.getDescription());
                                        break;
                                    case RECORD_ACTION:
                                        log.info("RDP action recorded by rule {}: {}", rule.getClass().getSimpleName(), action.getDescription());
                                        break;
                                }
                            }
                        } catch (Exception e) {
                            log.error("Error applying rule {} to RDP action", rule.getClass().getSimpleName(), e);
                        }
                    }
                }
                
                // Process action through command processor
                if (allowed) {
                    allowed = rdpCommandProcessor.processRdpAction(connectedSystem, action, rdpOutput);
                }
                
                // Log action result
                log.info("RDP action {} for session {}: {}", 
                    allowed ? "ALLOWED" : "BLOCKED", sessionId, action.getDescription());
                
                // Send notifications for specific action types
                sendActionNotifications(action, allowed, rdpOutput);
            }
            
            // Send any buffered responses back through the channel
            if (rdpOutput.size() > 0) {
                ctx.writeAndFlush(ctx.alloc().buffer().writeBytes(rdpOutput.toByteArray()));
            }
            
        } catch (Exception e) {
            log.error("Error processing RDP data with rules", e);
        }
    }

    /**
     * Initialize session rules for an RDP connection
     */
    private void initializeSessionRules(String sessionId, User user, HostGroup hostGroup) {
        try {
            ProfileConfiguration config = hostGroup.getConfiguration();
            if (config == null) {
                log.warn("No configuration found for host group: {}", hostGroup.getName());
                return;
            }
            
            Set<ProfileRule> rules = hostGroup.getRules();
            
            // Get pluggable services for rule initialization
            Map<String, PluggableServices> servicesMap = applicationContext.getBeansOfType(PluggableServices.class);
            Map<String, PluggableServices> services = servicesMap.entrySet().stream()
                .filter(entry -> entry.getValue().isEnabled())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            
            // Create ConnectedSystem for rule initialization
            ConnectedSystem connectedSystem = activeConnections.get(sessionId);
            if (connectedSystem == null) {
                connectedSystem = ConnectedSystem.builder()
                    .user(user)
                    .enclave(hostGroup)
                    .build();
                activeConnections.put(sessionId, connectedSystem);
            }
            
            // Initialize rules using RuleFactory
            List<AccessTokenEvaluator> synchronousRules = new ArrayList<>();
            List<SessionTokenEvaluator> sessionTokenRules = new ArrayList<>();
            
            RuleFactory.createRules(
                systemOptions,
                connectedSystem,
                sessionTrackingService,
                rules.stream().map(pr -> {
                    var rule = new io.sentrius.sso.core.model.auditing.Rule();
                    rule.setRuleClass(pr.getRuleClass());
                    rule.setRuleConfig(pr.getRuleConfig());
                    return rule;
                }).collect(Collectors.toList()),
                synchronousRules,
                sessionTokenRules,
                services
            );
            
            sessionRules.put(sessionId, synchronousRules);
            
            log.info("Initialized {} rules for RDP session {}", synchronousRules.size(), sessionId);
            
        } catch (Exception e) {
            log.error("Error initializing session rules for RDP session: " + sessionId, e);
        }
    }

    /**
     * Notify agents of new RDP session
     */
    private void notifyAgentsOfRdpSession(User user, HostGroup hostGroup, ConnectedSystem connectedSystem) {
        try {
            // This would integrate with the agent system to notify agents of the new RDP session
            log.info("Notifying agents of new RDP session for user: {} in host group: {}", 
                user.getUsername(), hostGroup.getName());
            
            // Notify agents of RDP session start using communication system
            String sessionPayload = String.format(
                "{\"type\":\"rdp_session_start\",\"user\":\"%s\",\"hostGroup\":\"%s\",\"sessionId\":\"%s\",\"timestamp\":\"%d\"}", 
                user.getUsername(), 
                hostGroup.getName(),
                connectedSystem.getSession() != null ? connectedSystem.getSession().getId() : "unknown",
                System.currentTimeMillis()
            );
            
            agentService.saveCommunication(
                java.util.UUID.randomUUID().toString(),
                "rdp-proxy", 
                "analytics-agent", 
                "rdp_session_notification", 
                sessionPayload
            );
            
        } catch (Exception e) {
            log.error("Error notifying agents of RDP session", e);
        }
    }

    /**
     * Send notifications for specific RDP actions
     */
    private void sendActionNotifications(RdpCommandProcessor.RdpAction action, boolean allowed, ByteArrayOutputStream rdpOutput) {
        try {
            switch (action.getType()) {
                case FILE_COPY_IN:
                case FILE_COPY_OUT:
                    terminalResponseService.sendFileTransferNotification(
                        action.getTarget(), action.getType().toString(), allowed, rdpOutput);
                    break;
                case CLIPBOARD_ACCESS:
                    terminalResponseService.sendClipboardNotification(
                        action.getTarget(), allowed, rdpOutput);
                    break;
                case DRIVE_REDIRECT:
                    terminalResponseService.sendDriveRedirectionNotification(
                        action.getTarget(), allowed, rdpOutput);
                    break;
                default:
                    // No specific notification for other action types
                    break;
            }
        } catch (Exception e) {
            log.error("Error sending action notification", e);
        }
    }

    /**
     * Extract RDP input events from packet data for behavioral analysis
     */
    private List<RdpCommandProcessor.RdpInputEvent> extractInputEvents(RdpPacketInfo packetInfo) {
        List<RdpCommandProcessor.RdpInputEvent> inputEvents = new ArrayList<>();
        
        // This is a simplified implementation - in a real RDP proxy,
        // this would parse actual RDP protocol packets to extract input events
        
        if (packetInfo.getData() != null && packetInfo.getData().length > 0) {
            byte[] data = packetInfo.getData();
            
            // Heuristic detection of different input event types
            
            // Check for keyboard input patterns
            if (containsPattern(data, "KEY") || containsKeyboardSignature(data)) {
                String keyData = extractKeyboardData(data);
                inputEvents.add(new RdpCommandProcessor.RdpInputEvent(
                    RdpCommandProcessor.RdpInputEvent.RdpInputType.KEYBOARD,
                    keyData
                ));
            }
            
            // Check for mouse click patterns
            if (containsPattern(data, "CLICK") || containsMouseClickSignature(data)) {
                String clickData = extractMouseClickData(data);
                inputEvents.add(new RdpCommandProcessor.RdpInputEvent(
                    RdpCommandProcessor.RdpInputEvent.RdpInputType.MOUSE_CLICK,
                    clickData
                ));
            }
            
            // Check for mouse movement patterns
            if (containsPattern(data, "MOVE") || containsMouseMoveSignature(data)) {
                String moveData = extractMouseMoveData(data);
                inputEvents.add(new RdpCommandProcessor.RdpInputEvent(
                    RdpCommandProcessor.RdpInputEvent.RdpInputType.MOUSE_MOVE,
                    moveData
                ));
            }
            
            // Check for scroll patterns
            if (containsPattern(data, "SCROLL") || containsScrollSignature(data)) {
                String scrollData = extractScrollData(data);
                inputEvents.add(new RdpCommandProcessor.RdpInputEvent(
                    RdpCommandProcessor.RdpInputEvent.RdpInputType.SCROLL,
                    scrollData
                ));
            }
        }
        
        return inputEvents;
    }
    
    // Helper methods for input event extraction
    
    private boolean containsKeyboardSignature(byte[] data) {
        // Look for RDP keyboard event signatures (simplified)
        return data.length > 4 && data[0] == 0x04; // Typical RDP input event marker
    }
    
    private boolean containsMouseClickSignature(byte[] data) {
        // Look for RDP mouse click signatures (simplified)
        return data.length > 6 && data[1] == 0x01; // Mouse button event marker
    }
    
    private boolean containsMouseMoveSignature(byte[] data) {
        // Look for RDP mouse movement signatures (simplified)
        return data.length > 8 && data[1] == 0x02; // Mouse movement marker
    }
    
    private boolean containsScrollSignature(byte[] data) {
        // Look for RDP scroll event signatures (simplified)
        return data.length > 6 && data[1] == 0x03; // Scroll event marker
    }
    
    private String extractKeyboardData(byte[] data) {
        // Extract keyboard data from RDP packet (simplified)
        try {
            // This would parse actual RDP keyboard event structures
            StringBuilder keyData = new StringBuilder();
            
            // Look for printable characters in the data
            for (int i = 4; i < data.length && i < 20; i++) {
                if (data[i] >= 32 && data[i] < 127) {
                    keyData.append((char) data[i]);
                } else if (data[i] == 13) {
                    keyData.append("\\n");
                } else if (data[i] == 9) {
                    keyData.append("\\t");
                }
            }
            
            return keyData.length() > 0 ? keyData.toString() : "key_event";
            
        } catch (Exception e) {
            return "unknown_key";
        }
    }
    
    private String extractMouseClickData(byte[] data) {
        // Extract mouse click data from RDP packet (simplified)
        try {
            // Parse coordinates and button info from packet
            int x = data.length > 6 ? ((data[4] & 0xFF) << 8) | (data[5] & 0xFF) : 0;
            int y = data.length > 8 ? ((data[6] & 0xFF) << 8) | (data[7] & 0xFF) : 0;
            String button = data.length > 3 && (data[3] & 0x01) != 0 ? "left" : "right";
            
            return String.format("%s:%d:%d", button, x, y);
            
        } catch (Exception e) {
            return "left:0:0";
        }
    }
    
    private String extractMouseMoveData(byte[] data) {
        // Extract mouse movement data from RDP packet (simplified)
        try {
            int x = data.length > 6 ? ((data[4] & 0xFF) << 8) | (data[5] & 0xFF) : 0;
            int y = data.length > 8 ? ((data[6] & 0xFF) << 8) | (data[7] & 0xFF) : 0;
            
            return String.format("%d:%d", x, y);
            
        } catch (Exception e) {
            return "0:0";
        }
    }
    
    private String extractScrollData(byte[] data) {
        // Extract scroll data from RDP packet (simplified)
        try {
            int delta = data.length > 4 ? (data[4] & 0xFF) : 0;
            if (delta > 127) delta -= 256; // Convert to signed
            
            return String.valueOf(delta);
            
        } catch (Exception e) {
            return "0";
        }
    }
    private List<RdpCommandProcessor.RdpAction> extractRdpActions(RdpPacketInfo packetInfo) {
        List<RdpCommandProcessor.RdpAction> actions = new ArrayList<>();
        
        // This is a simplified implementation - in a real RDP proxy,
        // this would parse actual RDP protocol packets to extract actions
        
        // For demonstration, create sample actions based on packet characteristics
        if (packetInfo.getData() != null && packetInfo.getData().length > 0) {
            // Simple heuristics to detect RDP actions
            byte[] data = packetInfo.getData();
            
            // Check for file transfer patterns
            if (containsPattern(data, "CLIPRDR")) {
                actions.add(new RdpCommandProcessor.RdpAction(
                    RdpCommandProcessor.RdpAction.RdpActionType.CLIPBOARD_ACCESS,
                    "clipboard", "Clipboard access detected"));
            }
            
            // Check for drive redirection patterns
            if (containsPattern(data, "RDPDR")) {
                actions.add(new RdpCommandProcessor.RdpAction(
                    RdpCommandProcessor.RdpAction.RdpActionType.DRIVE_REDIRECT,
                    "C:", "Drive redirection detected"));
            }
            
            // Check for general data transfer
            if (data.length > 1024) {
                actions.add(new RdpCommandProcessor.RdpAction(
                    RdpCommandProcessor.RdpAction.RdpActionType.SCREEN_CAPTURE,
                    "display", "Large data transfer - possible screen update"));
            }
        }
        
        return actions;
    }

    /**
     * Clean up session resources
     */
    public void cleanupSession(String sessionId) {
        log.info("Cleaning up RDP session resources: {}", sessionId);
        
        // Stop screenshot capture
        screenshotCaptureService.stopCapture(sessionId);
        
        ConnectedSystem connectedSystem = activeConnections.remove(sessionId);
        sessionRules.remove(sessionId);
        RdpJwtAuthenticationService.RdpAuthenticationResult jwtResult = sessionJwtResults.remove(sessionId);
        RdpJwtAuthenticationService.RdpTargetInfo targetInfo = sessionTargetInfo.remove(sessionId);
        
        if (connectedSystem != null) {
            // Notify session tracking service
            sessionTrackingService.removeUserSession(connectedSystem);
            
            // Notify agents of session end
            try {
                String userIdentifier = connectedSystem.getUser().getUsername();
                String targetIdentifier = targetInfo != null ? targetInfo.getHost() + ":" + targetInfo.getPort() : "unknown";
                
                log.info("Notifying agents of RDP session end for user: {} to target: {}", 
                    userIdentifier, targetIdentifier);
                
                // Create session end payload
                String sessionEndPayload = String.format(
                    "{\"type\":\"rdp_session_end\",\"user\":\"%s\",\"target\":\"%s\",\"sessionId\":\"%s\",\"jti\":\"%s\",\"timestamp\":\"%d\"}", 
                    userIdentifier,
                    targetIdentifier,
                    connectedSystem.getSession() != null ? connectedSystem.getSession().getId() : sessionId,
                    jwtResult != null ? jwtResult.getJti() : "none",
                    System.currentTimeMillis()
                );
                
                agentService.saveCommunication(
                    java.util.UUID.randomUUID().toString(),
                    "rdp-proxy", 
                    "analytics-agent", 
                    "rdp_session_end_notification", 
                    sessionEndPayload
                );
                
            } catch (Exception e) {
                log.error("Error notifying agents of session end", e);
            }
        }
    }
    
    /**
     * Handle traditional (non-JWT) authentication flow
     */
    private void handleTraditionalAuthentication(RdpSessionRoute sessionRoute, String username, String sessionId, ChannelHandlerContext ctx) {
        try {
            // Validate user through Sentrius
            var userOpt = userService.findByUsername(username);
            if (userOpt.isPresent()) {
                User user = userOpt.get();
                log.info("User {} authenticated for RDP session via traditional auth", username);
                
                // Get user's host groups and initialize rules
                List<HostGroup> userHostGroups = hostGroupService.getHostGroupsForUser(user.getId());
                if (!userHostGroups.isEmpty()) {
                    HostGroup hostGroup = userHostGroups.get(0); // Use first host group
                    
                    // Create ConnectedSystem for this session
                    ConnectedSystem connectedSystem = ConnectedSystem.builder()
                        .user(user)
                        .enclave(hostGroup)
                        .build();
                    
                    activeConnections.put(sessionId, connectedSystem);
                    
                    // Initialize rules with user's host group configuration
                    initializeSessionRules(sessionId, user, hostGroup);
                    
                    // Notify agents of new RDP session
                    notifyAgentsOfRdpSession(user, hostGroup, connectedSystem);
                    
                    // Start screenshot capture for this session
                    screenshotCaptureService.startCapture(sessionId, connectedSystem);
                    
                    log.info("Traditional RDP session {} fully initialized with rules and agent integration", sessionId);
                } else {
                    log.warn("User {} has no host groups configured", username);
                    ctx.close();
                }
            } else {
                log.warn("User {} not found in Sentrius system", username);
                ctx.close();
            }
        } catch (Exception e) {
            log.error("Error in traditional RDP authentication for session: " + sessionId, e);
            ctx.close();
        }
    }
    
    /**
     * Get or create a Sentrius user for JWT subject
     */
    private User getOrCreateUserForJwtSubject(String subject) {
        // Try to find existing user by username (subject)
        Optional<User> existingUser = userService.findByUsername(subject);
        
        if (existingUser.isPresent()) {
            return existingUser.get();
        }
        
        // For JWT-based authentication, we might create a minimal user or use a default
        // In a production system, this would be configurable
        log.info("Creating minimal user entry for JWT subject: {}", subject);
        
        // Return a default admin user or create a minimal JWT user
        // This implementation depends on your user management requirements
        return userService.findByUsername("admin").orElse(getDefaultUser());
    }
    
    /**
     * Get or create a default host group for JWT-based connections
     */
    private HostGroup getOrCreateJwtHostGroup() {
        // Return a default host group for JWT-based connections
        List<HostGroup> groups = hostGroupService.getAllHostGroups();
        
        // Look for a JWT-specific host group or use the first available
        HostGroup jwtHostGroup = groups.stream()
            .filter(group -> "jwt-rdp".equals(group.getName()) || "default".equals(group.getName()))
            .findFirst()
            .orElse(groups.isEmpty() ? null : groups.get(0));
            
        if (jwtHostGroup == null) {
            log.warn("No suitable host group found for JWT RDP connections");
        }
        
        return jwtHostGroup;
    }
    
    /**
     * Initialize session rules for JWT-based connections
     */
    private void initializeJwtSessionRules(String sessionId, User user, HostGroup hostGroup, 
                                         RdpJwtAuthenticationService.RdpTargetInfo targetInfo) {
        try {
            // For JWT-based connections, we might use minimal rules or target-specific rules
            if (hostGroup != null) {
                // Use existing rule initialization but potentially with different rule set
                initializeSessionRules(sessionId, user, hostGroup);
            } else {
                // Initialize with minimal JWT-specific rules
                List<AccessTokenEvaluator> jwtRules = new ArrayList<>();
                
                // Add basic security rules for JWT connections
                // This would be expanded based on requirements
                
                sessionRules.put(sessionId, jwtRules);
                log.info("Initialized minimal JWT rules for RDP session {}", sessionId);
            }
            
            // Apply target-specific restrictions
            applyTargetSpecificRestrictions(sessionId, targetInfo);
            
        } catch (Exception e) {
            log.error("Error initializing JWT session rules for session: " + sessionId, e);
        }
    }
    
    /**
     * Apply target-specific security restrictions
     */
    private void applyTargetSpecificRestrictions(String sessionId, RdpJwtAuthenticationService.RdpTargetInfo targetInfo) {
        // Apply restrictions based on target configuration
        if (!targetInfo.isRedirectionAllowed()) {
            log.info("Applying clipboard/drive redirection restrictions for session: {}", sessionId);
            // This would integrate with the RDP protocol handling to block specific channels
        }
    }
    
    /**
     * Notify agents of JWT-based RDP session
     */
    private void notifyAgentsOfJwtRdpSession(User user, String target, 
                                           RdpJwtAuthenticationService.RdpTargetInfo targetInfo,
                                           ConnectedSystem connectedSystem,
                                           RdpJwtAuthenticationService.RdpAuthenticationResult authResult) {
        try {
            log.info("Notifying agents of JWT RDP session for user: {} to target: {}:{}", 
                authResult.getSubject(), targetInfo.getHost(), targetInfo.getPort());
            
            // Create detailed session payload for JWT-based connection
            String sessionPayload = String.format(
                "{\"type\":\"jwt_rdp_session_start\",\"subject\":\"%s\",\"target\":\"%s\",\"targetHost\":\"%s\",\"targetPort\":%d,\"jti\":\"%s\",\"sessionId\":\"%s\",\"timestamp\":\"%d\"}", 
                authResult.getSubject(),
                target,
                targetInfo.getHost(),
                targetInfo.getPort(),
                authResult.getJti(),
                connectedSystem.getSession() != null ? connectedSystem.getSession().getId() : "unknown",
                System.currentTimeMillis()
            );
            
            agentService.saveCommunication(
                java.util.UUID.randomUUID().toString(),
                "rdp-proxy", 
                "analytics-agent", 
                "jwt_rdp_session_notification", 
                sessionPayload
            );
            
        } catch (Exception e) {
            log.error("Error notifying agents of JWT RDP session", e);
        }
    }
    
    /**
     * Log session metadata as required by security requirements
     */
    private void logSessionMetadata(String sessionId, String subject, String target, String clientIP, String jti, boolean success) {
        try {
            // Always log session metadata (never log raw JWT values)
            String logMessage = String.format(
                "RDP_SESSION_AUTH: sessionId=%s, subject=%s, target=%s, clientIP=%s, jti=%s, success=%s, timestamp=%d",
                sessionId != null ? sessionId : "unknown",
                subject != null ? subject : "unknown", 
                target != null ? target : "unknown",
                clientIP != null ? clientIP.replaceAll("[^\\w.:-]", "") : "unknown", // Basic sanitization
                jti != null ? jti : "unknown",
                success,
                System.currentTimeMillis()
            );
            
            if (success) {
                log.info(logMessage);
            } else {
                log.warn(logMessage);
            }
            
        } catch (Exception e) {
            log.error("Error logging session metadata", e);
        }
    }

    // Helper methods (keeping existing implementations for compatibility)
    
    private RdpPacketInfo parseRdpPacket(ByteBuf data) {
        RdpPacketInfo info = new RdpPacketInfo();
        
        if (data.readableBytes() >= 4) {
            // Copy data for processing
            byte[] packetData = new byte[data.readableBytes()];
            data.getBytes(0, packetData);
            info.setData(packetData);
            
            // Basic RDP packet type detection
            byte[] header = new byte[4];
            data.getBytes(0, header);
            
            if (header[3] == (byte) 0xE0) {
                info.setConnectionRequest(true);
            } else if (containsAuthenticationPattern(data)) {
                info.setAuthenticationData(true);
                // Extract authentication credentials from RDP packet
                parseAuthenticationData(packetData, info);
            }
        }
        
        return info;
    }
    
    /**
     * Parse RDP authentication data to extract username, password, and domain
     * This is a simplified implementation of RDP authentication packet parsing
     */
    private void parseAuthenticationData(byte[] data, RdpPacketInfo info) {
        try {
            // RDP authentication packets typically contain credential information
            // in a specific format. This is a simplified parser that looks for
            // common patterns in RDP authentication packets.
            
            // Look for TS_SECURITY_PACKET or similar structures
            // RDP uses various authentication methods (NLA, Basic, etc.)
            
            String username = null;
            String password = null;
            String domain = null;
            
            // Try to parse as CredSSP/NLA authentication (common in modern RDP)
            if (parseCredSSPAuth(data, info)) {
                return;
            }
            
            // Fallback: Try to parse as basic RDP authentication
            if (parseBasicRdpAuth(data, info)) {
                return;
            }
            
            // Last resort: Try to find credential patterns in the packet data
            parseGenericCredentials(data, info);
            
        } catch (Exception e) {
            log.warn("Error parsing RDP authentication data", e);
        }
    }
    
    /**
     * Parse CredSSP/NLA authentication data (most common in Windows 10+)
     */
    private boolean parseCredSSPAuth(byte[] data, RdpPacketInfo info) {
        try {
            // CredSSP authentication typically contains SPNEGO tokens
            // Look for ASN.1 structures that might contain credentials
            
            // This is a simplified approach - in a full implementation,
            // you would need to properly decode the CredSSP and SPNEGO structures
            
            // Look for patterns that indicate credential information
            for (int i = 0; i < data.length - 20; i++) {
                // Look for potential Unicode string patterns (common in Windows auth)
                if (data[i] == 0x00 && data[i+2] == 0x00 && data[i+1] != 0x00) {
                    // Potential Unicode string - try to extract it
                    String extracted = extractUnicodeString(data, i);
                    if (extracted != null && extracted.length() > 0) {
                        if (info.getUsername() == null && isValidUsername(extracted)) {
                            info.setUsername(extracted);
                        } else if (info.getPassword() == null && isValidPassword(extracted)) {
                            info.setPassword(extracted);
                        } else if (info.getDomain() == null && isValidDomain(extracted)) {
                            info.setDomain(extracted);
                        }
                    }
                }
            }
            
            return info.getUsername() != null || info.getPassword() != null;
            
        } catch (Exception e) {
            log.debug("Error parsing CredSSP auth data", e);
            return false;
        }
    }
    
    /**
     * Parse basic RDP authentication (legacy)
     */
    private boolean parseBasicRdpAuth(byte[] data, RdpPacketInfo info) {
        try {
            // Basic RDP authentication sends credentials in a simpler format
            // Look for null-terminated strings or length-prefixed strings
            
            List<String> extractedStrings = new ArrayList<>();
            
            // Extract null-terminated ASCII strings
            extractedStrings.addAll(extractNullTerminatedStrings(data));
            
            // Extract length-prefixed strings
            extractedStrings.addAll(extractLengthPrefixedStrings(data));
            
            // Assign extracted strings based on patterns
            for (String str : extractedStrings) {
                if (info.getUsername() == null && isValidUsername(str)) {
                    info.setUsername(str);
                } else if (info.getPassword() == null && isValidPassword(str)) {
                    info.setPassword(str);
                } else if (info.getDomain() == null && isValidDomain(str)) {
                    info.setDomain(str);
                }
            }
            
            return info.getUsername() != null || info.getPassword() != null;
            
        } catch (Exception e) {
            log.debug("Error parsing basic RDP auth data", e);
            return false;
        }
    }
    
    /**
     * Generic credential parsing as fallback
     */
    private void parseGenericCredentials(byte[] data, RdpPacketInfo info) {
        try {
            // Last resort: look for any string-like patterns that might be credentials
            List<String> possibleStrings = extractAllPossibleStrings(data);
            
            for (String str : possibleStrings) {
                if (info.getUsername() == null && isValidUsername(str)) {
                    info.setUsername(str);
                } else if (info.getPassword() == null && isValidPassword(str)) {
                    info.setPassword(str);
                } else if (info.getDomain() == null && isValidDomain(str)) {
                    info.setDomain(str);
                }
            }
            
        } catch (Exception e) {
            log.debug("Error in generic credential parsing", e);
        }
    }
    
    /**
     * Extract Unicode string from byte array starting at given position
     */
    private String extractUnicodeString(byte[] data, int start) {
        try {
            StringBuilder sb = new StringBuilder();
            for (int i = start; i < data.length - 1; i += 2) {
                if (data[i] == 0 && data[i + 1] == 0) {
                    break; // End of string
                }
                if (data[i + 1] == 0 && data[i] > 0 && data[i] < 127) {
                    sb.append((char) data[i]);
                } else {
                    break; // Not a valid ASCII Unicode string
                }
            }
            String result = sb.toString().trim();
            return result.length() > 0 ? result : null;
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Extract null-terminated ASCII strings
     */
    private List<String> extractNullTerminatedStrings(byte[] data) {
        List<String> strings = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        
        for (byte b : data) {
            if (b == 0) {
                if (current.length() > 0) {
                    strings.add(current.toString());
                    current = new StringBuilder();
                }
            } else if (b > 31 && b < 127) { // Printable ASCII
                current.append((char) b);
            } else if (current.length() > 0) {
                // Non-printable character, end current string
                strings.add(current.toString());
                current = new StringBuilder();
            }
        }
        
        if (current.length() > 0) {
            strings.add(current.toString());
        }
        
        return strings.stream()
            .filter(s -> s.length() >= 3) // Minimum reasonable length
            .collect(Collectors.toList());
    }
    
    /**
     * Extract length-prefixed strings (common in RDP)
     */
    private List<String> extractLengthPrefixedStrings(byte[] data) {
        List<String> strings = new ArrayList<>();
        
        for (int i = 0; i < data.length - 4; i++) {
            // Look for length prefixes (little-endian 16-bit or 32-bit)
            int len16 = (data[i] & 0xFF) | ((data[i + 1] & 0xFF) << 8);
            int len32 = (data[i] & 0xFF) | ((data[i + 1] & 0xFF) << 8) | 
                       ((data[i + 2] & 0xFF) << 16) | ((data[i + 3] & 0xFF) << 24);
            
            // Try 16-bit length prefix
            if (len16 > 0 && len16 < 256 && i + 2 + len16 <= data.length) {
                String str = extractStringFromBytes(data, i + 2, len16);
                if (str != null && str.length() >= 3) {
                    strings.add(str);
                }
            }
            
            // Try 32-bit length prefix
            if (len32 > 0 && len32 < 1024 && i + 4 + len32 <= data.length) {
                String str = extractStringFromBytes(data, i + 4, len32);
                if (str != null && str.length() >= 3) {
                    strings.add(str);
                }
            }
        }
        
        return strings;
    }
    
    /**
     * Extract string from bytes with given length
     */
    private String extractStringFromBytes(byte[] data, int start, int length) {
        try {
            StringBuilder sb = new StringBuilder();
            for (int i = start; i < start + length && i < data.length; i++) {
                byte b = data[i];
                if (b > 31 && b < 127) {
                    sb.append((char) b);
                } else if (b == 0) {
                    break; // Null terminator
                }
            }
            String result = sb.toString().trim();
            return result.length() > 0 ? result : null;
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Extract all possible strings from the packet data
     */
    private List<String> extractAllPossibleStrings(byte[] data) {
        List<String> allStrings = new ArrayList<>();
        allStrings.addAll(extractNullTerminatedStrings(data));
        allStrings.addAll(extractLengthPrefixedStrings(data));
        return allStrings.stream().distinct().collect(Collectors.toList());
    }
    
    /**
     * Check if a string looks like a valid username
     */
    private boolean isValidUsername(String str) {
        return str != null && str.length() >= 2 && str.length() <= 104 &&
               str.matches("^[a-zA-Z0-9@._-]+$") &&
               !str.equals("Administrator") && // Skip common defaults
               !str.equals("Guest") &&
               !str.equals("admin");
    }
    
    /**
     * Check if a string looks like a valid password (or JWT token)
     */
    private boolean isValidPassword(String str) {
        return str != null && str.length() >= 8 && str.length() <= 2048 &&
               (str.startsWith("__token__:") || // JWT token
                str.matches("^[A-Za-z0-9+/=._-]+$")); // Base64-like or alphanumeric
    }
    
    /**
     * Check if a string looks like a valid domain name
     */
    private boolean isValidDomain(String str) {
        return str != null && str.length() >= 2 && str.length() <= 255 &&
               str.matches("^[a-zA-Z0-9.-]+$") &&
               !str.equals("WORKGROUP"); // Skip defaults
    }

    private boolean containsAuthenticationPattern(ByteBuf data) {
        return data.readableBytes() > 50;
    }

    private boolean containsPattern(byte[] data, String pattern) {
        byte[] patternBytes = pattern.getBytes();
        for (int i = 0; i <= data.length - patternBytes.length; i++) {
            boolean found = true;
            for (int j = 0; j < patternBytes.length; j++) {
                if (data[i + j] != patternBytes[j]) {
                    found = false;
                    break;
                }
            }
            if (found) return true;
        }
        return false;
    }

    private String extractPasswordFromRdpAuth(RdpPacketInfo packetInfo) {
        // First try to get password from parsed packet info (improved parsing)
        if (packetInfo.getPassword() != null) {
            log.debug("Extracted password from parsed RDP packet (length: {})", packetInfo.getPassword().length());
            return packetInfo.getPassword();
        }
        
        // Fallback to legacy heuristic parsing
        try {
            if (packetInfo.getData() == null || packetInfo.getData().length < 10) {
                return null;
            }

            byte[] data = packetInfo.getData();
            StringBuilder password = new StringBuilder();

            // Simplified heuristic: look for printable strings longer than ~20 chars
            // (JWTs are long and base64-like)
            int consecutive = 0;
            for (int i = 0; i < data.length; i++) {
                if (data[i] >= 32 && data[i] < 127) {
                    password.append((char) data[i]);
                    consecutive++;
                } else {
                    if (consecutive > 20) break; // probable end of token
                    password.setLength(0);
                    consecutive = 0;
                }
            }

            String extracted = password.toString().trim();
            if (extracted.startsWith("eyJ") || extracted.startsWith("__token__:")) { 
                log.debug("Extracted JWT-like password of length {}", extracted.length());
                return extracted;
            }
            return null;

        } catch (Exception e) {
            log.error("Error extracting password/JWT from RDP auth data", e);
            return null;
        }
    }
    
    /**
     * Extract username from RDP authentication data
     */
    private String extractUsernameFromRdpAuth(RdpPacketInfo packetInfo) {
        // First try to get username from parsed packet info
        if (packetInfo.getUsername() != null) {
            log.debug("Extracted username from parsed RDP packet: {}", packetInfo.getUsername());
            return packetInfo.getUsername();
        }
        
        // Fallback: look for username patterns in raw data
        try {
            if (packetInfo.getData() == null || packetInfo.getData().length < 4) {
                return "rdp-user"; // Default fallback
            }
            
            // Simple heuristic to find username-like strings
            List<String> possibleUsernames = extractNullTerminatedStrings(packetInfo.getData());
            for (String candidate : possibleUsernames) {
                if (isValidUsername(candidate)) {
                    log.debug("Found potential username: {}", candidate);
                    return candidate;
                }
            }
            
        } catch (Exception e) {
            log.debug("Error extracting username from RDP auth data", e);
        }
        
        log.debug("Username not found in RDP packet, using fallback");
        return "rdp-user"; // Default fallback
    }


    private User getDefaultUser() {
        // Return a default user for initial connection setup
        return userService.findByUsername("admin").orElse(null);
    }

    private HostGroup getDefaultHostGroup() {
        // Return a default host group for initial connection setup
        List<HostGroup> groups = hostGroupService.getAllHostGroups();
        return groups.isEmpty() ? null : groups.get(0);
    }

    /**
     * RDP packet information holder with proper authentication data extraction
     */
    public static class RdpPacketInfo {
        private boolean connectionRequest = false;
        private boolean authenticationData = false;
        private byte[] data;
        private String username;
        private String password;
        private String domain;

        public boolean isConnectionRequest() { return connectionRequest; }
        public void setConnectionRequest(boolean connectionRequest) { this.connectionRequest = connectionRequest; }
        
        public boolean isAuthenticationData() { return authenticationData; }
        public void setAuthenticationData(boolean authenticationData) { this.authenticationData = authenticationData; }
        
        public byte[] getData() { return data; }
        public void setData(byte[] data) { this.data = data; }
        
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        
        public String getDomain() { return domain; }
        public void setDomain(String domain) { this.domain = domain; }
    }
}