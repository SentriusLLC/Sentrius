package io.sentrius.sso.websocket;

import java.security.GeneralSecurityException;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.sentrius.sso.core.model.ConnectedSystem;
import io.sentrius.sso.core.model.chat.ChatLog;
import io.sentrius.sso.core.services.ChatService;
import io.sentrius.sso.core.services.security.CryptoService;
import io.sentrius.sso.core.services.security.IntegrationSecurityTokenService;
import io.sentrius.sso.core.services.terminal.SessionTrackingService;
import io.sentrius.sso.core.utils.JsonUtil;
import io.sentrius.sso.genai.ChatConversation;
import io.sentrius.sso.genai.GenerativeAPI;
import io.sentrius.sso.genai.GeneratorConfiguration;
import io.sentrius.sso.genai.model.ChatResponse;
import io.sentrius.sso.genai.model.Conversation;
import io.sentrius.sso.core.integrations.external.ExternalIntegrationDTO;
import io.sentrius.sso.protobuf.Session;
import io.sentrius.sso.security.ApiKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatListenerService {

    private final SessionTrackingService sessionTrackingService;
    private final CryptoService cryptoService;

    @Qualifier("taskExecutor") // Specify the custom task executor to use
    private final Executor taskExecutor;
    private final IntegrationSecurityTokenService integrationSecurityTokenService;

    private final SshListenerService sshListenerService;
    private final ChatService chatService;

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

    public void startChatListener(String terminalSessionId, WebSocketSession session) throws GeneralSecurityException {

        var sessionIdStr = cryptoService.decrypt(terminalSessionId);
        var sessionIdLong = Long.parseLong(sessionIdStr);


        var connectedSystem = sessionTrackingService.getConnectedSession(sessionIdLong);

        log.info("Starting to listen to SSH server for session: {}", terminalSessionId);

        activeSessions.putIfAbsent(terminalSessionId, session);

        connectedSystem.setWebsocketChatSessionId(session.getId());



        taskExecutor.execute(() -> {
            while (!Thread.currentThread().isInterrupted() && activeSessions.get(terminalSessionId) != null &&
                !connectedSystem.getSession().getClosed()) {
                try {
                    // Mock logic for receiving data from SSH server
                    var sshData = sessionTrackingService.getOutput(connectedSystem, 1L, TimeUnit.SECONDS,
                        output -> (!connectedSystem.getSession().getClosed() && (null != activeSessions.get(terminalSessionId) && activeSessions.get(terminalSessionId).isOpen())));

                    // Send data to the specific terminal session
                    if (null != sshData ) {

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


    @Async
    public void sendToTerminalSession(String terminalSessionId, ConnectedSystem connectedSystem,
                                      Session.ChatMessage sshData) {
    }

    private ChatResponse toChatMessage(Session.ChatMessage sshData) {
        return ChatResponse.builder().role("user").content(sshData.getMessage()).build();
    }

    private ChatResponse toChatMessage(ChatLog chatLog) {
        return ChatResponse.builder().role(chatLog.getSender().equals("agent") ? "system" : "user").content(chatLog.getMessage()).build();
    }

    private List<ChatResponse> toChatMessages(List<ChatLog> chatLogs) {
        return chatLogs.stream().map(this::toChatMessage).collect(Collectors.toList());
    }

    public void processMessage(
        String sessionId,
        WebSocketSession session, ConnectedSystem terminalSessionId, Session.ChatMessage chatMessage) {
        var openaiService = integrationSecurityTokenService.findByConnectionType("openai").stream().findFirst().orElse(null);

        if (null != openaiService) {
            log.info("OpenAI service is available");
            ExternalIntegrationDTO externalIntegrationDTO = null;
            try {
                externalIntegrationDTO = JsonUtil.MAPPER.readValue(
                    openaiService.getConnectionInfo(),
                    ExternalIntegrationDTO.class
                );
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
            ApiKey key =
                ApiKey.builder().apiKey(externalIntegrationDTO.getApiToken())
                    .principal(externalIntegrationDTO.getUsername()).build();

            var chatConversation = new ChatConversation(key, new GenerativeAPI(key),
                GeneratorConfiguration.builder().build());

            var previousMessages = chatService.findBySessionIdAndChatGroupId(terminalSessionId.getSession().getId());
            Conversation convo =
                Conversation.builder().newUserMessage(toChatMessage(chatMessage)).previousMessages(toChatMessages(previousMessages)).build();
            convo.setSystemConfines("You are an agent whose responding to chats form a user on an SSH terminal " +
                "session. The history preceding this message will be included. Please respond with a JSON structure " +
                "that includes the message you want to send to the user. The message should be in the 'message' field" +
                ". If the user requests help directly into the terminal, please return a field labeled 'terminal'. " +
                "Since you have the ability to inject commands into the terminal, always ask if they would like you " +
                "to place it there before sending the terminal field, but place the question at the end of the " +
                "message field, not in terminal, which can be empty. If you are unsure of the user's intent, ask for " +
                "clarification. We " +
                "will send the " +
                "history of your chat with the user. We have another AI agent that asks security related questions. " +
                "If these come they will be sent to you as well along with their response. Ensure that the person " +
                "answers the question, if they do not or you assess that they are being evasive, send a json field " +
                "named alert as a boolean value of true." );
            try {
                var chatMessageResponse = chatConversation.generate(convo);
                ChatLog userChat =
                    ChatLog.builder().messageTimestamp(LocalDateTime.now()).chatGroupId("0")
                        .session(terminalSessionId.getSession()).message(chatMessage.getMessage()).sender(
                            "user").build();
                chatService.save(userChat);
                Session.ChatMessage userMessage = Session.ChatMessage.newBuilder()
                    .setSender("user")
                    .setMessage(chatMessage.getMessage())
                    .build();

                byte[] messageBytes = userMessage.toByteArray();
                String base64Message = Base64.getEncoder().encodeToString(messageBytes);
                log.trace("Sending message to session: {}", userMessage);
      //          session.sendMessage(new TextMessage(base64Message));
                if (chatMessageResponse.getTerminalMessage() != null) {
                    log.info("Sending terminal message to session: {}", chatMessageResponse.getTerminalMessage());
                    Session.TerminalMessage terminalMessage = Session.TerminalMessage.newBuilder()
                        .setType(Session.MessageType.USER_DATA)
                        .setCommand(chatMessageResponse.getTerminalMessage())
                        .build();
                    sshListenerService.sendToTerminalSession(sessionId, terminalSessionId,
                        terminalMessage);
                    sshListenerService.processTerminalMessage(terminalSessionId, terminalMessage);
                    if (chatMessageResponse.getContent().isEmpty()) {
                        Session.ChatMessage chatMessage1 = Session.ChatMessage.newBuilder()
                            .setSender("agent")
                            .setMessage("I've added the command to your terminal!")
                            .build();
                        messageBytes = chatMessage1.toByteArray();
                        base64Message = Base64.getEncoder().encodeToString(messageBytes);
                        log.trace("Sending message to session: {}", chatMessage1);
                        session.sendMessage(new TextMessage(base64Message));
                    }
                }
                if (chatMessageResponse.isAlert()){
                    log.info("**** alerttttttt");
                }
                if (!chatMessageResponse.getContent().isEmpty()) {
                    Session.ChatMessage chatMessage1 = Session.ChatMessage.newBuilder()
                        .setSender("agent")
                        .setMessage(chatMessageResponse.getContent())
                        .build();





                    ChatLog chatLog =
                        ChatLog.builder().messageTimestamp(LocalDateTime.now()).chatGroupId("0").session(terminalSessionId.getSession()).message(chatMessage1.getMessage()).sender(
                            "agent").build();
                    chatService.save(chatLog);

                    messageBytes = chatMessage1.toByteArray();
                    base64Message = Base64.getEncoder().encodeToString(messageBytes);
                    log.trace("Sending message to session: {}", chatMessage1);
                    session.sendMessage(new TextMessage(base64Message));
                }









            } catch (Exception e) {
                e.printStackTrace();
                log.error("Error categorizing command", e);
            }

        } else {
            log.info("OpenAI service is not available");
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
