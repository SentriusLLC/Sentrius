package io.sentrius.sso.core.services;

import io.sentrius.sso.automation.auditing.Trigger;
import io.sentrius.sso.automation.auditing.TriggerAction;
import io.sentrius.sso.core.integrations.ssh.DataSession;
import io.sentrius.sso.core.model.ConnectedSystem;
import io.sentrius.sso.core.services.security.CryptoService;
import io.sentrius.sso.core.services.terminal.SessionTrackingService;
import io.sentrius.sso.protobuf.Session;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * RDP listener service that monitors and manages RDP sessions.
 * Similar to SshListenerService but adapted for RDP protocol.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RdpListenerService {

    private final SessionTrackingService sessionTrackingService;
    private final CryptoService cryptoService;

    @Qualifier("rdpTaskExecutor") // Use RDP-specific executor
    private final Executor taskExecutor;

    private final ConcurrentMap<String, DataSession> activeSessions = new ConcurrentHashMap<>();

    public void startAuditingSession(String terminalSessionId, DataSession session) throws GeneralSecurityException {
        var sessionIdStr = cryptoService.decrypt(terminalSessionId);
        var sessionIdLong = Long.parseLong(sessionIdStr);

        log.info("Starting to audit RDP session: {}", terminalSessionId);
        activeSessions.putIfAbsent(terminalSessionId, session);
    }

    public void endAuditingSession(String terminalSessionId) throws GeneralSecurityException {
        log.info("Ending RDP audit session: {}", terminalSessionId);
        activeSessions.remove(terminalSessionId);
    }

    public void startListeningToRdpServer(String terminalSessionId, DataSession session) throws GeneralSecurityException {
        var sessionIdStr = cryptoService.decrypt(terminalSessionId);
        var sessionIdLong = Long.parseLong(sessionIdStr);

        var connectedSystem = sessionTrackingService.getConnectedSession(sessionIdLong);

        log.info("Starting to listen to RDP server for session: {}", terminalSessionId);

        activeSessions.putIfAbsent(terminalSessionId, session);
        connectedSystem.setWebsocketSessionId(session.getId());

        taskExecutor.execute(() -> {
            log.info("Listening to RDP server for session: {}", terminalSessionId);
            while (!Thread.currentThread().isInterrupted() && 
                   activeSessions.get(terminalSessionId) != null &&
                   !connectedSystem.getSession().getClosed()) {
                try {
                    // Logic for receiving data from RDP server
                    var rdpData = sessionTrackingService.getOutput(connectedSystem, 1L, TimeUnit.SECONDS,
                        output -> (!connectedSystem.getSession().getClosed() && 
                                 (activeSessions.get(terminalSessionId) != null && 
                                  activeSessions.get(terminalSessionId).isOpen())));

                    // Send data to the specific terminal session
                    if (rdpData != null) {
                        for (Session.TerminalMessage terminalMessage : rdpData) {
                            if (terminalMessage.getTrigger() == null) {
                                sendToTerminalSession(terminalSessionId, connectedSystem, terminalMessage);
                            }
                        }
                        for (Session.TerminalMessage terminalMessage : rdpData) {
                            if (terminalMessage.getTrigger() != null) {
                                sendToTerminalSession(terminalSessionId, connectedSystem, terminalMessage);
                            }
                        }
                    } else {
                        log.trace("No RDP data to return");
                    }

                } catch (Exception e) {
                    log.error("Error while listening to RDP server: ", e);
                    Thread.currentThread().interrupt(); // Ensure the thread can exit cleanly on exception
                }
            }
            log.trace("***Leaving RDP thread");
        });
    }

    public void stopListeningToRdpServer(ConnectedSystem connectedSystem) {
        sessionTrackingService.closeSession(connectedSystem);
    }

    private Session.TerminalMessage getTrigger(Trigger trigger) {
        var terminalMessage = Session.TerminalMessage.newBuilder();
        if (trigger.getAsk() != null) {
            terminalMessage.setType(Session.MessageType.PROMPT_DATA);
        } else {
            terminalMessage.setType(Session.MessageType.USER_DATA);
        }
        Session.Trigger.Builder triggerBuilder = Session.Trigger.newBuilder();
        switch(trigger.getAction()){
            case DENY_ACTION:
                triggerBuilder.setAction(Session.TriggerAction.DENY_ACTION);
                break;
            case JIT_ACTION:
                triggerBuilder.setAction(Session.TriggerAction.JIT_ACTION);
                break;
            case RECORD_ACTION:
                triggerBuilder.setAction(Session.TriggerAction.RECORD_ACTION);
                break;
            case APPROVE_ACTION:
                triggerBuilder.setAction(Session.TriggerAction.APPROVE_ACTION);
                break;
            case WARN_ACTION:
                triggerBuilder.setAction(Session.TriggerAction.WARN_ACTION);
                break;
            case PERSISTENT_MESSAGE:
                triggerBuilder.setAction(Session.TriggerAction.PERSISTENT_MESSAGE);
                break;
            case PROMPT_ACTION:
                triggerBuilder.setAction(Session.TriggerAction.PROMPT_ACTION);
                break;
            default:
                break;
        }
        triggerBuilder.setDescription(trigger.getDescription().isEmpty() ? "" : trigger.getDescription());
        terminalMessage.setTrigger(triggerBuilder.build());
        return terminalMessage.build();
    }

    @Async
    public void sendToTerminalSession(String terminalSessionId, ConnectedSystem connectedSystem,
                                      Session.TerminalMessage rdpData) {
        DataSession session = activeSessions.get(terminalSessionId);
        log.info("Sending RDP message to session: {}", terminalSessionId);
        if (session != null && session.isOpen()) {
            try {
                byte[] messageBytes = rdpData.toByteArray();
                String base64Message = Base64.getEncoder().encodeToString(messageBytes);
                log.trace("Sending RDP message to session: {}", rdpData);
                session.sendMessage(new TextMessage(base64Message));
            } catch (IOException e) {
                log.error("Error sending RDP data to terminal session: " + terminalSessionId, e);
            }
        } else {
            log.debug("RDP Session {} is not available or closed", terminalSessionId);
        }
    }

    public void processTerminalMessage(
        String sessionId, ConnectedSystem connectedSystem, String message) {
        
        try {
            log.debug("Processing RDP terminal message for session: {}", sessionId);
            
            // Process RDP-specific commands and data
            if (message.startsWith("RDP_COMMAND:")) {
                handleRdpCommand(connectedSystem, message.substring(12));
            } else {
                // Regular RDP data
                handleRdpData(connectedSystem, message);
            }
            
        } catch (Exception e) {
            log.error("Error processing RDP terminal message", e);
        }
    }

    private void handleRdpCommand(ConnectedSystem connectedSystem, String command) {
        log.info("Handling RDP command: {} for session: {}", command, connectedSystem.getSession().getId());
        // TODO: Implement RDP-specific command handling
    }

    private void handleRdpData(ConnectedSystem connectedSystem, String data) {
        log.debug("Handling RDP data for session: {}", connectedSystem.getSession().getId());
        // TODO: Implement RDP data processing and forwarding
    }

    public void removeSession(String sessionId) {
        log.trace("Removing RDP session: {}", sessionId);
        activeSessions.remove(sessionId);
    }
}