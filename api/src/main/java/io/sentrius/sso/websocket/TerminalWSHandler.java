
package io.sentrius.sso.websocket;

import io.sentrius.sso.automation.auditing.Trigger;
import io.sentrius.sso.automation.auditing.TriggerAction;
import io.sentrius.sso.core.integrations.ssh.DataWebSession;
import io.sentrius.sso.core.model.chat.ChatLog;
import io.sentrius.sso.core.services.ChatService;
import io.sentrius.sso.core.services.metadata.TerminalSessionMetadataService;
import io.sentrius.sso.core.services.security.CryptoService;
import io.sentrius.sso.core.services.SshListenerService;
import io.sentrius.sso.core.utils.StringUtils;
import io.sentrius.sso.protobuf.Session;
import io.sentrius.sso.core.services.terminal.SessionTrackingService;
import io.sentrius.sso.services.WebTerminalAISupportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final ChatService chatService;
    
    @Autowired(required = false)
    private WebTerminalAISupportService aiSupportService;

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
                log.debug("New connection established, session ID: " + sessionId);
                sshListenerService.startListeningToSshServer(sessionId, new DataWebSession(session));
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
        log.debug("got message {}", uri);
        try {
            if (uri != null) {
                Map<String, String> queryParams = parseQueryParams(uri.getQuery());
                String sessionId = queryParams.get("sessionId");

                if (sessionId != null) {
                    log.debug("Received message from session ID: " + sessionId);
                    // Handle the message (e.g., process or respond)


                    // Deserialize the protobuf message
                    byte[] messageBytes = Base64.getDecoder().decode(message.getPayload());
                    Session.TerminalMessage auditLog =
                        Session.TerminalMessage.parseFrom(messageBytes);
                    // Decrypt the session ID
                    var sessionIdStr = cryptoService.decrypt(sessionId);
                    var lookupId = sessionId; // + "==";
                    // Retrieve ConnectedSystem from your persistent map using the session ID
                    var sys = sessionTrackingService.getEncryptedConnectedSession(sessionIdStr);
                    if (null != sys ) {
                        
                        // Check for @agent commands before processing other actions
                        if (aiSupportService != null && isAgentCommand(auditLog)) {
                            handleAgentCommand(auditLog, sys, session);
                            return;
                        }
                        
                        boolean allNoAction = true;
                        log.debug("**** Processing message for session ID: {} with {} actions", sessionId,
                            sys.getSessionStartupActions().size());
                        for (var action : sys.getSessionStartupActions()) {
                            var trigger = action.onMessage(auditLog);
                            if (trigger.get().getAction() == TriggerAction.JIT_ACTION) {
                                allNoAction = false;
                                // drop the message
                                sys.getTerminalAuditor().setSessionTrigger(trigger.get());
                                log.debug("**** Setting JIT Trigger: {}", trigger.get());
                                sessionTrackingService.addSystemTrigger(sys, trigger.get());
                                return;
                            } else if (trigger.get().getAction() == TriggerAction.WARN_ACTION) {
                                allNoAction = false;
                                // send the message
                                log.debug("**** Setting WARN Trigger: {}", trigger.get());
                                sys.getTerminalAuditor().setSessionTrigger(trigger.get());
                                sessionTrackingService.addSystemTrigger(sys, trigger.get());
                            } else if (trigger.get().getAction() == TriggerAction.PROMPT_ACTION) {
                                if (!StringUtils.isBlank( trigger.get().getAsk())){
                                    // send the question into the log
                                    chatService.save(ChatLog.builder().sender("agent").message(trigger.get().getAsk()).build());
                                }
                                sessionTrackingService.addTrigger(sys, trigger.get());
                                return;
                            }
                        }
                        if (allNoAction && sys.getSessionStartupActions().size() > 0) {
                            log.debug("**** Setting NO_ACTION Trigger");
                            var noActionTrigger = new Trigger(TriggerAction.NO_ACTION, "");
                            sessionTrackingService.addSystemTrigger(sys, noActionTrigger);
                            sys.getTerminalAuditor().setSessionTrigger(noActionTrigger);
                        }

                        // Get the user's session and handle trigger if present
                        sshListenerService.processTerminalMessage(sys, auditLog);
                    } else {
                        log.debug("No session found for session ID: {}", sessionId);
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
                var sessionIdStr = cryptoService.decrypt(sessionId);
                var sys = sessionTrackingService.getEncryptedConnectedSession(sessionIdStr);
                if (null != sys){
                    log.debug("**** Closing session for {}", sys.getSession());
                    terminalSessionMetadataService.getSessionBySessionLog(sys.getSession()).ifPresent(sessionMetadata -> {
                        sessionMetadata.setEndTime(new Timestamp(System.currentTimeMillis()));
                        sessionMetadata.setSessionStatus("CLOSED");
                        terminalSessionMetadataService.saveSession(sessionMetadata);
                    });
                }

                sessions.remove(sessionId);
                sshListenerService.removeSession(sessionId);

                log.debug("Connection closed, session ID: {}", sessionId);
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
    
    /**
     * Check if a terminal message contains an @agent command
     */
    private boolean isAgentCommand(Session.TerminalMessage message) {
        if (message.getType() != Session.MessageType.USER_DATA) {
            return false;
        }
        
        String command = message.getCommand();
        if (command == null || command.trim().isEmpty()) {
            return false;
        }
        
        String trimmed = command.trim();
        return trimmed.startsWith("@agent") || trimmed.startsWith("/ask");
    }
    
    /**
     * Handle an @agent command from the web terminal
     */
    private void handleAgentCommand(Session.TerminalMessage message, 
                                    io.sentrius.sso.core.model.ConnectedSystem connectedSystem,
                                    WebSocketSession webSocketSession) {
        try {
            String command = message.getCommand().trim();
            String query;
            
            // Extract query from command
            if (command.startsWith("@agent ")) {
                query = command.substring("@agent ".length()).trim();
            } else if (command.startsWith("/ask ")) {
                query = command.substring("/ask ".length()).trim();
            } else if (command.equals("@agent") || command.equals("/ask")) {
                // Show help if no query provided
                sendAgentHelpMessage(webSocketSession);
                return;
            } else {
                return;
            }
            
            if (query.isEmpty()) {
                sendAgentHelpMessage(webSocketSession);
                return;
            }
            
            log.info("Processing @agent command from web terminal: {}", query);
            
            // Process the query through AI support service
            String response = aiSupportService.processAgentQuery(connectedSystem, query);
            
            // Send response back to terminal via chat
            if (response != null && !response.isEmpty()) {
                aiSupportService.sendAgentMessageToTerminal(webSocketSession, response, "ai-support-agent");
            }
            
        } catch (Exception e) {
            log.error("Error handling agent command in web terminal", e);
            try {
                aiSupportService.sendAgentMessageToTerminal(
                    webSocketSession,
                    "Sorry, I encountered an error processing your request. Please try again.",
                    "system"
                );
            } catch (Exception e2) {
                log.error("Failed to send error message", e2);
            }
        }
    }
    
    /**
     * Send agent help message to terminal
     */
    private void sendAgentHelpMessage(WebSocketSession webSocketSession) {
        String helpMessage = "╔════════════════════════════════════════════════════════════════╗\n" +
            "║                    AI SUPPORT AGENT                            ║\n" +
            "╚════════════════════════════════════════════════════════════════╝\n" +
            "\n" +
            "Ask questions and get intelligent assistance from the AI agent:\n" +
            "\n" +
            "Usage:\n" +
            "  @agent <question>     - Ask the agent a question\n" +
            "  /ask <question>       - Alternative command prefix\n" +
            "\n" +
            "Examples:\n" +
            "  @agent How do I list all files in a directory?\n" +
            "  /ask What is the purpose of the chmod command?\n" +
            "  @agent Help me understand this error message\n" +
            "\n" +
            "The agent can search documentation and TSGs to provide relevant help.\n";
        
        try {
            aiSupportService.sendAgentMessageToTerminal(webSocketSession, helpMessage, "system");
        } catch (Exception e) {
            log.error("Failed to send help message", e);
        }
    }
}
