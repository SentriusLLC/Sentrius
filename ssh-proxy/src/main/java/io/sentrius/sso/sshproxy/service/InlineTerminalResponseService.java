package io.sentrius.sso.sshproxy.service;

import io.sentrius.sso.automation.auditing.Trigger;
import io.sentrius.sso.automation.auditing.TriggerAction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Service that formats trigger responses for inline terminal output.
 * Converts WebSocket-style trigger responses to terminal-friendly text.
 */
@Slf4j
@Service
public class InlineTerminalResponseService {

    private static final String ANSI_RED = "\u001B[31m";
    private static final String ANSI_YELLOW = "\u001B[33m";
    private static final String ANSI_GREEN = "\u001B[32m";
    private static final String ANSI_BLUE = "\u001B[34m";
    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_BOLD = "\u001B[1m";

    /**
     * Sends a formatted trigger response to the SSH terminal
     */
    public void sendTriggerResponse(Trigger trigger, OutputStream out) throws IOException {
        if (trigger == null || trigger.getAction() == TriggerAction.NO_ACTION) {
            return;
        }

        String message = formatTriggerMessage(trigger);
        if (message != null && !message.isEmpty()) {
            out.write(message.getBytes());
            out.flush();
        }
    }

    /**
     * Formats a trigger into a terminal-friendly message
     */
    public String formatTriggerMessage(Trigger trigger) {
        if (trigger == null) {
            return "";
        }

        switch (trigger.getAction()) {
            case DENY_ACTION:
                return formatDenyMessage(trigger);
            case WARN_ACTION:
                return formatWarnMessage(trigger);
            case PROMPT_ACTION:
                return formatPromptMessage(trigger);
            case JIT_ACTION:
                return formatJitMessage(trigger);
            case RECORD_ACTION:
                return formatRecordMessage(trigger);
            case PERSISTENT_MESSAGE:
                return formatPersistentMessage(trigger);
            case APPROVE_ACTION:
                return formatApproveMessage(trigger);
            case LOG_ACTION:
                return ""; // Log actions don't show user messages
            case ALERT_ACTION:
                return formatAlertMessage(trigger);
            default:
                return "";
        }
    }

    private String formatDenyMessage(Trigger trigger) {
        StringBuilder sb = new StringBuilder();
        sb.append("\r\n");
        sb.append(ANSI_RED).append(ANSI_BOLD).append("⚠ COMMAND BLOCKED ⚠").append(ANSI_RESET).append("\r\n");
        sb.append(ANSI_RED).append("Reason: ").append(trigger.getDescription()).append(ANSI_RESET).append("\r\n");
        sb.append(ANSI_RED).append("This command has been blocked by security policy.").append(ANSI_RESET).append("\r\n");
        sb.append("\r\n");
        return sb.toString();
    }

    private String formatWarnMessage(Trigger trigger) {
        StringBuilder sb = new StringBuilder();
        sb.append("\r\n");
        sb.append(ANSI_YELLOW).append(ANSI_BOLD).append("⚠ WARNING ⚠").append(ANSI_RESET).append("\r\n");
        sb.append(ANSI_YELLOW).append("Warning: ").append(trigger.getDescription()).append(ANSI_RESET).append("\r\n");
        sb.append("\r\n");
        return sb.toString();
    }

    private String formatPromptMessage(Trigger trigger) {
        StringBuilder sb = new StringBuilder();
        sb.append("\r\n");
        sb.append(ANSI_BLUE).append(ANSI_BOLD).append("📝 PROMPT").append(ANSI_RESET).append("\r\n");
        sb.append(ANSI_BLUE).append(trigger.getDescription()).append(ANSI_RESET).append("\r\n");
        if (trigger.getAsk() != null && !trigger.getAsk().isEmpty()) {
            sb.append(ANSI_BLUE).append(trigger.getAsk()).append(" (y/n): ").append(ANSI_RESET);
        }
        return sb.toString();
    }

    private String formatJitMessage(Trigger trigger) {
        StringBuilder sb = new StringBuilder();
        sb.append("\r\n");
        sb.append(ANSI_YELLOW).append(ANSI_BOLD).append("🔐 JUST-IN-TIME ACCESS").append(ANSI_RESET).append("\r\n");
        sb.append(ANSI_YELLOW).append("Reason: ").append(trigger.getDescription()).append(ANSI_RESET).append("\r\n");
        sb.append(ANSI_YELLOW).append("Requesting elevated access...").append(ANSI_RESET).append("\r\n");
        sb.append("\r\n");
        return sb.toString();
    }

    private String formatRecordMessage(Trigger trigger) {
        StringBuilder sb = new StringBuilder();
        sb.append("\r\n");
        sb.append(ANSI_GREEN).append(ANSI_BOLD).append("📹 RECORDING").append(ANSI_RESET).append("\r\n");
        sb.append(ANSI_GREEN).append("This session is being recorded for audit purposes.").append(ANSI_RESET).append("\r\n");
        if (!trigger.getDescription().isEmpty()) {
            sb.append(ANSI_GREEN).append("Reason: ").append(trigger.getDescription()).append(ANSI_RESET).append("\r\n");
        }
        sb.append("\r\n");
        return sb.toString();
    }

    private String formatPersistentMessage(Trigger trigger) {
        StringBuilder sb = new StringBuilder();
        sb.append("\r\n");
        sb.append(ANSI_BLUE).append(ANSI_BOLD).append("💬 MESSAGE").append(ANSI_RESET).append("\r\n");
        sb.append(ANSI_BLUE).append(trigger.getDescription()).append(ANSI_RESET).append("\r\n");
        sb.append("\r\n");
        return sb.toString();
    }

    private String formatApproveMessage(Trigger trigger) {
        StringBuilder sb = new StringBuilder();
        sb.append("\r\n");
        sb.append(ANSI_GREEN).append(ANSI_BOLD).append("✅ APPROVED").append(ANSI_RESET).append("\r\n");
        sb.append(ANSI_GREEN).append(trigger.getDescription()).append(ANSI_RESET).append("\r\n");
        sb.append("\r\n");
        return sb.toString();
    }

    private String formatAlertMessage(Trigger trigger) {
        StringBuilder sb = new StringBuilder();
        sb.append("\r\n");
        sb.append(ANSI_RED).append(ANSI_BOLD).append("🚨 ALERT").append(ANSI_RESET).append("\r\n");
        sb.append(ANSI_RED).append(trigger.getDescription()).append(ANSI_RESET).append("\r\n");
        sb.append("\r\n");
        return sb.toString();
    }

    /**
     * Sends a plain message to the terminal
     */
    public void sendMessage(String message, OutputStream out) throws IOException {
        if (message != null && !message.isEmpty()) {
            out.write(message.getBytes());
            out.flush();
        }
    }

    /**
     * Clears the current line in the terminal
     */
    public void clearCurrentLine(OutputStream out) throws IOException {
        out.write("\r\033[K".getBytes());
        out.flush();
    }
}