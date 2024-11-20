package io.sentrius.sso.websocket;
import io.sentrius.sso.automation.auditing.Trigger;
import io.sentrius.sso.automation.auditing.TriggerAction;
import io.sentrius.sso.protobuf.Session;
import io.sentrius.sso.core.model.ConnectedSystem;
import io.sentrius.sso.core.security.service.CryptoService;
import io.sentrius.sso.core.services.terminal.SessionTrackingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class SshListenerService {

    private final SessionTrackingService sessionTrackingService;
    private final CryptoService cryptoService;

    @Qualifier("taskExecutor") // Specify the custom task executor to use
    private final Executor taskExecutor;

    private final ConcurrentMap<String, WebSocketSession> activeSessions = new ConcurrentHashMap<>();

    public void startAuditingSession(String terminalSessionId, WebSocketSession session) throws GeneralSecurityException {

        var sessionIdStr = cryptoService.decrypt(terminalSessionId);
        var sessionIdLong = Long.parseLong(sessionIdStr);
        var connectedSystem = sessionTrackingService.getConnectedSession(sessionIdLong);
        if (null != connectedSystem ) {
            connectedSystem.setWebsocketListenerSessionId(session.getId());
        }
    }

    public void endAuditingSession(String terminalSessionId) throws GeneralSecurityException {

        var sessionIdStr = cryptoService.decrypt(terminalSessionId);
        var sessionIdLong = Long.parseLong(sessionIdStr);
        var connectedSystem = sessionTrackingService.getConnectedSession(sessionIdLong);
        if (null != connectedSystem ) {
            connectedSystem.setWebsocketListenerSessionId("");
        }
    }

    public void startListeningToSshServer(String terminalSessionId, WebSocketSession session) throws GeneralSecurityException {

        var sessionIdStr = cryptoService.decrypt(terminalSessionId);
        var sessionIdLong = Long.parseLong(sessionIdStr);


        var connectedSystem = sessionTrackingService.getConnectedSession(sessionIdLong);

        log.info("Starting to listen to SSH server for session: {}", terminalSessionId);

        activeSessions.putIfAbsent(terminalSessionId, session);

        connectedSystem.setWebsocketSessionId(session.getId());



        taskExecutor.execute(() -> {
            while (!Thread.currentThread().isInterrupted() && activeSessions.get(terminalSessionId) != null &&
                !connectedSystem.getSession().getClosed()) {
                try {
                    // logic for receiving data from SSH server
                    var sshData = sessionTrackingService.getOutput(connectedSystem, 1L, TimeUnit.SECONDS,
                        output -> (!connectedSystem.getSession().getClosed() && (null != activeSessions.get(terminalSessionId) && activeSessions.get(terminalSessionId).isOpen())));

                    // Send data to the specific terminal session
                    if (null != sshData ) {
                        for(Session.TerminalMessage terminalMessage : sshData){
                            if (terminalMessage.getTrigger() == null) {
                                sendToTerminalSession(terminalSessionId, connectedSystem, terminalMessage);
                            }
                        }
                        for(Session.TerminalMessage terminalMessage : sshData){
                            if (terminalMessage.getTrigger() != null) {
                                sendToTerminalSession(terminalSessionId, connectedSystem, terminalMessage);
                            }
                        }
                    }else {
                        log.trace("No data to return");
                    }



                } catch (Exception e) {
                    log.error("Error while listening to SSH server: ", e);
                    Thread.currentThread().interrupt(); // Ensure the thread can exit cleanly on exception
                }
            };
            log.trace("***L:eaving thread");
        });
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
                                      Session.TerminalMessage sshData) {
        WebSocketSession session = activeSessions.get(terminalSessionId);
        log.trace("Sending message to session: {}", terminalSessionId);
        if (session != null && session.isOpen()) {
            try {

                byte[] messageBytes = sshData.toByteArray();
                String base64Message = Base64.getEncoder().encodeToString(messageBytes);
                log.trace("Sending message to session: {}", sshData);
                session.sendMessage(new TextMessage(base64Message));
                log.trace("sent message to session: {}", terminalSessionId);
            } catch (Exception e) {
                log.error("Error sending message to session {}: ", terminalSessionId, e);
            }
        } else {
            log.warn("Session not found or already closed: {} , {}", terminalSessionId, (session == null));
        }
    }

    public void processTerminalMessage(
        ConnectedSystem terminalSessionId, Session.TerminalMessage terminalMessage) {
        if (!terminalSessionId.getSession().getClosed() && terminalMessage.getType() != Session.MessageType.HEARTBEAT) {

            try {
                String command = terminalMessage.getCommand();

                log.info("Got " + command);

                Integer keyCode = null;
                Double keyCodeDbl = terminalMessage.getKeycode();
                if (keyCodeDbl != null) {
                    keyCode = keyCodeDbl.intValue();
                }

                if (terminalSessionId.getTerminalAuditor().getSessionTrigger().getAction() != TriggerAction.NO_ACTION &&
                    terminalSessionId.getTerminalAuditor().getSessionTrigger().getAction() != TriggerAction.WARN_ACTION &&
                    terminalSessionId.getTerminalAuditor().getSessionTrigger().getAction() != TriggerAction.PROMPT_ACTION &&
                    terminalSessionId.getTerminalAuditor().getSessionTrigger().getAction() != TriggerAction.PERSISTENT_MESSAGE) {
                    log.info("Session Trigger action is not NO_ACTION");
                    return;
                }
                    var schSession = terminalSessionId.getSession();

                    if (terminalSessionId.getTerminalAuditor().getCurrentTrigger().getAction()
                        == TriggerAction.DENY_ACTION) {
                        sessionTrackingService.addTrigger(terminalSessionId, terminalSessionId.getTerminalAuditor().getCurrentTrigger());
                    }
                    if (keyCode != null && keyCode != -1) {
                        log.info("key code isn't null " + keyCode );
                        if (keyMap.containsKey(keyCode)) {

                            if (keyCode == 13
                                && terminalSessionId.getTerminalAuditor().getCurrentTrigger().getAction()
                                == TriggerAction.DENY_ACTION
                                || terminalSessionId.getTerminalAuditor().getCurrentTrigger().getAction()
                                == TriggerAction.JIT_ACTION
                                || terminalSessionId.getTerminalAuditor().getCurrentTrigger().getAction()
                                == TriggerAction.RECORD_ACTION) {
                                // don't allow command to be processed
                                terminalSessionId.getTerminalAuditor().keycode(keyCode);
                                if (terminalSessionId.getTerminalAuditor().getCurrentTrigger().getAction()
                                    == TriggerAction.DENY_ACTION
                                    || terminalSessionId.getTerminalAuditor().getCurrentTrigger().getAction()
                                    == TriggerAction.JIT_ACTION
                                    || terminalSessionId.getTerminalAuditor().getCurrentTrigger().getAction()
                                    == TriggerAction.RECORD_ACTION) {
                                    keyCode = 67;
                                }
                                terminalSessionId.getCommander().write(keyMap.get(keyCode));
                                terminalSessionId.getTerminalAuditor().clear(0); // clear in case
                            } else {
                                if (terminalSessionId.getTerminalAuditor().keycode(keyCode)
                                    == TriggerAction.RECORD_ACTION) {
                                    keyCode = 67;
                                }
                                terminalSessionId.getCommander().write(keyMap.get(keyCode));
                                terminalSessionId.getTerminalAuditor().keycode(keyCode);
                            }
                        } else {
                        }

                    } else {
                        log.info("Appending");

                            terminalSessionId.getTerminalAuditor().append(command);
                            terminalSessionId.getCommander().print(command);



                }

                // update timeout
                // AuthUtil.setTimeout(g);
            } catch (IllegalStateException  | IOException ex) {
                log.error(ex.toString(), ex);
            }
        } else if (terminalMessage.getType() == Session.MessageType.HEARTBEAT) {
            // Handle heartbeat message
            log.trace("received heartbedat");
        }
    }


    public void stopListeningToSshServer(ConnectedSystem connectedSystem) {
        sessionTrackingService.closeSession(connectedSystem);
    }

    /** Maps key press events to the ascii values */
    static Map<Integer, byte[]> keyMap = new HashMap<>();

    static {
        // ESC
        keyMap.put(27, new byte[] {(byte) 0x1b});
        // ENTER
        keyMap.put(13, new byte[] {(byte) 0x0d});
        // LEFT
        keyMap.put(37, new byte[] {(byte) 0x1b, (byte) 0x4f, (byte) 0x44});
        // UP
        keyMap.put(38, new byte[] {(byte) 0x1b, (byte) 0x4f, (byte) 0x41});
        // RIGHT
        keyMap.put(39, new byte[] {(byte) 0x1b, (byte) 0x4f, (byte) 0x43});
        // DOWN
        keyMap.put(40, new byte[] {(byte) 0x1b, (byte) 0x4f, (byte) 0x42});
        // BS
        keyMap.put(8, new byte[] {(byte) 0x7f});
        // TAB
        keyMap.put(9, new byte[] {(byte) 0x09});
        // CTR
        keyMap.put(17, new byte[] {});
        // DEL
        keyMap.put(46, "\033[3~".getBytes());
        // CTR-A
        keyMap.put(65, new byte[] {(byte) 0x01});
        // CTR-B
        keyMap.put(66, new byte[] {(byte) 0x02});
        // CTR-C
        keyMap.put(67, new byte[] {(byte) 0x03});
        // CTR-D
        keyMap.put(68, new byte[] {(byte) 0x04});
        // CTR-E
        keyMap.put(69, new byte[] {(byte) 0x05});
        // CTR-F
        keyMap.put(70, new byte[] {(byte) 0x06});
        // CTR-G
        keyMap.put(71, new byte[] {(byte) 0x07});
        // CTR-H
        keyMap.put(72, new byte[] {(byte) 0x08});
        // CTR-I
        keyMap.put(73, new byte[] {(byte) 0x09});
        // CTR-J
        keyMap.put(74, new byte[] {(byte) 0x0A});
        // CTR-K
        keyMap.put(75, new byte[] {(byte) 0x0B});
        // CTR-L
        keyMap.put(76, new byte[] {(byte) 0x0C});
        // CTR-M
        keyMap.put(77, new byte[] {(byte) 0x0D});
        // CTR-N
        keyMap.put(78, new byte[] {(byte) 0x0E});
        // CTR-O
        keyMap.put(79, new byte[] {(byte) 0x0F});
        // CTR-P
        keyMap.put(80, new byte[] {(byte) 0x10});
        // CTR-Q
        keyMap.put(81, new byte[] {(byte) 0x11});
        // CTR-R
        keyMap.put(82, new byte[] {(byte) 0x12});
        // CTR-S
        keyMap.put(83, new byte[] {(byte) 0x13});
        // CTR-T
        keyMap.put(84, new byte[] {(byte) 0x14});
        // CTR-U
        keyMap.put(85, new byte[] {(byte) 0x15});
        // CTR-V
        keyMap.put(86, new byte[] {(byte) 0x16});
        // CTR-W
        keyMap.put(87, new byte[] {(byte) 0x17});
        // CTR-X
        keyMap.put(88, new byte[] {(byte) 0x18});
        // CTR-Y
        keyMap.put(89, new byte[] {(byte) 0x19});
        // CTR-Z
        keyMap.put(90, new byte[] {(byte) 0x1A});
        // CTR-[
        keyMap.put(219, new byte[] {(byte) 0x1B});
        // CTR-]
        keyMap.put(221, new byte[] {(byte) 0x1D});
        // INSERT
        keyMap.put(45, "\033[2~".getBytes());
        // PG UP
        keyMap.put(33, "\033[5~".getBytes());
        // PG DOWN
        keyMap.put(34, "\033[6~".getBytes());
        // END
        keyMap.put(35, "\033[4~".getBytes());
        // HOME
        keyMap.put(36, "\033[1~".getBytes());
    }

    public void removeSession(String sessionId) {
        log.trace("Removing session: {}", sessionId);
        activeSessions.remove(sessionId);
    }
}
