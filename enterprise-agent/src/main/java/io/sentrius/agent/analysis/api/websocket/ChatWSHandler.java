
package io.sentrius.agent.analysis.api.websocket;

import java.io.IOException;
import java.net.URI;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.sentrius.agent.analysis.agents.agents.ChatAgent;
import io.sentrius.agent.analysis.agents.agents.VerbRegistry;
import io.sentrius.agent.analysis.agents.verbs.AgentVerbs;
import io.sentrius.agent.analysis.agents.verbs.ChatVerbs;
import io.sentrius.agent.analysis.agents.verbs.TerminalVerbs;
import io.sentrius.agent.analysis.api.UserCommunicationService;
import io.sentrius.agent.analysis.model.LLMResponse;
import io.sentrius.sso.core.exceptions.ZtatException;
import io.sentrius.sso.core.services.agents.AgentClientService;
import io.sentrius.sso.core.services.agents.AgentExecutionService;
import io.sentrius.sso.core.services.agents.ZeroTrustClientService;
import io.sentrius.sso.genai.Message;
import io.sentrius.sso.protobuf.Session;
import io.sentrius.sso.provenance.ProvenanceEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "agents.ai.chat.agent.enabled", havingValue = "true", matchIfMissing = false)
public class ChatWSHandler extends TextWebSocketHandler {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    final UserCommunicationService userCommunicationService;
    final ZeroTrustClientService zeroTrustClientService;
    final TerminalVerbs terminalVerbs;
    final AgentVerbs agentVerbs;
    final ChatVerbs chatVerbs;
    final AgentExecutionService agentExecutionService;
    // Store active sessions, using session ID or a custom identifier


    private final ChatAgent  chatAgent;
    private final AgentClientService agentClientService;
    private final VerbRegistry verbRegistry;


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


        // Automatically pause the agent when chat session is established
        if (!chatAgent.isPaused()) {
            log.info("Automatically pausing agent due to new chat session");
            chatAgent.pauseAgent();
        }

        ProvenanceEvent provenanceEvent = ProvenanceEvent.builder()
            .eventType(ProvenanceEvent.EventType.USER_CHAT_AGENT)
            .actor("admin")
            .triggeringUser(chatAgent.getAgentExecution().getUser().getName())
            .outputSummary("New chat session established - agent paused")
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
                    if (null == websocketCommunication.getAgentExecutionContextDTO().getAgentContext()){
                        log.info("Loading agent context for session ID: {} is null ? {}" , sessionId,
                            agentExecutionService.getExecutionContextDTO( chatAgent.getAgentExecution().getExecutionId() ).getAgentContext()==null);
                        websocketCommunication.getAgentExecutionContextDTO().setAgentContext(
                            agentExecutionService.getExecutionContextDTO( chatAgent.getAgentExecution().getExecutionId() ).getAgentContext()
                        );
                    }
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
                        var json = OBJECT_MAPPER.readTree(auditLog.getMessage());
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
                        } else if ("pause-agent".equals(json.get("type").asText())) {
                            log.info("Received pause command from session {}", sessionId);
                            chatAgent.pauseAgent();
                            websocketCommunication.setAgentPausedBySession(true);
                            
                            var pauseResponse = Session.ChatMessage.newBuilder()
                                .setMessage("Agent autonomous operations have been paused. All state has been preserved including execution context and ztats.")
                                .setSender("agent")
                                .setChatGroupId("")
                                .setSessionId(websocketCommunication.getUniqueIdentifier())
                                .setTimestamp(System.currentTimeMillis())
                                .build();
                            messageBytes = pauseResponse.toByteArray();
                            String base64Message = Base64.getEncoder().encodeToString(messageBytes);
                            session.sendMessage(new TextMessage(base64Message));
                            return;
                        } else if ("resume-agent".equals(json.get("type").asText())) {
                            log.info("Received resume command from session {}", sessionId);
                            chatAgent.resumeAgent();
                            
                            var resumeResponse = Session.ChatMessage.newBuilder()
                                .setMessage("Agent autonomous operations have been resumed. Continuing from saved state.")
                                .setSender("agent")
                                .setChatGroupId("")
                                .setSessionId(websocketCommunication.getUniqueIdentifier())
                                .setTimestamp(System.currentTimeMillis())
                                .build();
                            messageBytes = resumeResponse.toByteArray();
                            String base64Message = Base64.getEncoder().encodeToString(messageBytes);
                            session.sendMessage(new TextMessage(base64Message));
                            return;
                        } else if ("agent-status".equals(json.get("type").asText())) {
                            log.info("Received status query from session {}", sessionId);
                            String status = chatAgent.isPaused() ? "PAUSED" : "RUNNING";
                            
                            var statusResponse = Session.ChatMessage.newBuilder()
                                .setMessage(String.format("Agent status: %s", status))
                                .setSender("agent")
                                .setChatGroupId("")
                                .setSessionId(websocketCommunication.getUniqueIdentifier())
                                .setTimestamp(System.currentTimeMillis())
                                .build();
                            messageBytes = statusResponse.toByteArray();
                            String base64Message = Base64.getEncoder().encodeToString(messageBytes);
                            session.sendMessage(new TextMessage(base64Message));
                            return;
                        } else if ("modify-context".equals(json.get("type").asText())) {
                            log.info("Received modify-context command from session {}", sessionId);
                            
                            // Extract modification details first
                            final String contextKey = json.has("contextKey") ? json.get("contextKey").asText() : null;
                            final String contextValue = json.has("contextValue") ? json.get("contextValue").asText() : null;
                            final String operation = json.has("operation") ? json.get("operation").asText() : null;
                            
                            try {
                                // Use the agent's modifyContextIfPaused method to ensure thread-safe modification
                                boolean modified = chatAgent.modifyContextIfPaused(() -> {
                                    try {
                                        // Perform modifications while holding the agent's pause lock
                                        if (contextKey != null && contextValue != null) {
                                            // Update the agent's execution context
                                            JsonNode valueNode = OBJECT_MAPPER.readTree(contextValue);
                                            websocketCommunication.getAgentExecutionContextDTO().addToMemory(contextKey, valueNode);
                                            log.info("Updated context: {} = {}", contextKey, contextValue);
                                        }
                                        
                                        if (operation != null) {
                                            // Change the next operation
                                            ObjectNode opNode = OBJECT_MAPPER.createObjectNode();
                                            opNode.put("nextOperation", operation);
                                            websocketCommunication.getAgentExecutionContextDTO().addToMemory("nextOperation", opNode);
                                            log.info("Changed next operation to: {}", operation);
                                        }
                                    } catch (Exception e) {
                                        log.error("Error parsing context value: {}", e.getMessage(), e);
                                        throw new RuntimeException("Failed to parse context value: " + e.getMessage(), e);
                                    }
                                });
                                
                                if (!modified) {
                                    // Agent was not paused
                                    var errorResponse = Session.ChatMessage.newBuilder()
                                        .setMessage("Cannot modify context while agent is running. Please pause the agent first.")
                                        .setSender("agent")
                                        .setChatGroupId("")
                                        .setSessionId(websocketCommunication.getUniqueIdentifier())
                                        .setTimestamp(System.currentTimeMillis())
                                        .build();
                                    messageBytes = errorResponse.toByteArray();
                                    String base64Message = Base64.getEncoder().encodeToString(messageBytes);
                                    session.sendMessage(new TextMessage(base64Message));
                                    return;
                                }
                                
                                var modifyResponse = Session.ChatMessage.newBuilder()
                                    .setMessage("Agent context has been modified. Changes will take effect when agent is resumed.")
                                    .setSender("agent")
                                    .setChatGroupId("")
                                    .setSessionId(websocketCommunication.getUniqueIdentifier())
                                    .setTimestamp(System.currentTimeMillis())
                                    .build();
                                messageBytes = modifyResponse.toByteArray();
                                String base64Message = Base64.getEncoder().encodeToString(messageBytes);
                                session.sendMessage(new TextMessage(base64Message));
                            } catch (Exception e) {
                                log.error("Error modifying context: {}", e.getMessage(), e);
                                var errorResponse = Session.ChatMessage.newBuilder()
                                    .setMessage("Failed to modify context: " + e.getMessage())
                                    .setSender("agent")
                                    .setChatGroupId("")
                                    .setSessionId(websocketCommunication.getUniqueIdentifier())
                                    .setTimestamp(System.currentTimeMillis())
                                    .build();
                                messageBytes = errorResponse.toByteArray();
                                String base64Message = Base64.getEncoder().encodeToString(messageBytes);
                                session.sendMessage(new TextMessage(base64Message));
                            }
                            return;
                        } else if ("user-message".equals(json.get("type").asText())) {
                            Message userMessage = Message.builder().role("user").content(json.get("message").asText()).build();

                            // Store user message for conversation history
                            websocketCommunication.getAgentExecutionContextDTO().addToPersistentMemory(
                                "user_message_" + System.currentTimeMillis(),
                                json.get("message").asText(),
                                "PRIVATE",
                                new String[]{"CONVERSATION"}
                            );
                            log.info("Received heartbeat from session {}", sessionId);
                            var response = chatVerbs.interpretUserData(chatAgent.getAgentExecution(),
                                websocketCommunication.getAgentExecutionContextDTO(),
                                websocketCommunication, userMessage);
                            log.info("Response: {}", response);
                            if (response.getMemoryLookup() != null && !response.getMemoryLookup().isEmpty()) {
                                log.info("Memory lookup requested: {}", response.getMemoryLookup());
                                try {
                                    // Set up memory lookup arguments
                                    Map<String, Object> memoryArgs = new HashMap<>();
                                    memoryArgs.put("query", response.getMemoryLookup());

                                    // Execute memory lookup

                                    var memoryResponse = verbRegistry.execute(
                                        chatAgent.getAgentExecution(),
                                        websocketCommunication.getAgentExecutionContextDTO(),
                                        null,
                                        "lookup_agent_memory",
                                        memoryArgs
                                    );

                                    // Add memory results to context for LLM
                                    if (memoryResponse != null && memoryResponse.getReturnName() != null) {
                                        var memoryResult = websocketCommunication.getAgentExecutionContextDTO().getAgentShortTermMemory()
                                            .get(memoryResponse.getReturnName());
                                        if (memoryResult != null) {
                                            websocketCommunication.getAgentExecutionContextDTO().addMessages(
                                                Message.builder()
                                                    .role("system")
                                                    .content("Memory lookup results: " + memoryResult.toString())
                                                    .build()
                                            );
                                            log.info("Memory lookup completed, results added to context");
                                        }
                                    }
                                } catch (Exception e) {
                                    log.warn("Memory lookup failed: {}", e.getMessage());
                                    websocketCommunication.getAgentExecutionContextDTO().addMessages(
                                        Message.builder()
                                            .role("system")
                                            .content("Memory lookup failed: " + e.getMessage())
                                            .build()
                                    );
                                }
                            }


                            var responseToUser = getSafeResponse( response.getResponseForUser() );

                            // Store agent response for conversation history
                            websocketCommunication.getAgentExecutionContextDTO().addToPersistentMemory(
                                "agent_response_" + System.currentTimeMillis(),
                                responseToUser,
                                "PRIVATE",
                                new String[]{"CONVERSATION"}
                            );


                            var newMessage = Session.ChatMessage.newBuilder()
                                .setMessage(responseToUser
                                )
                                .setSender("agent")
                                .setChatGroupId("")
                                .setSessionId(websocketCommunication.getUniqueIdentifier())
                                .setTimestamp(System.currentTimeMillis())
                                .build();
                            websocketCommunication.getAgentExecutionContextDTO().addToPersistentMemory(
                                "agent_response_" + System.currentTimeMillis(),
                                responseToUser,
                                "PRIVATE",
                                new String[]{"CONVERSATION"}
                            );
                            messageBytes = newMessage.toByteArray();
                            String base64Message = Base64.getEncoder().encodeToString(messageBytes);
                            session.sendMessage(new TextMessage(
                                base64Message
                            ));

                            websocky.get().getCommunicationResponses().add(response);

                            if (response.getNextOperation() != null && !response.getNextOperation().isEmpty() &&
                                verbRegistry.isVerbRegistered(response.getNextOperation()))
                            {
                                try {

                                    LLMResponse nextResponse = null;

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
                                        if (planResponse.isEmpty()) {
                                            var respName =
                                                websocketCommunication.getAgentExecutionContextDTO().getAgentShortTermMemory().get( executionResponse.getReturnName() );
                                            if (respName != null) {
                                                planResponse = respName.toString();
                                            }
                                        }
                                        log.info("Plan response: {} from {}", planResponse, responses);
                                        nextResponse = chatVerbs.interpret_plan_response(
                                            chatAgent.getAgentExecution(),
                                            websocketCommunication.getAgentExecutionContextDTO(),
                                            verbRegistry.getVerbs().get(response.getNextOperation()),
                                            planResponse
                                        );

                                        if (nextResponse.getMemoryLookup() != null && !nextResponse.getMemoryLookup().isEmpty()) {
                                            log.info("Memory lookup requested: {}", nextResponse.getMemoryLookup());
                                            try {
                                                // Set up memory lookup arguments
                                                Map<String, Object> memoryArgs = new HashMap<>();
                                                memoryArgs.put("query", nextResponse.getMemoryLookup());

                                                // Execute memory lookup
                                                var memoryResponse = verbRegistry.execute(
                                                    chatAgent.getAgentExecution(),
                                                    websocketCommunication.getAgentExecutionContextDTO(),
                                                    null,
                                                    "lookup_agent_memory",
                                                    memoryArgs
                                                );

                                                // Add memory results to context for LLM
                                                if (memoryResponse != null && memoryResponse.getReturnName() != null) {
                                                    var memoryResult = websocketCommunication.getAgentExecutionContextDTO().getAgentShortTermMemory()
                                                        .get(memoryResponse.getReturnName());
                                                    if (memoryResult != null) {
                                                        websocketCommunication.getAgentExecutionContextDTO().addMessages(
                                                            Message.builder()
                                                                .role("system")
                                                                .content("Memory lookup results: " + memoryResult.toString())
                                                                .build()
                                                        );
                                                        log.info("Memory lookup completed, results added to context");
                                                    }
                                                }
                                            } catch (Exception e) {
                                                log.warn("Memory lookup failed: {}", e.getMessage());
                                                websocketCommunication.getAgentExecutionContextDTO().addMessages(
                                                    Message.builder()
                                                        .role("system")
                                                        .content("Memory lookup failed: " + e.getMessage())
                                                        .build()
                                                );
                                            }
                                        }

                                        websocketCommunication.getAgentExecutionContextDTO().addToPersistentMemory(
                                            "agent_response_" + System.currentTimeMillis(),
                                            nextResponse.getResponseForUser(),
                                            "PRIVATE",
                                            new String[]{"CONVERSATION"}
                                        );

                                        websocky.get().getCommunicationResponses().add(nextResponse);

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

                                        var memory = websocketCommunication.getAgentExecutionContextDTO().flushPersistentMemory();
                                        if (memory != null && !memory.isEmpty()) {
                                            for(var memoryEntry : memory.entrySet()){
                                                JsonNode memoryMeta = memoryEntry.getValue();

                                                // Extract metadata from the memory node
                                                String classification = memoryMeta.has("classification") ?
                                                    memoryMeta.get("classification").asText() : "PRIVATE";
                                                String markings = memoryMeta.has("markings") ?
                                                    memoryMeta.get("markings").asText() : null;
                                                JsonNode value = memoryMeta.has("value") ?
                                                    memoryMeta.get("value") : memoryMeta;

                                                // Add userId to markings for privacy scoping if userId is available
                                                String userId = chatAgent.getAgentExecution().getUser() != null 
                                                    ? chatAgent.getAgentExecution().getUser().getUserId() 
                                                    : null;
                                                String enhancedMarkings;
                                                if (userId != null && !userId.isEmpty()) {
                                                    enhancedMarkings = markings != null
                                                        ? markings + ",USER:" + userId
                                                        : "USER:" + userId;
                                                } else {
                                                    // If no userId, use markings as-is without USER scoping
                                                    // Ensure we have at least an empty string to avoid NPE in split()
                                                    enhancedMarkings = markings != null ? markings : "";
                                                }

                                                agentClientService.storeMemory(chatAgent.getAgentExecution(),
                                                    websocketCommunication.getAgentExecutionContextDTO().getAgentContext().getName(),
                                                    io.sentrius.sso.core.dto.agents.AgentMemoryDTO.builder()
                                                        .agentName(websocketCommunication.getAgentExecutionContextDTO().getAgentContext().getName())
                                                        .memoryKey(memoryEntry.getKey())
                                                        .memoryValue(value.toString())
                                                        .classification(classification)
                                                        .markings(enhancedMarkings.isEmpty() ? new String[0] : enhancedMarkings.split(","))
                                                        .conversationId(chatAgent.getAgentExecution().getCommunicationId())
                                                        .build());
                                                log.info("Stored memory: {} with classification: {} and markings: {}",
                                                    memoryEntry.getKey(), classification, enhancedMarkings);
                                            }
                                        } else {
                                            log.info("No persistent memory to store 424.");
                                        }

                                    }while (nextResponse.getNextOperation() != null && !nextResponse.getNextOperation().isEmpty());
                                }catch (Exception e){
                                    e.printStackTrace();
                                    log.error("Error executing next operation: {}", e.getMessage());

                                }


                            }else {
                                var memory = websocketCommunication.getAgentExecutionContextDTO().flushPersistentMemory();
                                if (memory != null && !memory.isEmpty()) {
                                    for(var memoryEntry : memory.entrySet()){
                                        JsonNode memoryMeta = memoryEntry.getValue();

                                        // Extract metadata from the memory node
                                        String classification = memoryMeta.has("classification") ?
                                            memoryMeta.get("classification").asText() : "PRIVATE";
                                        String markings = memoryMeta.has("markings") ?
                                            memoryMeta.get("markings").asText() : null;
                                        JsonNode value = memoryMeta.has("value") ?
                                            memoryMeta.get("value") : memoryMeta;

                                        // Add userId to markings for privacy scoping if userId is available
                                        String userId = chatAgent.getAgentExecution().getUser() != null 
                                            ? chatAgent.getAgentExecution().getUser().getUserId() 
                                            : null;
                                        String enhancedMarkings;
                                        if (userId != null && !userId.isEmpty()) {
                                            enhancedMarkings = markings != null
                                                ? markings + ",USER:" + userId
                                                : "USER:" + userId;
                                        } else {
                                            // If no userId, use markings as-is without USER scoping
                                            // Ensure we have at least an empty string to avoid NPE in split()
                                            enhancedMarkings = markings != null ? markings : "";
                                        }

                                        agentClientService.storeMemory(chatAgent.getAgentExecution(),
                                            websocketCommunication.getAgentExecutionContextDTO().getAgentContext().getName(),
                                            io.sentrius.sso.core.dto.agents.AgentMemoryDTO.builder()
                                                .agentName(websocketCommunication.getAgentExecutionContextDTO().getAgentContext().getName())
                                                .memoryKey(memoryEntry.getKey())
                                                .memoryValue(value.toString())
                                                .classification(classification)
                                                .markings(enhancedMarkings.isEmpty() ? new String[0] : enhancedMarkings.split(","))
                                                .conversationId(chatAgent.getAgentExecution().getCommunicationId())
                                                .build());
                                        log.info("Stored memory: {} with classification: {} and markings: {}",
                                            memoryEntry.getKey(), classification, enhancedMarkings);
                                    }
                                } else {
                                    log.info("No persistent memory to store 470.");
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

    private String getSafeResponse(String responseForUser) {
        if (null != responseForUser && !responseForUser.trim().isEmpty()) {
            return  responseForUser;
        }
        return "Working on your request...";
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

                if (chatAgent.isPaused()){
                    log.info("Resuming agent as chat session has ended");
                    chatAgent.resumeAgent();
                }
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
