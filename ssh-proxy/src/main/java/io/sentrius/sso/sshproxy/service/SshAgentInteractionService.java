package io.sentrius.sso.sshproxy.service;

import io.sentrius.sso.core.dto.UserDTO;
import io.sentrius.sso.core.dto.agents.AgentExecution;
import io.sentrius.sso.core.dto.agents.SshAgentResponseMessage;
import io.sentrius.sso.core.model.users.User;
import io.sentrius.sso.core.model.chat.ChatLog;
import io.sentrius.sso.core.model.sessions.SessionLog;
import io.sentrius.sso.core.services.ChatService;
import io.sentrius.sso.core.services.agents.AgentExecutionService;
import io.sentrius.sso.core.services.agents.ZeroTrustClientService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Service that handles agent interactions from SSH proxy sessions.
 * Allows SSH users to prompt agents and receive responses through the terminal.
 */
@Slf4j
@Service
public class SshAgentInteractionService {

    private final ChatService chatService;
    private final AgentExecutionService agentExecutionService;
    private final InlineTerminalResponseService terminalResponseService;
    private final ZeroTrustClientService zeroTrustClientService;
    
    @Autowired(required = false)
    private SshAgentKafkaProducer kafkaProducer;
    
    @Autowired(required = false)
    private SshAgentKafkaConsumer kafkaConsumer;
    
    @Value("${agent.chat.enabled:false}")
    private boolean agentChatEnabled;
    
    @Value("${ssh.agent.kafka.enabled:false}")
    private boolean kafkaEnabled;
    
    @Value("${ssh.agent.response.timeout.seconds:8}")
    private int responseTimeoutSeconds;
    
    public SshAgentInteractionService(
        ChatService chatService,
        AgentExecutionService agentExecutionService,
        InlineTerminalResponseService terminalResponseService, 
        ZeroTrustClientService zeroTrustClientService
    ) {
        this.chatService = chatService;
        this.agentExecutionService = agentExecutionService;
        this.terminalResponseService = terminalResponseService;
        this.zeroTrustClientService = zeroTrustClientService;
    }

    /**
     * Process an agent query from an SSH session
     * 
     * @param user The user making the request
     * @param sessionLog The SSH session
     * @param query The query to send to the agent
     * @param terminalOutput The terminal output stream
     * @return true if the query was processed successfully
     */
    public boolean processAgentQuery(User user, SessionLog sessionLog, String query, OutputStream terminalOutput) {
        try {
            // Get or create agent execution context for this user
            UserDTO userDTO = UserDTO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .emailAddress(user.getEmailAddress() != null ? user.getEmailAddress() : "")
                .build();
            
            AgentExecution execution = agentExecutionService.getAgentExecution(userDTO);
            String chatGroupId = execution.getExecutionId();
            
            // Log the user's query
            ChatLog userMessage = ChatLog.builder()
                .session(sessionLog)
                .chatGroupId(chatGroupId)
                .sender(user.getUsername())
                .message(query)
                .messageTimestamp(LocalDateTime.now())
                .build();
            chatService.save(userMessage);
            
            // Show processing indicator
            terminalResponseService.sendMessage("\r\n🤖 Processing your query...\r\n", terminalOutput);
            
            // Get agent response
            String agentResponse = getAgentResponse(user, query, chatGroupId);
            
            // Log the agent's response
            ChatLog agentMessage = ChatLog.builder()
                .session(sessionLog)
                .chatGroupId(chatGroupId)
                .sender("agent")
                .message(agentResponse)
                .messageTimestamp(LocalDateTime.now())
                .build();
            chatService.save(agentMessage);
            
            // Send formatted response to terminal
            sendAgentResponse(agentResponse, terminalOutput);
            
            return true;
            
        } catch (Exception e) {
            log.error("Error processing agent query", e);
            try {
                terminalResponseService.sendMessage(
                    "\r\n❌ Error: Failed to process agent query\r\n", 
                    terminalOutput
                );
            } catch (IOException ioException) {
                log.error("Error sending error message to terminal", ioException);
            }
            return false;
        }
    }

    /**
     * Get response from agent via Kafka queue
     */
    private String getAgentResponse(User user, String query, String chatGroupId) {
        if (!agentChatEnabled) {
            log.warn("Agent chat is disabled, returning fallback response");
            return getFallbackResponse(query);
        }
        
        if (!kafkaEnabled || kafkaProducer == null || kafkaConsumer == null) {
            log.warn("Kafka is not enabled or not configured, returning fallback response");
            return getFallbackResponse(query);
        }
        
        try {
            // Send query to Kafka
            // Note: chatGroupId serves as both session ID and chat group ID since they represent
            // the same logical grouping for SSH agent conversations
            String queryId = kafkaProducer.sendQuery(
                user.getId().toString(),
                user.getUsername(),
                user.getEmailAddress() != null ? user.getEmailAddress() : "",
                chatGroupId,  // sessionId - using chatGroupId as session identifier
                query,
                chatGroupId   // chatGroupId
            );
            
            log.info("Sent SSH agent query to Kafka: queryId={}, userId={}", queryId, user.getId());
            
            // Wait for response with timeout
            CompletableFuture<SshAgentResponseMessage> responseFuture = kafkaConsumer.awaitResponse(queryId);
            
            try {
                SshAgentResponseMessage response = responseFuture.get(responseTimeoutSeconds, TimeUnit.SECONDS);
                
                if ("success".equalsIgnoreCase(response.getStatus())) {
                    log.info("Received successful SSH agent response: queryId={}", queryId);
                    return response.getResponse();
                } else {
                    log.warn("SSH agent response had error status: {}, error: {}", 
                            response.getStatus(), response.getErrorMessage());
                    return getFallbackResponse(query);
                }
                
            } catch (TimeoutException e) {
                log.warn("Timeout waiting for SSH agent response after {} seconds", responseTimeoutSeconds);
                return "I'm processing your query but it's taking longer than expected. " +
                       "Your request has been queued and an agent will respond shortly. " +
                       "Please try again in a moment.";
            } catch (Exception e) {
                log.error("Error waiting for SSH agent response", e);
                return getFallbackResponse(query);
            }
            
        } catch (Exception e) {
            log.error("Error getting agent response via Kafka", e);
            return getFallbackResponse(query);
        }
    }
    
    /**
     * Fallback response when agent service is unavailable
     */
    private String getFallbackResponse(String query) {
        return "I received your query: \"" + query + "\"\n\n" +
               "I'm your Sentrius AI assistant. The agent service is currently initializing.\n" +
               "I can help you with:\n" +
               "- Understanding SSH commands and their security implications\n" +
               "- Explaining why certain commands are blocked or warned\n" +
               "- Suggesting safer alternatives for risky operations\n" +
               "- Providing guidance on best practices\n\n" +
               "Your query has been logged. Please try again in a moment.";
    }

    /**
     * Send a formatted agent response to the terminal
     */
    private void sendAgentResponse(String response, OutputStream terminalOutput) throws IOException {
        StringBuilder formattedResponse = new StringBuilder();
        formattedResponse.append("\r\n");
        formattedResponse.append("╔════════════════════════════════════════════════════════════════╗\r\n");
        formattedResponse.append("║                      AGENT RESPONSE                            ║\r\n");
        formattedResponse.append("╚════════════════════════════════════════════════════════════════╝\r\n");
        formattedResponse.append("\r\n");
        
        // Format multi-line response
        String[] lines = response.split("\n");
        for (String line : lines) {
            formattedResponse.append(line).append("\r\n");
        }
        
        formattedResponse.append("\r\n");
        
        terminalResponseService.sendMessage(formattedResponse.toString(), terminalOutput);
    }

    /**
     * Check if a command is an agent query
     */
    public boolean isAgentCommand(String command) {
        if (command == null || command.trim().isEmpty()) {
            return false;
        }
        
        String trimmed = command.trim();
        // Support multiple prefixes for agent commands
        return trimmed.startsWith("@agent ") || 
               trimmed.startsWith("/ask ") ||
               trimmed.equals("@agent") ||
               trimmed.equals("/ask");
    }

    /**
     * Extract the query from an agent command
     */
    public String extractQuery(String command) {
        String trimmed = command.trim();
        
        if (trimmed.startsWith("@agent ")) {
            return trimmed.substring("@agent ".length()).trim();
        } else if (trimmed.startsWith("/ask ")) {
            return trimmed.substring("/ask ".length()).trim();
        } else if (trimmed.equals("@agent") || trimmed.equals("/ask")) {
            return ""; // Empty query, should show help
        }
        
        return "";
    }

    /**
     * Send agent help message
     */
    public void sendAgentHelp(OutputStream terminalOutput) throws IOException {
        String help = "\r\n" +
            "╔════════════════════════════════════════════════════════════════╗\r\n" +
            "║                    AGENT ASSISTANCE                            ║\r\n" +
            "╚════════════════════════════════════════════════════════════════╝\r\n" +
            "\r\n" +
            "Ask questions and get help from the Sentrius agent:\r\n" +
            "\r\n" +
            "Usage:\r\n" +
            "  @agent <question>     - Ask the agent a question\r\n" +
            "  /ask <question>       - Alternative command prefix\r\n" +
            "\r\n" +
            "Examples:\r\n" +
            "  @agent How do I list all files in a directory?\r\n" +
            "  /ask What is the purpose of the chmod command?\r\n" +
            "  @agent Can you explain this error message?\r\n" +
            "\r\n";
        
        terminalResponseService.sendMessage(help, terminalOutput);
    }
}
