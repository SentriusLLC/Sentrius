
package io.sentrius.agent.analysis.api.websocket;

import java.io.IOException;
import java.net.URI;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.sentrius.agent.analysis.agents.agents.ChatAgent;
import io.sentrius.agent.analysis.agents.agents.VerbRegistry;
import io.sentrius.agent.analysis.agents.verbs.AgentVerbs;
import io.sentrius.agent.analysis.agents.verbs.ChatVerbs;
import io.sentrius.agent.analysis.agents.verbs.TerminalVerbs;
import io.sentrius.agent.analysis.api.UserCommunicationService;
import io.sentrius.agent.analysis.model.TerminalResponse;
import io.sentrius.sso.core.exceptions.ZtatException;
import io.sentrius.sso.core.services.agents.AgentClientService;
import io.sentrius.sso.core.services.agents.ZeroTrustClientService;
import io.sentrius.sso.genai.Message;
import io.sentrius.sso.protobuf.Session;
import io.sentrius.sso.provenance.ProvenanceEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Slf4j
@Component
@ConditionalOnProperty(name = "agents.ai.chat.agent.enabled", havingValue = "true", matchIfMissing = false)
public class ChatWSHandler extends TextWebSocketHandler {

    final UserCommunicationService userCommunicationService;
    final ZeroTrustClientService zeroTrustClientService;
    final TerminalVerbs terminalVerbs;
    final AgentVerbs agentVerbs;
    final ChatVerbs chatVerbs;
    // Store active sessions, using session ID or a custom identifier


    private final ChatAgent  chatAgent;
    private final AgentClientService agentClientService;
    private final VerbRegistry verbRegistry;

    @Autowired
    public ChatWSHandler(UserCommunicationService userCommunicationService, ZeroTrustClientService zeroTrustClientService,
                         TerminalVerbs terminalVerbs, AgentVerbs agentVerbs, ChatVerbs chatVerbs, ChatAgent chatAgent,
                         AgentClientService agentClientService,
                         VerbRegistry verbRegistry
    ) {
        this.userCommunicationService = userCommunicationService;
        this.zeroTrustClientService = zeroTrustClientService;
        this.terminalVerbs = terminalVerbs;
        this.agentVerbs = agentVerbs;
        this.chatVerbs = chatVerbs;
        this.chatAgent = chatAgent;
        this.agentClientService = agentClientService;
        this.verbRegistry = verbRegistry;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        log.info("New connection established");
        URI uri = session.getUri();
        if (uri == null) {
            session.close(CloseStatus.BAD_DATA);
            return;
        }

        Map<String, String> queryParams = parseQueryParams(uri.getQuery());
        Long sessionId = UUID.fromString( queryParams.get("sessionId") ).getMostSignificantBits();
        String chatGroupId = queryParams.get("chatGroupId");
        String ztatToken = queryParams.get("ztat");

        if (sessionId == null || ztatToken == null) {
            log.warn("Missing sessionId or ZTAT");
            session.close(CloseStatus.BAD_DATA);
            return;
        }

        // Store session
        var websocky = userCommunicationService.createSession(queryParams.get("sessionId"), session);
        log.info("Session {} created for incoming connection", sessionId);

        // Generate and store nonce for this session
        String nonce = UUID.randomUUID().toString();
        session.getAttributes().put("ztatNonce", nonce);
        session.getAttributes().put("ztatToken", ztatToken);
        session.getAttributes().put("sessionId", sessionId);

        // Send challenge to the client
        log.info("Sending challenge to client: {}", nonce);
        var challenge = Session.ChatMessage.newBuilder()
            .setMessage(String.format("{\"type\":\"challenge\",\"nonce\":\"%s\"}", nonce))
            .setSender("agent")
            .setChatGroupId(chatGroupId)
            .setSessionId(sessionId)
            .setTimestamp(System.currentTimeMillis())
            .build();
        byte[] messageBytes = challenge.toByteArray();
        String base64Message = Base64.getEncoder().encodeToString(messageBytes);
        session.sendMessage(new TextMessage(
            base64Message
        ));

        userCommunicationService.createSession(queryParams.get("sessionId"), session);


        ProvenanceEvent provenanceEvent = ProvenanceEvent.builder()
            .eventType(ProvenanceEvent.EventType.USER_CHAT_AGENT)
            .actor("admin")
            .triggeringUser(chatAgent.getAgentExecution().getUser().getName())
            .outputSummary("New chat session established")
            .sessionId(session.getId())
            .build();

        agentClientService.submitProvenance(chatAgent.getAgentExecution(), provenanceEvent);

    }


    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message)
        throws IOException, GeneralSecurityException {

        // Extract query parameters from the URI again if needed
        URI uri = session.getUri();
        log.info("got message {}", uri);
        try {
            if (uri != null) {
                Map<String, String> queryParams = parseQueryParams(uri.getQuery());
                String sessionId = queryParams.get("sessionId");

                var websocky = userCommunicationService.getSession(sessionId);

                if (sessionId != null && websocky.isPresent()) {
                    var websocketCommunication = websocky.get();
                    log.info("Received message from session ID: {}" , sessionId, websocketCommunication.getUniqueIdentifier());
                    // Handle the message (e.g., process or respond)


                    var connection = userCommunicationService.getSession(sessionId);
                    // Deserialize the protobuf message

                        byte[] messageBytes = Base64.getDecoder().decode(message.getPayload());
                        Session.ChatMessage auditLog =
                            Session.ChatMessage.parseFrom(messageBytes);

                        if (auditLog.getMessage().equals("heartbeat")) {
                            return;
                        }
                        var json = new ObjectMapper().readTree(auditLog.getMessage());
                        if ("challenge-response".equals(json.get("type").asText())) {
                            String signature = json.get("signature").asText();
                            String publicKey = json.get("publicKey").asText();
                            String nonce = (String) session.getAttributes().get("ztatNonce");
                            String ztat = (String) session.getAttributes().get("ztatToken");

                            boolean verified =
                                zeroTrustClientService.verifyZtatChallenge(chatAgent.getAgentExecution(), ztat, nonce,
                                    signature,
                                    publicKey);

                            if (verified) {
                                session.getAttributes().put("verified", true);
                                log.info("ZTAT challenge verified for session {}", session.getId());
                            } else {
                                log.warn("ZTAT challenge failed for session {}", session.getId());
                                session.close();
                            }
                            return;
                        } else if ("user-message".equals(json.get("type").asText())) {
                            Message userMessage = Message.builder().role("user").content(json.get("message").asText()).build();
                            log.info("Received heartbeat from session {}", sessionId);
                            var response = chatVerbs.interpretUserData(chatAgent.getAgentExecution(),
                                websocketCommunication.getAgentExecutionContextDTO(),
                                websocketCommunication, userMessage);
                            log.info("Response: {}", response);
                            var newMessage = Session.ChatMessage.newBuilder()
                                .setMessage(response.getResponseForUser()/*String.format("{\"type\":\"user-message\"," +
                                        "\"message\":\"%s\"}",
                                    response.getResponseForUser())*/
                                )
                                .setSender("agent")
                                .setChatGroupId("")
                                .setSessionId(websocketCommunication.getUniqueIdentifier())
                                .setTimestamp(System.currentTimeMillis())
                                .build();
                            messageBytes = newMessage.toByteArray();
                            String base64Message = Base64.getEncoder().encodeToString(messageBytes);
                            session.sendMessage(new TextMessage(
                                base64Message
                            ));

                            websocky.get().getMessages().add(response);

                            if (response.getNextOperation() != null && !response.getNextOperation().isEmpty() &&
                                verbRegistry.isVerbRegistered(response.getNextOperation()))
                            {
                                try {

                                    TerminalResponse nextResponse = null;

                                    var lastVerbResponse =
                                        websocketCommunication.getVerbResponses().stream()
                                            .reduce((prev, next) -> next)
                                            .orElse(null);
                                    do {

                                        var arguments = response.getArguments();
                                        var executionResponse = verbRegistry.execute(
                                            chatAgent.getAgentExecution(),
                                            websocketCommunication.getAgentExecutionContextDTO(),
                                            lastVerbResponse,
                                            response.getNextOperation(), arguments
                                        );

//                                        chatAgent.getAgentExecution().addMessages(Message.builder().role("System")
//                                        .content("System executed operation: " + response.getNextOperation()).build());
                                        var responses = websocketCommunication.getAgentExecutionContextDTO().getAgentDataList();
                                        var planResponse =
                                            responses.isEmpty() ? "" :
                                                responses.get( responses.size() -1 ).asText();
                                        nextResponse = chatVerbs.interpret_plan_response(
                                            chatAgent.getAgentExecution(),
                                            websocketCommunication.getAgentExecutionContextDTO(),
                                            websocketCommunication,
                                            verbRegistry.getVerbs().get(response.getNextOperation()),
                                            planResponse
                                        );

                                        websocky.get().getMessages().add(nextResponse);

                                        websocketCommunication.getVerbResponses().add(executionResponse);

                                        var newNextMessage = Session.ChatMessage.newBuilder()
                                            .setMessage(
                                                nextResponse.getResponseForUser()
                                            )
                                            .setSender("agent")
                                            .setChatGroupId("")
                                            .setSessionId(websocketCommunication.getUniqueIdentifier())
                                            .setTimestamp(System.currentTimeMillis())
                                            .build();
                                        messageBytes = newNextMessage.toByteArray();
                                        base64Message = Base64.getEncoder().encodeToString(messageBytes);
                                        session.sendMessage(new TextMessage(
                                            base64Message
                                        ));
                                        log.info("Next response: {}", nextResponse.getResponseForUser());
                                        log.info("Next getNextOperation: {}", nextResponse.getNextOperation());
                                        log.info("Next getArguments: {}", nextResponse.getArguments());
                                        lastVerbResponse = executionResponse;
                                        response = nextResponse;
                                    }while (nextResponse.getNextOperation() != null && !nextResponse.getNextOperation().isEmpty());
                                }catch (Exception e){
                                    e.printStackTrace();
                                    log.error("Error executing next operation: {}", e.getMessage());

                                }


                            }
                            return; // Ignore heartbeat messages
                        } else {
                            log.info("Processing message: {}", auditLog.getMessage());
                            // Process the message as needed
                            //chatAgent.handleChatMessage(sessionId, auditLog);
                        }






                } else {
                    log.info("Session ID not found in query parameters for message handling.");
                }
            }
        }catch (Exception | ZtatException e ){
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


                userCommunicationService.remove(sessionId);

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
        var websocket = userCommunicationService.getSession(sessionId);
        if (websocket.isPresent()) {
            WebSocketSession session = websocket.get().getWebSocketSession();

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
}
