package io.sentrius.sso.sshproxy.service;

import io.sentrius.sso.automation.auditing.Trigger;
import io.sentrius.sso.automation.auditing.TriggerAction;
import io.sentrius.sso.core.model.ConnectedSystem;
import io.sentrius.sso.core.services.terminal.SessionTrackingService;
import io.sentrius.sso.protobuf.Session;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Service that processes SSH commands and applies Sentrius safeguards.
 * Integrates with existing SessionTrackingService and trigger system.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SshCommandProcessor {

    private final SessionTrackingService sessionTrackingService;
    private final InlineTerminalResponseService terminalResponseService;

    /**
     * Processes a command through the trigger system and returns whether it should be executed
     */
    public boolean processCommand(ConnectedSystem connectedSystem, String command, OutputStream terminalOutput) {
        try {
            // For now, implement basic command filtering logic
            // TODO: Integrate with actual trigger system once ConnectedSystem is properly initialized
            
            // Basic command filtering for demonstration
            if (isDangerousCommand(command)) {
                Trigger denyTrigger = new Trigger(TriggerAction.DENY_ACTION, "Command blocked by security policy");
                return handleTrigger(denyTrigger, terminalOutput, command, false);
            }
            
            if (isWarningCommand(command)) {
                Trigger warnTrigger = new Trigger(TriggerAction.WARN_ACTION, "This command requires caution");
                handleTrigger(warnTrigger, terminalOutput, command, false);
                return true; // Allow but warn
            }

            // Command is allowed
            return true;

        } catch (Exception e) {
            log.error("Error processing command through trigger system", e);
            try {
                terminalResponseService.sendMessage("\r\nError: Command processing failed\r\n", terminalOutput);
            } catch (IOException ioException) {
                log.error("Error sending error message to terminal", ioException);
            }
            return false;
        }
    }
    
    /**
     * Check if command is considered dangerous and should be blocked
     */
    private boolean isDangerousCommand(String command) {
        String cmd = command.trim().toLowerCase();
        // Basic dangerous command detection
        return cmd.startsWith("rm -rf") || 
               cmd.startsWith("dd if=") || 
               cmd.contains("format") ||
               cmd.startsWith("sudo rm") ||
               cmd.contains("shutdown") ||
               cmd.contains("reboot");
    }
    
    /**
     * Check if command should trigger a warning
     */
    private boolean isWarningCommand(String command) {
        String cmd = command.trim().toLowerCase();
        return cmd.startsWith("sudo") ||
               cmd.startsWith("su ") ||
               cmd.contains("passwd") ||
               cmd.startsWith("chmod 777") ||
               cmd.startsWith("chown");
    }

    /**
     * Handles a trigger by sending appropriate response to terminal and returning execution decision
     */
    private boolean handleTrigger(Trigger trigger, OutputStream terminalOutput, String command, boolean isSessionTrigger) {
        try {
            switch (trigger.getAction()) {
                case DENY_ACTION:
                    terminalResponseService.sendTriggerResponse(trigger, terminalOutput);
                    return false; // Block command execution

                case WARN_ACTION:
                    terminalResponseService.sendTriggerResponse(trigger, terminalOutput);
                    return true; // Allow command but with warning

                case RECORD_ACTION:
                    terminalResponseService.sendTriggerResponse(trigger, terminalOutput);
                    return true; // Allow command and record

                case ALERT_ACTION:
                    terminalResponseService.sendTriggerResponse(trigger, terminalOutput);
                    return true; // Allow command but send alert

                case APPROVE_ACTION:
                    terminalResponseService.sendTriggerResponse(trigger, terminalOutput);
                    return true; // Command approved

                case PROMPT_ACTION:
                    // For now, treat prompt as warning in terminal mode
                    // In future, could implement interactive prompting
                    terminalResponseService.sendTriggerResponse(trigger, terminalOutput);
                    return true;

                case JIT_ACTION:
                    terminalResponseService.sendTriggerResponse(trigger, terminalOutput);
                    // For now, treat JIT as warning. In future, could integrate with JIT system
                    return true;

                case PERSISTENT_MESSAGE:
                    terminalResponseService.sendTriggerResponse(trigger, terminalOutput);
                    return true; // Allow command with message

                case LOG_ACTION:
                    // Log action doesn't display message, just logs
                    return true;

                case NO_ACTION:
                default:
                    return true; // Allow command
            }
        } catch (IOException e) {
            log.error("Error sending trigger response to terminal", e);
            return false; // Block command on error
        }
    }

    /**
     * Processes keycode input (for special keys like Ctrl+C, arrows, etc.)
     */
    public boolean processKeycode(ConnectedSystem connectedSystem, int keyCode, OutputStream terminalOutput) {
        try {
            // For now, allow most keycodes through
            // TODO: Implement actual keycode filtering when needed
            return true;

        } catch (Exception e) {
            log.error("Error processing keycode through trigger system", e);
            return false;
        }
    }
}