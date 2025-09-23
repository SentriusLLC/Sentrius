package io.sentrius.sso.rdpproxy.service;

import io.sentrius.sso.automation.auditing.AccessTokenAuditor;
import io.sentrius.sso.automation.auditing.AccessTokenEvaluator;
import io.sentrius.sso.automation.auditing.SessionTokenEvaluator;
import io.sentrius.sso.automation.auditing.Trigger;
import io.sentrius.sso.automation.auditing.TriggerAction;
import io.sentrius.sso.core.config.SystemOptions;
import io.sentrius.sso.core.data.auditing.RecordingStudio;
import io.sentrius.sso.core.model.ConnectedSystem;
import io.sentrius.sso.core.services.agents.AgentService;
import io.sentrius.sso.core.services.security.ZeroTrustAccessTokenService;
import io.sentrius.sso.core.services.terminal.SessionTrackingService;
import io.sentrius.sso.protobuf.Session;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Service that processes RDP actions and applies Sentrius safeguards.
 * Integrates with existing SessionTrackingService and trigger system.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RdpCommandProcessor {

    private final SessionTrackingService sessionTrackingService;
    private final RdpTerminalResponseService terminalResponseService;
    private final AgentService agentService;
    private final ZeroTrustAccessTokenService zeroTrustAccessTokenService;
    private final SystemOptions systemOptions;
    
    // Track AccessTokenAuditor per session for proper command handling with ctrl+c, backspace, etc.
    private final ConcurrentMap<Long, AccessTokenAuditor> sessionAuditors = new ConcurrentHashMap<>();

    private final Set<Integer> pressedModifiers = ConcurrentHashMap.newKeySet();

    /**
     * Processes an RDP action through the trigger system and returns whether it should be executed
     */
    public boolean processRdpAction(ConnectedSystem connectedSystem, RdpAction action, OutputStream rdpOutput) {
        try {
            log.debug("Processing RDP action: {} for session: {}", action.getType(), connectedSystem.getSession().getId());
            
            // Apply security rules based on RDP action type
            if (isDangerousAction(action)) {
                Trigger denyTrigger = new Trigger(TriggerAction.DENY_ACTION, "RDP action blocked by security policy: " + action.getDescription());
                return handleTrigger(denyTrigger, rdpOutput, action, false);
            }
            
            if (isWarningAction(action)) {
                Trigger warnTrigger = new Trigger(TriggerAction.WARN_ACTION, "This RDP action requires caution: " + action.getDescription());
                handleTrigger(warnTrigger, rdpOutput, action, false);
                return true; // Allow but warn
            }

            if (isHighPrivilegeAction(action)) {
                Trigger recordTrigger = new Trigger(TriggerAction.RECORD_ACTION, "Recording high privilege RDP action: " + action.getDescription());
                handleTrigger(recordTrigger, rdpOutput, action, false);
                return true; // Allow and record
            }

            // Action is allowed
            return true;

        } catch (Exception e) {
            log.error("Error processing RDP action through trigger system", e);
            try {
                terminalResponseService.sendMessage("Error: RDP action processing failed\r\n", rdpOutput);
            } catch (IOException ioException) {
                log.error("Error sending error message to RDP output", ioException);
            }
            return false;
        }
    }
    
    /**
     * Check if RDP action is considered dangerous and should be blocked
     */
    private boolean isDangerousAction(RdpAction action) {
        switch (action.getType()) {
            case FILE_DELETE:
                return action.getTarget() != null && (
                    action.getTarget().contains("system32") ||
                    action.getTarget().contains("windows") ||
                    action.getTarget().endsWith(".exe") ||
                    action.getTarget().endsWith(".dll")
                );
            case REGISTRY_MODIFY:
                return action.getTarget() != null && (
                    action.getTarget().contains("HKEY_LOCAL_MACHINE") ||
                    action.getTarget().contains("CurrentVersion\\Run")
                );
            case PROCESS_TERMINATE:
                return action.getTarget() != null && (
                    action.getTarget().contains("explorer.exe") ||
                    action.getTarget().contains("winlogon.exe") ||
                    action.getTarget().contains("csrss.exe")
                );
            case NETWORK_ACCESS:
                return action.getTarget() != null && (
                    action.getTarget().contains("445") || // SMB
                    action.getTarget().contains("22") ||  // SSH
                    action.getTarget().contains("21")     // FTP
                );
            default:
                return false;
        }
    }
    
    /**
     * Check if RDP action should trigger a warning
     */
    private boolean isWarningAction(RdpAction action) {
        switch (action.getType()) {
            case FILE_COPY_OUT:
            case FILE_COPY_IN:
            case CLIPBOARD_ACCESS:
            case DRIVE_REDIRECT:
                return true;
            case PROCESS_START:
                return action.getTarget() != null && (
                    action.getTarget().contains("cmd.exe") ||
                    action.getTarget().contains("powershell.exe") ||
                    action.getTarget().contains("regedit.exe")
                );
            default:
                return false;
        }
    }

    /**
     * Check if RDP action is high privilege and should be recorded
     */
    private boolean isHighPrivilegeAction(RdpAction action) {
        switch (action.getType()) {
            case ADMIN_ACCESS:
            case SERVICE_CONTROL:
            case USER_MANAGEMENT:
            case SECURITY_SETTINGS:
                return true;
            default:
                return false;
        }
    }

    /**
     * Handles a trigger by sending appropriate response to RDP output and returning execution decision
     */
    private boolean handleTrigger(Trigger trigger, OutputStream rdpOutput, RdpAction action, boolean isSessionTrigger) {
        try {
            switch (trigger.getAction()) {
                case DENY_ACTION:
                    terminalResponseService.sendTriggerResponse(trigger, rdpOutput);
                    log.warn("RDP action denied: {} - {}", action.getType(), trigger.getDescription());
                    return false; // Block action execution

                case WARN_ACTION:
                    terminalResponseService.sendTriggerResponse(trigger, rdpOutput);
                    log.warn("RDP action warning: {} - {}", action.getType(), trigger.getDescription());
                    return true; // Allow action but with warning

                case RECORD_ACTION:
                    terminalResponseService.sendTriggerResponse(trigger, rdpOutput);
                    log.info("Recording RDP action: {} - {}", action.getType(), trigger.getDescription());
                    return true; // Allow action and record

                case ALERT_ACTION:
                    terminalResponseService.sendTriggerResponse(trigger, rdpOutput);
                    log.warn("RDP action alert: {} - {}", action.getType(), trigger.getDescription());
                    return true; // Allow action but send alert

                case APPROVE_ACTION:
                    terminalResponseService.sendTriggerResponse(trigger, rdpOutput);
                    log.info("RDP action approved: {} - {}", action.getType(), trigger.getDescription());
                    return true; // Action approved

                case PROMPT_ACTION:
                    // For now, treat prompt as warning in RDP mode
                    terminalResponseService.sendTriggerResponse(trigger, rdpOutput);
                    log.info("RDP action requires approval: {} - {}", action.getType(), trigger.getDescription());
                    return true;

                case JIT_ACTION:
                    terminalResponseService.sendTriggerResponse(trigger, rdpOutput);
                    log.info("RDP action requires JIT access: {} - {}", action.getType(), trigger.getDescription());
                    return true;

                case PERSISTENT_MESSAGE:
                    terminalResponseService.sendTriggerResponse(trigger, rdpOutput);
                    return true; // Allow action with message

                case LOG_ACTION:
                    // Log action doesn't display message, just logs
                    log.info("Logging RDP action: {} - {}", action.getType(), action.getDescription());
                    return true;

                case NO_ACTION:
                default:
                    return true; // Allow action
            }
        } catch (IOException e) {
            log.error("Error sending trigger response to RDP output", e);
            return false; // Block action on error
        }
    }

    /**
     * Processes mouse/keyboard input for RDP sessions with comprehensive behavioral analysis
     */
    public boolean processInputEvent(ConnectedSystem connectedSystem, RdpInputEvent event, OutputStream rdpOutput) {
        try {
            Long sessionId = connectedSystem.getSession().getId();
            log.debug("Processing RDP input event: {} for session: {}", event.getType(), sessionId);
            
            // Comprehensive input event analysis
            InputEventAnalysis analysis = analyzeInputEvent(event, connectedSystem);
            
            // Check for suspicious patterns
            if (analysis.isSuspicious()) {
                Trigger suspiciousTrigger = new Trigger(
                    analysis.getTriggerAction(), 
                    "Suspicious input pattern detected: " + analysis.getReason()
                );
                boolean allowed = handleTrigger(suspiciousTrigger, rdpOutput, 
                    new RdpAction(RdpAction.RdpActionType.SCREEN_CAPTURE, "input-analysis", analysis.getDescription()), 
                    false);
                
                if (!allowed) {
                    log.warn("Blocked suspicious input event: {} - {}", event.getType(), analysis.getReason());
                    return false;
                }
            }
            
            // Track input patterns for behavioral analysis
            trackInputBehavior(event, analysis, connectedSystem);
            
            // Send to agents for multimodal analysis if configured
            notifyAgentsOfInputEvent(event, analysis, connectedSystem);
            
            // Apply input-specific filtering rules
            return applyInputFiltering(event, analysis, connectedSystem, rdpOutput);
            
        } catch (Exception e) {
            log.error("Error processing RDP input event through trigger system", e);
            try {
                terminalResponseService.sendMessage("Error: Input event processing failed\r\n", rdpOutput);
            } catch (IOException ioException) {
                log.error("Error sending input error message to RDP output", ioException);
            }
            return false;
        }
    }
    
    /**
     * Analyzes input events for suspicious behavior patterns
     */
    private InputEventAnalysis analyzeInputEvent(RdpInputEvent event, ConnectedSystem connectedSystem) {
        InputEventAnalysis analysis = new InputEventAnalysis(event);
        
        switch (event.getType()) {
            case KEYBOARD:
                analysis = analyzeKeyboardInput(event, connectedSystem);
                break;
            case MOUSE_CLICK:
                analysis = analyzeMouseClick(event, connectedSystem);
                break;
            case MOUSE_MOVE:
                analysis = analyzeMouseMovement(event, connectedSystem);
                break;
            case SCROLL:
                analysis = analyzeScrollEvent(event, connectedSystem);
                break;
        }
        
        return analysis;
    }
    
    /**
     * Analyzes keyboard input for suspicious patterns using AccessTokenAuditor
     * This leverages the existing infrastructure for handling ctrl+c, backspace, enter, etc.
     */
    private InputEventAnalysis analyzeKeyboardInput(RdpInputEvent event, ConnectedSystem connectedSystem) {
        InputEventAnalysis analysis = new InputEventAnalysis(event);
        String keyData = event.getData();
        Long sessionId = connectedSystem.getSession().getId();
        
        // Get or create AccessTokenAuditor for this session
        AccessTokenAuditor auditor = sessionAuditors.get(sessionId);
        if (auditor == null) {
            log.warn("No AccessTokenAuditor found for session {}, cannot analyze keyboard input", sessionId);
            return analysis;
        }
        
        // Parse key data to extract keysym and convert to keycode
        Integer keycode = extractKeycode(keyData);
        String typedChar = extractTypedText(keyData);
        
        if (keycode != null) {
            // Use AccessTokenAuditor to handle special keys (enter, backspace, ctrl+c, etc.)
            TriggerAction action = auditor.keycode(keycode);
            
            if (action == TriggerAction.DENY_ACTION) {
                analysis.setSuspicious(true);
                analysis.setTriggerAction(TriggerAction.DENY_ACTION);
                analysis.setReason("Command blocked by rule");
                analysis.setDescription("AccessTokenAuditor blocked the command");
                return analysis;
            } else if (action == TriggerAction.RECORD_ACTION) {
                analysis.setTriggerAction(TriggerAction.RECORD_ACTION);
                analysis.setDescription("Command being recorded");
            }
        } else if (typedChar != null && !typedChar.isEmpty()) {
            // Regular character input - append to auditor's command builder
            String currentCommand = auditor.append(typedChar);
            log.debug("Current command for session {}: {}", sessionId, currentCommand);
            
            // The auditor's onPartial() method is called automatically and evaluates rules
            // Check the current trigger state
            Trigger currentTrigger = auditor.getCurrentTrigger();
            if (currentTrigger != null) {
                if (currentTrigger.getAction() == TriggerAction.DENY_ACTION) {
                    analysis.setSuspicious(true);
                    analysis.setTriggerAction(TriggerAction.DENY_ACTION);
                    analysis.setReason("Rule violation: " + currentTrigger.getDescription());
                    analysis.setDescription("Command blocked by rule");
                } else if (currentTrigger.getAction() == TriggerAction.WARN_ACTION) {
                    analysis.setSuspicious(true);
                    analysis.setTriggerAction(TriggerAction.WARN_ACTION);
                    analysis.setReason("Rule warning: " + currentTrigger.getDescription());
                }
            }
        }
        
        // Check for dangerous key combinations
        if (containsDangerousKeyCombination(keyData)) {
            analysis.setSuspicious(true);
            analysis.setTriggerAction(TriggerAction.WARN_ACTION);
            analysis.setReason("Dangerous key combination detected: " + keyData);
            analysis.setDescription("Potential system manipulation via keyboard shortcuts");
        }
        
        // Check for rapid typing (potential automated input)
        if (isRapidTyping(keyData, connectedSystem)) {
            analysis.setSuspicious(true);
            analysis.setTriggerAction(TriggerAction.RECORD_ACTION);
            analysis.setReason("Unusually rapid typing detected");
            analysis.setDescription("Potential automated input or script execution");
        }
        
        // Check for password-like patterns
        if (containsPasswordPattern(keyData)) {
            analysis.setContainsSensitiveData(true);
            analysis.setDescription("Password or sensitive data input detected");
        }
        
        // Check for command-like patterns
        if (auditor != null && auditor.get() != null && !auditor.get().isEmpty()) {
            analysis.setContainsCommands(true);
            analysis.setDescription("Command execution pattern detected");
        }
        
        return analysis;
    }
    
    /**
     * Analyzes mouse click events for suspicious behavior
     */
    private InputEventAnalysis analyzeMouseClick(RdpInputEvent event, ConnectedSystem connectedSystem) {
        InputEventAnalysis analysis = new InputEventAnalysis(event);
        String clickData = event.getData();
        
        // Parse click coordinates and button
        MouseClickInfo clickInfo = parseMouseClickData(clickData);
        
        // Check for rapid clicking (potential automated behavior)
        if (isRapidClicking(clickInfo, connectedSystem)) {
            analysis.setSuspicious(true);
            analysis.setTriggerAction(TriggerAction.WARN_ACTION);
            analysis.setReason("Unusually rapid mouse clicking detected");
            analysis.setDescription("Potential automated clicking or bot behavior");
        }
        
        // Check for clicks on sensitive UI elements
        if (clickInfo.isOnSensitiveArea()) {
            analysis.setTriggerAction(TriggerAction.RECORD_ACTION);
            analysis.setDescription("Click on sensitive UI area: " + clickInfo.getAreaDescription());
        }
        
        return analysis;
    }
    
    /**
     * Analyzes mouse movement for behavioral patterns
     */
    private InputEventAnalysis analyzeMouseMovement(RdpInputEvent event, ConnectedSystem connectedSystem) {
        InputEventAnalysis analysis = new InputEventAnalysis(event);
        String moveData = event.getData();
        
        // Parse movement coordinates
        MouseMovementInfo moveInfo = parseMouseMovementData(moveData);
        
        // Check for unnatural movement patterns (straight lines, perfect curves)
        if (moveInfo.isUnnatural()) {
            analysis.setSuspicious(true);
            analysis.setTriggerAction(TriggerAction.RECORD_ACTION);
            analysis.setReason("Unnatural mouse movement pattern detected");
            analysis.setDescription("Potential automated mouse control");
        }
        
        // Track movement velocity and patterns
        analysis.setMovementVelocity(moveInfo.getVelocity());
        analysis.setMovementPattern(moveInfo.getPattern());
        
        return analysis;
    }
    
    /**
     * Analyzes scroll events for patterns
     */
    private InputEventAnalysis analyzeScrollEvent(RdpInputEvent event, ConnectedSystem connectedSystem) {
        InputEventAnalysis analysis = new InputEventAnalysis(event);
        String scrollData = event.getData();
        
        // Parse scroll information
        ScrollInfo scrollInfo = parseScrollData(scrollData);
        
        // Check for excessive scrolling (data exfiltration attempts)
        if (scrollInfo.isExcessive()) {
            analysis.setSuspicious(true);
            analysis.setTriggerAction(TriggerAction.ALERT_ACTION);
            analysis.setReason("Excessive scrolling activity detected");
            analysis.setDescription("Potential data scanning or exfiltration attempt");
        }
        
        return analysis;
    }
    
    /**
     * Tracks input behavior patterns for analysis
     */
    private void trackInputBehavior(RdpInputEvent event, InputEventAnalysis analysis, ConnectedSystem connectedSystem) {
        try {
            Long sessionId = connectedSystem.getSession().getId();
            
            // Create behavior tracking record
            InputBehaviorRecord record = InputBehaviorRecord.builder()
                .sessionId(sessionId)
                .timestamp(System.currentTimeMillis())
                .inputType(event.getType().toString())
                .inputData(event.getData())
                .analysis(analysis)
                .suspicious(analysis.isSuspicious())
                .containsSensitiveData(analysis.isContainsSensitiveData())
                .containsCommands(analysis.isContainsCommands())
                .build();
            
            // Store for behavioral pattern analysis
            // Note: This method would be added to SessionTrackingService for comprehensive input tracking
            // sessionTrackingService.trackInputBehavior(connectedSystem, record);
            
            // For now, log the behavior tracking
            log.info("Input behavior tracked for session {}: type={}, suspicious={}, sensitive={}", 
                sessionId, record.getInputType(), record.isSuspicious(), record.isContainsSensitiveData());
            
        } catch (Exception e) {
            log.error("Error tracking input behavior", e);
        }
    }
    
    /**
     * Notifies agents of input events for multimodal analysis
     */
    private void notifyAgentsOfInputEvent(RdpInputEvent event, InputEventAnalysis analysis, ConnectedSystem connectedSystem) {
        try {
            // Create agent notification with multimodal data
            AgentInputNotification notification = AgentInputNotification.builder()
                .sessionId(connectedSystem.getSession().getId())
                .userId(connectedSystem.getUser().getId())
                .inputType(event.getType().toString())
                .inputData(event.getData())
                .analysis(analysis)
                .timestamp(System.currentTimeMillis())
                .requiresMultimodalAnalysis(analysis.isSuspicious() || analysis.isContainsSensitiveData())
                .build();
            
            // Send to agent service for processing
            // Note: This would integrate with the existing agent communication system
            log.info("Notifying agents of RDP input event: {} for session: {}", 
                event.getType(), connectedSystem.getSession().getId());
                
            // Integrate with actual agent notification system
            String inputEventPayload = String.format(
                "{\"type\":\"rdp_input_event\",\"eventType\":\"%s\",\"sessionId\":\"%s\",\"timestamp\":\"%d\"}", 
                event.getType(),
                connectedSystem.getSession().getId(),
                System.currentTimeMillis()
            );
            
            agentService.saveCommunication(
                java.util.UUID.randomUUID().toString(),
                "rdp-proxy", 
                "analytics-agent", 
                "rdp_input_event_notification", 
                inputEventPayload
            );
            
        } catch (Exception e) {
            log.error("Error notifying agents of input event", e);
        }
    }
    
    /**
     * Applies input-specific filtering rules
     */
    private boolean applyInputFiltering(RdpInputEvent event, InputEventAnalysis analysis, 
                                      ConnectedSystem connectedSystem, OutputStream rdpOutput) {
        try {
            // Apply general input filtering based on analysis
            if (analysis.isSuspicious() && analysis.getTriggerAction() == TriggerAction.DENY_ACTION) {
                terminalResponseService.sendMessage(
                    "[Sentrius] Input blocked: " + analysis.getReason() + "\r\n", 
                    rdpOutput);
                return false;
            }
            
            // Apply specific filtering based on input type
            switch (event.getType()) {
                case KEYBOARD:
                    return filterKeyboardInput(event, analysis, rdpOutput);
                case MOUSE_CLICK:
                    return filterMouseClick(event, analysis, rdpOutput);
                case MOUSE_MOVE:
                    // Usually allow mouse movement unless extremely suspicious
                    return !analysis.isSuspicious() || analysis.getTriggerAction() != TriggerAction.DENY_ACTION;
                case SCROLL:
                    return filterScrollEvent(event, analysis, rdpOutput);
                default:
                    return true;
            }
            
        } catch (Exception e) {
            log.error("Error applying input filtering", e);
            return false; // Block on error for security
        }
    }
    
    /**
     * Filters keyboard input based on analysis
     */
    private boolean filterKeyboardInput(RdpInputEvent event, InputEventAnalysis analysis, OutputStream rdpOutput) throws IOException {
        String keyData = event.getData();
        
        // Block dangerous key combinations
        if (containsDangerousKeyCombination(keyData)) {
            terminalResponseService.sendMessage(
                "[Sentrius] Blocked dangerous key combination: " + keyData + "\r\n", 
                rdpOutput);
            return false;
        }
        
        // Warn on sensitive operations
        if (analysis.isContainsSensitiveData()) {
            terminalResponseService.sendMessage(
                "[Sentrius] Sensitive data input detected - monitoring enabled\r\n", 
                rdpOutput);
        }
        
        return true;
    }
    
    /**
     * Filters mouse click events
     */
    private boolean filterMouseClick(RdpInputEvent event, InputEventAnalysis analysis, OutputStream rdpOutput) throws IOException {
        if (analysis.isSuspicious() && analysis.getTriggerAction() == TriggerAction.DENY_ACTION) {
            terminalResponseService.sendMessage(
                "[Sentrius] Blocked suspicious mouse activity\r\n", 
                rdpOutput);
            return false;
        }
        
        return true;
    }
    
    /**
     * Filters scroll events
     */
    private boolean filterScrollEvent(RdpInputEvent event, InputEventAnalysis analysis, OutputStream rdpOutput) throws IOException {
        if (analysis.isSuspicious()) {
            terminalResponseService.sendMessage(
                "[Sentrius] Excessive scrolling detected - activity being recorded\r\n", 
                rdpOutput);
        }
        
        return true;
    }
    
    // Helper methods for pattern detection
    
    private boolean containsDangerousKeyCombination(String keyData) {
        String[] dangerousKeys = {
            "ctrl+alt+del", "ctrl+shift+esc", "alt+f4", "ctrl+alt+t", 
            "win+r", "win+x", "ctrl+alt+f1", "ctrl+alt+f2"
        };
        
        String normalizedKey = keyData.toLowerCase().trim();
        for (String dangerous : dangerousKeys) {
            if (normalizedKey.contains(dangerous)) {
                return true;
            }
        }
        return false;
    }
    
    private boolean isRapidTyping(String keyData, ConnectedSystem connectedSystem) {
        // Implementation would track typing speed and detect automation
        // For now, use simple heuristics
        return keyData.length() > 50 && !keyData.contains(" ");
    }
    
    private boolean containsPasswordPattern(String keyData) {
        // Look for password-like typing patterns
        return keyData.toLowerCase().contains("password") || 
               keyData.matches(".*[\\*]{3,}.*") ||
               (keyData.length() > 8 && !keyData.contains(" "));
    }
    
    private boolean containsCommandPattern(String keyData) {
        String[] commandPatterns = {
            "cmd", "powershell", "bash", "sh", "exec", "system", 
            "net user", "reg add", "sc create", "schtasks"
        };
        
        String normalizedKey = keyData.toLowerCase();
        for (String pattern : commandPatterns) {
            if (normalizedKey.contains(pattern)) {
                return true;
            }
        }
        return false;
    }
    
    private MouseClickInfo parseMouseClickData(String clickData) {
        // Parse mouse click data format: "button:x:y" or similar
        try {
            String[] parts = clickData.split(":");
            return MouseClickInfo.builder()
                .button(parts.length > 0 ? parts[0] : "left")
                .x(parts.length > 1 ? Integer.parseInt(parts[1]) : 0)
                .y(parts.length > 2 ? Integer.parseInt(parts[2]) : 0)
                .build();
        } catch (Exception e) {
            return MouseClickInfo.builder().button("unknown").x(0).y(0).build();
        }
    }
    
    private boolean isRapidClicking(MouseClickInfo clickInfo, ConnectedSystem connectedSystem) {
        // Implementation would track click timing and frequency
        // For now, assume not rapid
        return false;
    }
    
    private MouseMovementInfo parseMouseMovementData(String moveData) {
        try {
            String[] parts = moveData.split(":");
            int x = parts.length > 0 ? Integer.parseInt(parts[0]) : 0;
            int y = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            
            return MouseMovementInfo.builder()
                .x(x)
                .y(y)
                .velocity(calculateVelocity(x, y))
                .build();
        } catch (Exception e) {
            return MouseMovementInfo.builder().x(0).y(0).velocity(0.0).build();
        }
    }
    
    private double calculateVelocity(int x, int y) {
        // Simple velocity calculation - in real implementation would track over time
        return Math.sqrt(x * x + y * y);
    }
    
    private ScrollInfo parseScrollData(String scrollData) {
        try {
            String[] parts = scrollData.split(":");
            int delta = parts.length > 0 ? Integer.parseInt(parts[0]) : 0;
            
            return ScrollInfo.builder()
                .delta(delta)
                .excessive(Math.abs(delta) > 1000) // Threshold for excessive scrolling
                .build();
        } catch (Exception e) {
            return ScrollInfo.builder().delta(0).excessive(false).build();
        }
    }
    
    /**
     * Extract keycode from Guacamole key data for AccessTokenAuditor
     * Maps common keysyms to keycodes that AccessTokenAuditor understands
     */
    private Integer extractKeycode(String keyData) {
        try {
            if (!keyData.contains("keysym:"))
                return null;

            String[] parts = keyData.split(",");
            Integer keysym = null;
            boolean pressed = false;

            for (String part : parts) {
                part = part.trim();
                if (part.startsWith("keysym:")) {
                    keysym = Integer.parseInt(part.substring(part.indexOf(":") + 1).trim());
                } else if (part.startsWith("pressed:")) {
                    pressed = part.endsWith("1");
                }
            }

            if (keysym == null || !pressed) {
                return null; // Only process key presses
            }

            // Map Guacamole keysyms to keycodes that AccessTokenAuditor expects
            // Enter
            if (keysym == 65293) return 13;
            // Backspace
            if (keysym == 65288) return 8;
            // Tab
            if (keysym == 65289) return 9;
            // Up arrow
            if (keysym == 65362) return 38;
            // Down arrow
            if (keysym == 65364) return 48;
            
            // For other keys, return null (they'll be handled as typed characters)
            return null;
            
        } catch (Exception e) {
            log.debug("Error extracting keycode from key data: {}", keyData, e);
            return null;
        }
    }
    
    /**
     * Extract typed text from Guacamole key data
     * Key data format: "keysym:65307,pressed:1" where keysym is the key code
     */
    private String extractTypedText(String keyData) {
        try {
            if (!keyData.contains("keysym:"))
                return null;

            String[] parts = keyData.split(",");
            Integer keysym = null;
            boolean pressed = false;

            for (String part : parts) {
                part = part.trim();
                if (part.startsWith("keysym:")) {
                    keysym = Integer.parseInt(part.substring(part.indexOf(":") + 1).trim());
                } else if (part.startsWith("pressed:")) {
                    pressed = part.endsWith("1");
                }
            }

            if (keysym == null)
                return null;

            // Handle modifier state tracking
            if (keysym == 65507 || keysym == 65508) { // Control_L / Control_R
                if (pressed) pressedModifiers.add(keysym);
                else pressedModifiers.remove(keysym);
                return null;
            }
            if (keysym == 65505 || keysym == 65506) { // Shift_L / Shift_R
                if (pressed) pressedModifiers.add(keysym);
                else pressedModifiers.remove(keysym);
                return null;
            }
            if (keysym == 65513 || keysym == 65514) { // Alt_L / Alt_R
                if (pressed) pressedModifiers.add(keysym);
                else pressedModifiers.remove(keysym);
                return null;
            }

            // We only process key presses (not releases)
            if (!pressed)
                return null;

            boolean ctrl = pressedModifiers.contains(65507) || pressedModifiers.contains(65508);
            boolean shift = pressedModifiers.contains(65505) || pressedModifiers.contains(65506);

            // Handle control key combinations
            if (ctrl) {
                // Ctrl+A..Z (ASCII 1–26)
                if (keysym >= 97 && keysym <= 122) { // lowercase a-z
                    char ch = (char) (keysym - 96);  // convert to control code
                    return "^" + (char) (keysym - 32); // returns e.g. "^C" for display/logging
                }
            }

            // Printable characters
            if (keysym >= 32 && keysym <= 126) {
                char ch = (char)keysym.intValue();
                if (shift && Character.isLetter(ch))
                    ch = Character.toUpperCase(ch);
                return String.valueOf(ch);
            }

            // Special keys
            switch (keysym) {
                case 65293: return "\n"; // Enter
                case 65288: return "\b"; // Backspace
                case 32:    return " ";  // Space
            }

        } catch (Exception e) {
            log.debug("Error extracting typed text from key data: {}", keyData, e);
        }
        return null;
    }
    
    /**
     * Register session rules for command evaluation (e.g., DeletePrevention, SudoPrevention)
     * Creates an AccessTokenAuditor for the session with the rules
     */
    public void registerSessionRules(Long sessionId, List<AccessTokenEvaluator> rules, 
                                     List<SessionTokenEvaluator> startupActions,
                                     ConnectedSystem connectedSystem) {
        try {
            if (rules == null || rules.isEmpty()) {
                log.warn("No rules provided for session {}", sessionId);
                return;
            }
            
            // Get or create RecordingStudio for the session
            RecordingStudio recorder = connectedSystem.getTerminalRecorder();
            
            // Create AccessTokenAuditor with proper infrastructure
            AccessTokenAuditor auditor = new AccessTokenAuditor(
                zeroTrustAccessTokenService,
                connectedSystem,
                sessionTrackingService,
                recorder
            );
            
            // Set up rules
            auditor.setSynchronousRules(rules);
            if (startupActions != null && !startupActions.isEmpty()) {
                auditor.setStartupActions(startupActions);
            }
            
            sessionAuditors.put(sessionId, auditor);
            log.info("Registered AccessTokenAuditor with {} rules for session {}", rules.size(), sessionId);
            
        } catch (ClassNotFoundException | NoSuchMethodException | InvocationTargetException | 
                 InstantiationException | IllegalAccessException e) {
            log.error("Error registering session rules for session {}", sessionId, e);
        }
    }
    
    /**
     * Clear session data when session ends
     */
    public void clearSession(Long sessionId) {
        AccessTokenAuditor auditor = sessionAuditors.remove(sessionId);
        if (auditor != null) {
            auditor.shutdown();
        }
        log.debug("Cleared session data for session {}", sessionId);
    }

    /**
     * Comprehensive analysis of input events for behavioral monitoring
     */
    public static class InputEventAnalysis {
        private final RdpInputEvent originalEvent;
        private boolean suspicious = false;
        private TriggerAction triggerAction = TriggerAction.NO_ACTION;
        private String reason = "";
        private String description = "";
        private boolean containsSensitiveData = false;
        private boolean containsCommands = false;
        private double movementVelocity = 0.0;
        private String movementPattern = "normal";
        
        public InputEventAnalysis(RdpInputEvent event) {
            this.originalEvent = event;
            this.description = "Input event: " + event.getType();
        }
        
        // Getters and setters
        public RdpInputEvent getOriginalEvent() { return originalEvent; }
        public boolean isSuspicious() { return suspicious; }
        public void setSuspicious(boolean suspicious) { this.suspicious = suspicious; }
        public TriggerAction getTriggerAction() { return triggerAction; }
        public void setTriggerAction(TriggerAction triggerAction) { this.triggerAction = triggerAction; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public boolean isContainsSensitiveData() { return containsSensitiveData; }
        public void setContainsSensitiveData(boolean containsSensitiveData) { this.containsSensitiveData = containsSensitiveData; }
        public boolean isContainsCommands() { return containsCommands; }
        public void setContainsCommands(boolean containsCommands) { this.containsCommands = containsCommands; }
        public double getMovementVelocity() { return movementVelocity; }
        public void setMovementVelocity(double movementVelocity) { this.movementVelocity = movementVelocity; }
        public String getMovementPattern() { return movementPattern; }
        public void setMovementPattern(String movementPattern) { this.movementPattern = movementPattern; }
    }
    
    /**
     * Input behavior tracking record for pattern analysis
     */
    public static class InputBehaviorRecord {
        private final Long sessionId;
        private final long timestamp;
        private final String inputType;
        private final String inputData;
        private final InputEventAnalysis analysis;
        private final boolean suspicious;
        private final boolean containsSensitiveData;
        private final boolean containsCommands;
        
        private InputBehaviorRecord(Builder builder) {
            this.sessionId = builder.sessionId;
            this.timestamp = builder.timestamp;
            this.inputType = builder.inputType;
            this.inputData = builder.inputData;
            this.analysis = builder.analysis;
            this.suspicious = builder.suspicious;
            this.containsSensitiveData = builder.containsSensitiveData;
            this.containsCommands = builder.containsCommands;
        }
        
        public static Builder builder() { return new Builder(); }
        
        public static class Builder {
            private Long sessionId;
            private long timestamp;
            private String inputType;
            private String inputData;
            private InputEventAnalysis analysis;
            private boolean suspicious;
            private boolean containsSensitiveData;
            private boolean containsCommands;
            
            public Builder sessionId(Long sessionId) { this.sessionId = sessionId; return this; }
            public Builder timestamp(long timestamp) { this.timestamp = timestamp; return this; }
            public Builder inputType(String inputType) { this.inputType = inputType; return this; }
            public Builder inputData(String inputData) { this.inputData = inputData; return this; }
            public Builder analysis(InputEventAnalysis analysis) { this.analysis = analysis; return this; }
            public Builder suspicious(boolean suspicious) { this.suspicious = suspicious; return this; }
            public Builder containsSensitiveData(boolean containsSensitiveData) { this.containsSensitiveData = containsSensitiveData; return this; }
            public Builder containsCommands(boolean containsCommands) { this.containsCommands = containsCommands; return this; }
            
            public InputBehaviorRecord build() { return new InputBehaviorRecord(this); }
        }
        
        // Getters
        public Long getSessionId() { return sessionId; }
        public long getTimestamp() { return timestamp; }
        public String getInputType() { return inputType; }
        public String getInputData() { return inputData; }
        public InputEventAnalysis getAnalysis() { return analysis; }
        public boolean isSuspicious() { return suspicious; }
        public boolean isContainsSensitiveData() { return containsSensitiveData; }
        public boolean isContainsCommands() { return containsCommands; }
    }
    
    /**
     * Agent notification for multimodal input analysis
     */
    public static class AgentInputNotification {
        private final Long sessionId;
        private final Long userId;
        private final String inputType;
        private final String inputData;
        private final InputEventAnalysis analysis;
        private final long timestamp;
        private final boolean requiresMultimodalAnalysis;
        
        private AgentInputNotification(Builder builder) {
            this.sessionId = builder.sessionId;
            this.userId = builder.userId;
            this.inputType = builder.inputType;
            this.inputData = builder.inputData;
            this.analysis = builder.analysis;
            this.timestamp = builder.timestamp;
            this.requiresMultimodalAnalysis = builder.requiresMultimodalAnalysis;
        }
        
        public static Builder builder() { return new Builder(); }
        
        public static class Builder {
            private Long sessionId;
            private Long userId;
            private String inputType;
            private String inputData;
            private InputEventAnalysis analysis;
            private long timestamp;
            private boolean requiresMultimodalAnalysis;
            
            public Builder sessionId(Long sessionId) { this.sessionId = sessionId; return this; }
            public Builder userId(Long userId) { this.userId = userId; return this; }
            public Builder inputType(String inputType) { this.inputType = inputType; return this; }
            public Builder inputData(String inputData) { this.inputData = inputData; return this; }
            public Builder analysis(InputEventAnalysis analysis) { this.analysis = analysis; return this; }
            public Builder timestamp(long timestamp) { this.timestamp = timestamp; return this; }
            public Builder requiresMultimodalAnalysis(boolean requiresMultimodalAnalysis) { this.requiresMultimodalAnalysis = requiresMultimodalAnalysis; return this; }
            
            public AgentInputNotification build() { return new AgentInputNotification(this); }
        }
        
        // Getters
        public Long getSessionId() { return sessionId; }
        public Long getUserId() { return userId; }
        public String getInputType() { return inputType; }
        public String getInputData() { return inputData; }
        public InputEventAnalysis getAnalysis() { return analysis; }
        public long getTimestamp() { return timestamp; }
        public boolean isRequiresMultimodalAnalysis() { return requiresMultimodalAnalysis; }
    }
    
    /**
     * Mouse click information for analysis
     */
    public static class MouseClickInfo {
        private final String button;
        private final int x;
        private final int y;
        private final boolean onSensitiveArea;
        private final String areaDescription;
        
        private MouseClickInfo(Builder builder) {
            this.button = builder.button;
            this.x = builder.x;
            this.y = builder.y;
            this.onSensitiveArea = builder.onSensitiveArea;
            this.areaDescription = builder.areaDescription;
        }
        
        public static Builder builder() { return new Builder(); }
        
        public static class Builder {
            private String button = "left";
            private int x = 0;
            private int y = 0;
            private boolean onSensitiveArea = false;
            private String areaDescription = "normal";
            
            public Builder button(String button) { this.button = button; return this; }
            public Builder x(int x) { this.x = x; return this; }
            public Builder y(int y) { this.y = y; return this; }
            public Builder onSensitiveArea(boolean onSensitiveArea) { this.onSensitiveArea = onSensitiveArea; return this; }
            public Builder areaDescription(String areaDescription) { this.areaDescription = areaDescription; return this; }
            
            public MouseClickInfo build() { return new MouseClickInfo(this); }
        }
        
        // Getters
        public String getButton() { return button; }
        public int getX() { return x; }
        public int getY() { return y; }
        public boolean isOnSensitiveArea() { return onSensitiveArea; }
        public String getAreaDescription() { return areaDescription; }
    }
    
    /**
     * Mouse movement information for behavioral analysis
     */
    public static class MouseMovementInfo {
        private final int x;
        private final int y;
        private final double velocity;
        private final boolean unnatural;
        private final String pattern;
        
        private MouseMovementInfo(Builder builder) {
            this.x = builder.x;
            this.y = builder.y;
            this.velocity = builder.velocity;
            this.unnatural = builder.unnatural;
            this.pattern = builder.pattern;
        }
        
        public static Builder builder() { return new Builder(); }
        
        public static class Builder {
            private int x = 0;
            private int y = 0;
            private double velocity = 0.0;
            private boolean unnatural = false;
            private String pattern = "normal";
            
            public Builder x(int x) { this.x = x; return this; }
            public Builder y(int y) { this.y = y; return this; }
            public Builder velocity(double velocity) { this.velocity = velocity; return this; }
            public Builder unnatural(boolean unnatural) { this.unnatural = unnatural; return this; }
            public Builder pattern(String pattern) { this.pattern = pattern; return this; }
            
            public MouseMovementInfo build() { return new MouseMovementInfo(this); }
        }
        
        // Getters
        public int getX() { return x; }
        public int getY() { return y; }
        public double getVelocity() { return velocity; }
        public boolean isUnnatural() { return unnatural; }
        public String getPattern() { return pattern; }
    }
    
    /**
     * Scroll event information for analysis
     */
    public static class ScrollInfo {
        private final int delta;
        private final boolean excessive;
        private final String direction;
        
        private ScrollInfo(Builder builder) {
            this.delta = builder.delta;
            this.excessive = builder.excessive;
            this.direction = builder.direction;
        }
        
        public static Builder builder() { return new Builder(); }
        
        public static class Builder {
            private int delta = 0;
            private boolean excessive = false;
            private String direction = "none";
            
            public Builder delta(int delta) { 
                this.delta = delta; 
                this.direction = delta > 0 ? "up" : (delta < 0 ? "down" : "none");
                return this; 
            }
            public Builder excessive(boolean excessive) { this.excessive = excessive; return this; }
            public Builder direction(String direction) { this.direction = direction; return this; }
            
            public ScrollInfo build() { return new ScrollInfo(this); }
        }
        
        // Getters
        public int getDelta() { return delta; }
        public boolean isExcessive() { return excessive; }
        public String getDirection() { return direction; }
    }

    /**
     * Represents an RDP action that can be monitored and controlled
     */
    public static class RdpAction {
        private final RdpActionType type;
        private final String target;
        private final String description;

        public RdpAction(RdpActionType type, String target, String description) {
            this.type = type;
            this.target = target;
            this.description = description;
        }

        public RdpActionType getType() { return type; }
        public String getTarget() { return target; }
        public String getDescription() { return description; }

        public enum RdpActionType {
            FILE_COPY_IN,
            FILE_COPY_OUT,
            FILE_DELETE,
            CLIPBOARD_ACCESS,
            DRIVE_REDIRECT,
            PROCESS_START,
            PROCESS_TERMINATE,
            REGISTRY_MODIFY,
            NETWORK_ACCESS,
            ADMIN_ACCESS,
            SERVICE_CONTROL,
            USER_MANAGEMENT,
            SECURITY_SETTINGS,
            SCREEN_CAPTURE,
            AUDIO_RECORD,
            PRINTER_ACCESS
        }
    }

    /**
     * Represents an RDP input event (mouse, keyboard)
     */
    public static class RdpInputEvent {
        private final RdpInputType type;
        private final String data;

        public RdpInputEvent(RdpInputType type, String data) {
            this.type = type;
            this.data = data;
        }

        public RdpInputType getType() { return type; }
        public String getData() { return data; }

        public enum RdpInputType {
            KEYBOARD,
            MOUSE_CLICK,
            MOUSE_MOVE,
            SCROLL
        }
    }
}