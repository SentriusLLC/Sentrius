package io.sentrius.sso.rdpproxy.service;

import io.sentrius.sso.automation.auditing.Trigger;
import io.sentrius.sso.automation.auditing.TriggerAction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Service that formats trigger responses for RDP protocol output.
 * Converts trigger responses to RDP-compatible messages.
 */
@Slf4j
@Service
public class RdpTerminalResponseService {

    // RDP message types for sending notifications
    private static final byte RDP_MESSAGE_INFO = 0x01;
    private static final byte RDP_MESSAGE_WARNING = 0x02;
    private static final byte RDP_MESSAGE_ERROR = 0x03;
    private static final byte RDP_MESSAGE_SUCCESS = 0x04;

    /**
     * Sends a formatted trigger response to the RDP connection
     */
    public void sendTriggerResponse(Trigger trigger, OutputStream out) throws IOException {
        if (trigger == null || trigger.getAction() == TriggerAction.NO_ACTION) {
            return;
        }

        byte[] message = formatTriggerMessage(trigger);
        if (message != null && message.length > 0) {
            out.write(message);
            out.flush();
            log.debug("Sent RDP trigger response: {} - {}", trigger.getAction(), trigger.getDescription());
        }
    }

    /**
     * Sends a plain message to the RDP connection
     */
    public void sendMessage(String message, OutputStream out) throws IOException {
        if (message == null || message.isEmpty()) {
            return;
        }

        byte[] rdpMessage = createRdpMessage(RDP_MESSAGE_INFO, message);
        out.write(rdpMessage);
        out.flush();
        log.debug("Sent RDP message: {}", message);
    }

    /**
     * Formats a trigger into an RDP-compatible message
     */
    public byte[] formatTriggerMessage(Trigger trigger) {
        if (trigger == null) {
            return new byte[0];
        }

        String messageText = getTriggerMessageText(trigger);
        byte messageType = getTriggerMessageType(trigger);

        return createRdpMessage(messageType, messageText);
    }

    /**
     * Gets the appropriate message text for a trigger
     */
    private String getTriggerMessageText(Trigger trigger) {
        String prefix = getTriggerPrefix(trigger.getAction());
        String description = trigger.getDescription() != null ? trigger.getDescription() : "Security policy triggered";
        
        return String.format("[Sentrius] %s: %s", prefix, description);
    }

    /**
     * Gets the appropriate message type for a trigger
     */
    private byte getTriggerMessageType(Trigger trigger) {
        switch (trigger.getAction()) {
            case DENY_ACTION:
                return RDP_MESSAGE_ERROR;
            case WARN_ACTION:
            case PROMPT_ACTION:
                return RDP_MESSAGE_WARNING;
            case APPROVE_ACTION:
            case RECORD_ACTION:
                return RDP_MESSAGE_SUCCESS;
            case ALERT_ACTION:
            case JIT_ACTION:
            case PERSISTENT_MESSAGE:
                return RDP_MESSAGE_INFO;
            default:
                return RDP_MESSAGE_INFO;
        }
    }

    /**
     * Gets the prefix text for different trigger actions
     */
    private String getTriggerPrefix(TriggerAction action) {
        switch (action) {
            case DENY_ACTION:
                return "BLOCKED";
            case WARN_ACTION:
                return "WARNING";
            case RECORD_ACTION:
                return "RECORDED";
            case ALERT_ACTION:
                return "ALERT";
            case APPROVE_ACTION:
                return "APPROVED";
            case PROMPT_ACTION:
                return "REQUIRES APPROVAL";
            case JIT_ACTION:
                return "JIT ACCESS REQUIRED";
            case PERSISTENT_MESSAGE:
                return "NOTICE";
            case LOG_ACTION:
                return "LOGGED";
            default:
                return "INFO";
        }
    }

    /**
     * Creates an RDP-compatible message packet
     * This is a simplified implementation - in a real RDP implementation,
     * this would create proper RDP protocol messages
     */
    private byte[] createRdpMessage(byte messageType, String text) {
        try {
            byte[] textBytes = text.getBytes(StandardCharsets.UTF_8);
            
            // Create a simple message format:
            // [4 bytes: message length] [1 byte: message type] [text bytes]
            ByteBuffer buffer = ByteBuffer.allocate(4 + 1 + textBytes.length);
            buffer.putInt(1 + textBytes.length); // Length of type + text
            buffer.put(messageType);
            buffer.put(textBytes);
            
            return buffer.array();
            
        } catch (Exception e) {
            log.error("Error creating RDP message", e);
            return new byte[0];
        }
    }

    /**
     * Sends an RDP notification for file transfer operations
     */
    public void sendFileTransferNotification(String filename, String operation, boolean allowed, OutputStream out) throws IOException {
        String message = String.format("[Sentrius] File %s: %s - %s", 
            operation, filename, allowed ? "ALLOWED" : "BLOCKED");
        
        byte messageType = allowed ? RDP_MESSAGE_INFO : RDP_MESSAGE_ERROR;
        byte[] rdpMessage = createRdpMessage(messageType, message);
        
        out.write(rdpMessage);
        out.flush();
        log.info("RDP file transfer notification: {}", message);
    }

    /**
     * Sends an RDP notification for clipboard operations
     */
    public void sendClipboardNotification(String operation, boolean allowed, OutputStream out) throws IOException {
        String message = String.format("[Sentrius] Clipboard %s - %s", 
            operation, allowed ? "ALLOWED" : "BLOCKED");
        
        byte messageType = allowed ? RDP_MESSAGE_INFO : RDP_MESSAGE_ERROR;
        byte[] rdpMessage = createRdpMessage(messageType, message);
        
        out.write(rdpMessage);
        out.flush();
        log.info("RDP clipboard notification: {}", message);
    }

    /**
     * Sends an RDP notification for drive redirection operations
     */
    public void sendDriveRedirectionNotification(String drive, boolean allowed, OutputStream out) throws IOException {
        String message = String.format("[Sentrius] Drive redirection %s - %s", 
            drive, allowed ? "ALLOWED" : "BLOCKED");
        
        byte messageType = allowed ? RDP_MESSAGE_INFO : RDP_MESSAGE_ERROR;
        byte[] rdpMessage = createRdpMessage(messageType, message);
        
        out.write(rdpMessage);
        out.flush();
        log.info("RDP drive redirection notification: {}", message);
    }

    /**
     * Sends a session monitoring notification
     */
    public void sendSessionMonitoringNotification(String event, OutputStream out) throws IOException {
        String message = String.format("[Sentrius] Session Event: %s", event);
        
        byte[] rdpMessage = createRdpMessage(RDP_MESSAGE_INFO, message);
        out.write(rdpMessage);
        out.flush();
        log.info("RDP session monitoring: {}", message);
    }
}