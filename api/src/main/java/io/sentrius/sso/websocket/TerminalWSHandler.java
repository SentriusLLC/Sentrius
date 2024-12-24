
package io.sentrius.sso.websocket;

import io.sentrius.sso.automation.auditing.Trigger;
import io.sentrius.sso.automation.auditing.TriggerAction;
import io.sentrius.sso.core.model.metadata.TerminalSessionMetadata;
import io.sentrius.sso.core.services.metadata.TerminalSessionMetadataService;
import io.sentrius.sso.protobuf.Session;
import io.sentrius.sso.core.security.service.CryptoService;
import io.sentrius.sso.core.services.terminal.SessionTrackingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.sql.Timestamp;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;

import java.net.URI;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Component
@RequiredArgsConstructor
public class TerminalWSHandler extends TextWebSocketHandler {


    final SessionTrackingService sessionTrackingService;
    final SshListenerService sshListenerService;
    final CryptoService cryptoService;
    final TerminalSessionMetadataService terminalSessionMetadataService;


    // Store active sessions, using session ID or a custom identifier
    private final ConcurrentHashMap<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        // Extract query parameters from the URI
        URI uri = session.getUri();
        if (uri != null) {
            Map<String, String> queryParams = parseQueryParams(uri.getQuery());
            String sessionId = queryParams.get("sessionId");



            if (sessionId != null) {
                // Store the WebSocket session using the session ID from the query parameter
                sessions.put(sessionId, session);
                log.info("New connection established, session ID: " + sessionId);
                sshListenerService.startListeningToSshServer(sessionId, session);
            } else {
                log.trace("Session ID not found in query parameters.");
                session.close(); // Close the session if no valid session ID is provided
            }
        } else {
            log.trace("No URI available for this session.");
            session.close(); // Close the session if URI is unavailable
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message)
        throws IOException, GeneralSecurityException {

        // Extract query parameters from the URI again if needed
        URI uri = session.getUri();
        log.trace("got message {}", uri);
        try {
            if (uri != null) {
                Map<String, String> queryParams = parseQueryParams(uri.getQuery());
                String sessionId = queryParams.get("sessionId");

                if (sessionId != null) {
                    log.trace("Received message from session ID: " + sessionId);
                    // Handle the message (e.g., process or respond)


                    // Deserialize the protobuf message
                    byte[] messageBytes = Base64.getDecoder().decode(message.getPayload());
                    Session.TerminalMessage auditLog =
                        Session.TerminalMessage.parseFrom(messageBytes);
                    log.info("got message {}; {}; {}", uri,sessionId, auditLog.getCommand());
                    // Decrypt the session ID
//                    var sessionIdStr = cryptoService.decrypt(sessionId);
  //                  var sessionIdLong = Long.parseLong(sessionIdStr);
                    var lookupId = sessionId + "==";
                    // Retrieve ConnectedSystem from your persistent map using the session ID
                    var sys = sessionTrackingService.getEncryptedConnectedSession(lookupId);
                    if (null != sys ) {
                        boolean allNoAction = true;
                        log.info("**** Processing message for session ID: {} with {} actions", sessionId,
                            sys.getSessionStartupActions().size());
                        for (var action : sys.getSessionStartupActions()) {
                            var trigger = action.onMessage(auditLog);
                            if (trigger.get().getAction() == TriggerAction.JIT_ACTION) {
                                allNoAction = false;
                                // drop the message
                                sys.getTerminalAuditor().setSessionTrigger(trigger.get());
                                log.info("**** Setting JIT Trigger: {}", trigger.get());
                                sessionTrackingService.addSystemTrigger(sys, trigger.get());
                                return;
                            } else if (trigger.get().getAction() == TriggerAction.WARN_ACTION) {
                                allNoAction = false;
                                // send the message
                                log.info("**** Setting WARN Trigger: {}", trigger.get());
                                sys.getTerminalAuditor().setSessionTrigger(trigger.get());
                                sessionTrackingService.addSystemTrigger(sys, trigger.get());
                            } else if (trigger.get().getAction() == TriggerAction.PROMPT_ACTION) {
                                sessionTrackingService.addTrigger(sys, trigger.get());
                                return;
                            }
                        }
                        if (allNoAction && sys.getSessionStartupActions().size() > 0) {
                            log.info("**** Setting NO_ACTION Trigger");
                            var noActionTrigger = new Trigger(TriggerAction.NO_ACTION, "");
                            sessionTrackingService.addSystemTrigger(sys, noActionTrigger);
                            sys.getTerminalAuditor().setSessionTrigger(noActionTrigger);
                        }

                        // Get the user's session and handle trigger if present
                        sshListenerService.processTerminalMessage(sys, auditLog);
                    }
                } else {
                    log.trace("Session ID not found in query parameters for message handling.");
                }
            }
        }catch (Exception e ){
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, org.springframework.web.socket.CloseStatus status) throws Exception {
        URI uri = session.getUri();
        if (uri != null) {
            Map<String, String> queryParams = parseQueryParams(uri.getQuery());
            String sessionId = queryParams.get("sessionId");

            if (sessionId != null) {
                // Remove the session when connection is closed
                var lookupId = sessionId + "==";
                var sys = sessionTrackingService.getEncryptedConnectedSession(lookupId);
                if (null != sys){
                    log.info("**** Closing session for {}", sys.getSession());
                    terminalSessionMetadataService.getSessionBySessionLog(sys.getSession()).ifPresent(sessionMetadata -> {
                        sessionMetadata.setEndTime(new Timestamp(System.currentTimeMillis()));
                        sessionMetadata.setSessionStatus("CLOSED");
                        terminalSessionMetadataService.saveSession(sessionMetadata);
                    });
                }

                sessions.remove(sessionId);
                sshListenerService.removeSession(sessionId);

                log.info("Connection closed, session ID: " + sessionId);
            }
        }
    }

    // Utility method to parse query parameters
    private Map<String, String> parseQueryParams(String query) {
        if (query == null || query.isEmpty()) {
            return Map.of();
        }
        return Stream.of(query.split("&"))
            .map(param -> param.split("="))
            .collect(Collectors.toMap(
                param -> param[0],
                param -> param.length > 1 ? param[1] : ""
            ));
    }

    // Utility method to send a message to a specific session
    public void sendMessageToSession(String sessionId, String message) {
        WebSocketSession session = sessions.get(sessionId);
        if (session != null && session.isOpen()) {
            try {
                session.sendMessage(new TextMessage(message));
            } catch (IOException e) {
                System.err.println("Error sending message to session " + sessionId);
                e.printStackTrace();
            }
        } else {
            System.err.println("Session not found or already closed: " + sessionId);
        }
    }
}
