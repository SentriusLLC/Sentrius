package io.sentrius.sso.sshproxy.handler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import io.sentrius.sso.automation.auditing.BaseAccessTokenAuditor;
import io.sentrius.sso.automation.auditing.Trigger;
import io.sentrius.sso.core.integrations.ssh.DataSession;
import io.sentrius.sso.core.model.ConnectedSystem;
import io.sentrius.sso.core.services.SshListenerService;
import io.sentrius.sso.protobuf.Session;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;

@Slf4j
public class ResponseServiceSession implements DataSession {

    private final String sessionId;
    private final InputStream in;
    private final OutputStream out;
    private final BaseAccessTokenAuditor auditor;
    private ConnectedSystem connectedSystem;


    private static final String ANSI_RED = "\u001B[31m";
    private static final String ANSI_YELLOW = "\u001B[33m";
    private static final String ANSI_GREEN = "\u001B[32m";
    private static final String ANSI_BLUE = "\u001B[34m";
    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_BOLD = "\u001B[1m";

    public ResponseServiceSession(ConnectedSystem connectedSystem, InputStream in,
                                  OutputStream out) {
        this.sessionId = connectedSystem.getWebsocketSessionId();
        this.connectedSystem = connectedSystem;
        this.in = in;
        this.out = out;
        this.auditor = connectedSystem.getTerminalAuditor();
    }
    @Override
    public String getId() {
        return sessionId;
    }

    @Override
    public boolean isOpen() {
        return true;
    }

    @Override
    public void sendMessage(WebSocketMessage<?> message) throws IOException {
        log.info("Received message for session {}: {}", sessionId, message.getPayload());
        if (message instanceof TextMessage){
            byte[] messageBytes = Base64.getDecoder().decode(((TextMessage)message).getPayload());
            String terminalMessage = new String(messageBytes);

            Session.TerminalMessage auditLog =
                Session.TerminalMessage.parseFrom(messageBytes);

            var trigger = auditLog.getTrigger();
            String msg = "";
            switch (trigger.getAction()) {
                case DENY_ACTION:
                    msg = formatDenyMessage(trigger, auditLog);
                    connectedSystem.getCommander().write(SshListenerService.keyMap.get(3));
                    connectedSystem.getTerminalAuditor().clear(0); // clear in case
                    break;
                case WARN_ACTION:
                    msg = formatWarnMessage(trigger, auditLog);

                    break;
                case PROMPT_ACTION:
                    msg = formatPromptMessage(trigger, auditLog);
                    break;
                case JIT_ACTION:
                    msg = formatJitMessage(trigger, auditLog);
                    break;
                case RECORD_ACTION:
                    msg = formatRecordMessage(trigger, auditLog);
                    break;

                case PERSISTENT_MESSAGE:
                    msg = formatPersistentMessage(trigger, auditLog);
                    break;
                case APPROVE_ACTION:
                    msg = formatApproveMessage(trigger, auditLog);
                    break;
                case LOG_ACTION:
                    return ; // Log actions don't show user messages
                case ALERT_ACTION:
                    msg = formatAlertMessage(trigger, auditLog);
                    break;
                default: {
                    msg = auditLog.getCommand();
                    break;
                }
            };

            log.info("Sending terminal message to session {}: ",
                msg);
            out.write(msg.getBytes(StandardCharsets.UTF_8));
            out.flush();



        }
    }


    private String formatDenyMessage(Session.Trigger trigger, Session.TerminalMessage auditLog) {
            StringBuilder sb = new StringBuilder();
            sb.append("\r\n");
            sb.append(ANSI_RED).append(ANSI_BOLD).append("⚠ COMMAND BLOCKED ⚠").append(ANSI_RESET).append("\r\n");
            sb.append(ANSI_RED).append("Reason: ").append(trigger.getDescription()).append(ANSI_RESET).append("\r\n");
            sb.append(ANSI_RED).append("This command has been blocked by security policy.").append(ANSI_RESET).append("\r\n");
            sb.append("\r\n");
            return sb.toString();
        }

        private String formatWarnMessage(Session.Trigger trigger, Session.TerminalMessage auditLog) {
            StringBuilder sb = new StringBuilder();
            sb.append("\r\n");
            sb.append(ANSI_YELLOW).append(ANSI_BOLD).append("⚠ WARNING ⚠").append(ANSI_RESET).append("\r\n");
            sb.append(ANSI_YELLOW).append("Warning: ").append(trigger.getDescription()).append(ANSI_RESET).append("\r\n");
            sb.append("\r\n");
            return sb.toString();
        }

        private String formatPromptMessage(Session.Trigger trigger, Session.TerminalMessage auditLog) {
            StringBuilder sb = new StringBuilder();
            sb.append("\r\n");
            sb.append(ANSI_BLUE).append(ANSI_BOLD).append("📝 PROMPT").append(ANSI_RESET).append("\r\n");
            sb.append(ANSI_BLUE).append(trigger.getDescription()).append(ANSI_RESET).append("\r\n");
            if (!auditLog.getCommand().isEmpty()) {
                sb.append(ANSI_BLUE).append(auditLog.getCommand()).append(" (y/n): ").append(ANSI_RESET);
            }
            return sb.toString();
        }

        private String formatJitMessage(Session.Trigger trigger, Session.TerminalMessage auditLog) {
            StringBuilder sb = new StringBuilder();
            sb.append("\r\n");
            sb.append(ANSI_YELLOW).append(ANSI_BOLD).append("🔐 JUST-IN-TIME ACCESS").append(ANSI_RESET).append("\r\n");
            sb.append(ANSI_YELLOW).append("Reason: ").append(trigger.getDescription()).append(ANSI_RESET).append("\r\n");
            sb.append(ANSI_YELLOW).append("Requesting access...").append(ANSI_RESET).append("\r\n");
            sb.append("\r\n");
            return sb.toString();
        }

        private String formatRecordMessage(Session.Trigger trigger, Session.TerminalMessage auditLog) {
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

        private String formatPersistentMessage(Session.Trigger trigger, Session.TerminalMessage auditLog) {
            StringBuilder sb = new StringBuilder();
            sb.append("\r\n");
            sb.append(ANSI_BLUE).append(ANSI_BOLD).append("💬 MESSAGE").append(ANSI_RESET).append("\r\n");
            sb.append(ANSI_BLUE).append(trigger.getDescription()).append(ANSI_RESET).append("\r\n");
            sb.append("\r\n");
            return sb.toString();
        }

        private String formatApproveMessage(Session.Trigger trigger, Session.TerminalMessage auditLog) {
            StringBuilder sb = new StringBuilder();
            sb.append("\r\n");
            sb.append(ANSI_GREEN).append(ANSI_BOLD).append("✅ APPROVED").append(ANSI_RESET).append("\r\n");
            sb.append(ANSI_GREEN).append(trigger.getDescription()).append(ANSI_RESET).append("\r\n");
            sb.append("\r\n");
            return sb.toString();
        }

        private String formatAlertMessage(Session.Trigger trigger, Session.TerminalMessage auditLog) {
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
}
