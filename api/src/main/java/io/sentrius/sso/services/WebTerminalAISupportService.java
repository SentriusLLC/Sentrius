package io.sentrius.sso.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.sentrius.sso.core.dto.UserDTO;
import io.sentrius.sso.core.dto.agents.AgentExecution;
import io.sentrius.sso.core.dto.documents.DocumentSearchDTO;
import io.sentrius.sso.core.dto.ztat.TokenDTO;
import io.sentrius.sso.core.exceptions.ZtatException;
import io.sentrius.sso.core.model.ConnectedSystem;
import io.sentrius.sso.core.model.chat.ChatLog;
import io.sentrius.sso.core.model.documents.Document;
import io.sentrius.sso.core.model.users.User;
import io.sentrius.sso.core.services.ChatService;
import io.sentrius.sso.core.services.agents.AgentExecutionService;
import io.sentrius.sso.core.services.agents.LLMService;
import io.sentrius.sso.core.services.documents.DocumentService;
import io.sentrius.sso.core.utils.JsonUtil;
import io.sentrius.sso.genai.Message;
import io.sentrius.sso.genai.model.LLMRequest;
import io.sentrius.sso.protobuf.Session;
import lombok.extern.slf4j.Slf4j;
import org.apache.accumulo.access.AccessEvaluator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for handling AI Support Agent interactions in web terminal sessions.
 * Integrates document search, TSG lookup, and intelligent assistance into the web UI.
 * Uses LLM proxy service for generating intelligent responses.
 */
@Slf4j
@Service
public class WebTerminalAISupportService {

    private final ChatService chatService;
    private final AgentExecutionService agentExecutionService;
    
    @Autowired(required = false)
    private DocumentService documentService;
    
    @Autowired(required = false)
    private LLMService llmService;
    
    @Value("${agent.support.enabled:true}")
    private boolean agentSupportEnabled;
    
    @Value("${agent.support.include.documents:true}")
    private boolean includeDocuments;
    
    @Value("${agent.support.max.docs:3}")
    private int maxDocumentsInResponse;

    public WebTerminalAISupportService(
        ChatService chatService,
        AgentExecutionService agentExecutionService
    ) {
        this.chatService = chatService;
        this.agentExecutionService = agentExecutionService;
    }

    /**
     * Process an agent query from web terminal
     * 
     * @param connectedSystem The connected system/session
     * @param query The user's query
     * @return Response message to send to user
     */
    public String processAgentQuery(ConnectedSystem connectedSystem, String query) {
        if (!agentSupportEnabled) {
            log.debug("AI Support Agent is disabled");
            return "AI Support Agent is currently disabled.";
        }
        
        try {
            User user = connectedSystem.getUser();
            
            // Get or create agent execution context
            UserDTO userDTO = UserDTO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .emailAddress(user.getEmailAddress() != null ? user.getEmailAddress() : "")
                .build();
            
            AgentExecution execution = agentExecutionService.getAgentExecution(userDTO);
            String chatGroupId = execution.getExecutionId();
            
            // Log the user's query
            ChatLog userMessage = ChatLog.builder()
                .session(connectedSystem.getSession())
                .chatGroupId(chatGroupId)
                .sender(user.getUsername())
                .message(query)
                .messageTimestamp(LocalDateTime.now())
                .build();
            chatService.save(userMessage);
            
            // Generate response with document context
            String response = generateAgentResponse(query, user.getId().toString());
            
            // Log the agent's response
            ChatLog agentMessage = ChatLog.builder()
                .session(connectedSystem.getSession())
                .chatGroupId(chatGroupId)
                .sender("ai-support-agent")
                .message(response)
                .messageTimestamp(LocalDateTime.now())
                .build();
            chatService.save(agentMessage);
            
            return response;
            
        } catch (Exception e) {
            log.error("Error processing agent query", e);
            return "Sorry, I encountered an error processing your request. Please try again.";
        }
    }

    /**
     * Generate an intelligent response using LLM service and document search
     */
    private String generateAgentResponse(String query, String userId) {
        StringBuilder response = new StringBuilder();
        
        // Use LLM service to generate intelligent response if available
        if (llmService != null) {
            try {
                String llmResponse = generateLLMResponse(query, userId);
                if (llmResponse != null && !llmResponse.isEmpty()) {
                    response.append(llmResponse).append("\n\n");
                } else {
                    // Fallback to contextual help if LLM fails
                    response.append(generateContextualHelp(query)).append("\n\n");
                }
            } catch (ZtatException | JsonProcessingException e) {
                log.error("Failed to generate LLM response, falling back to contextual help", e);
                response.append(generateContextualHelp(query)).append("\n\n");
            }
        } else {
            // Use contextual help if LLM service not available
            response.append(generateContextualHelp(query)).append("\n\n");
        }
        
        // Search for relevant documents if enabled
        if (includeDocuments && documentService != null) {
            List<Document> relevantDocs = searchRelevantDocuments(query, userId);
            
            if (!relevantDocs.isEmpty()) {
                response.append("📚 Relevant Documentation:\n");
                
                for (int i = 0; i < Math.min(relevantDocs.size(), maxDocumentsInResponse); i++) {
                    Document doc = relevantDocs.get(i);
                    response.append(String.format("\n%d. %s (%s)\n",
                        i + 1,
                        doc.getDocumentName(),
                        doc.getDocumentType()
                    ));
                    
                    if (doc.getSummary() != null && !doc.getSummary().isEmpty()) {
                        response.append("   ").append(doc.getSummary()).append("\n");
                    }
                }
                
                response.append("\nYou can access these documents through the documentation portal.\n");
            }
        }
        
        return response.toString();
    }
    
    /**
     * Generate LLM-based response using the LLM proxy service
     */
    private String generateLLMResponse(String query, String userId) throws ZtatException, JsonProcessingException {
        // Build TokenDTO for LLM service (only needs ztatToken and communicationId which can be empty for this use case)
        TokenDTO tokenDTO = TokenDTO.builder().build();
        
        // Get relevant documents for context
        List<Document> docs = searchRelevantDocuments(query, userId);
        String documentContext = buildDocumentContext(docs);
        
        // Build prompt with document context
        String prompt = buildLLMPrompt(query, documentContext);
        
        // Build LLM request
        Message message = Message.builder()
            .role("user")
            .content(prompt)
            .build();
        
        LLMRequest request = LLMRequest.builder()
            .model("gpt-4o-mini")
            .messages(List.of(message))
            .maxTokens(400)
            .build();
        
        // Call LLM service
        String llmResponse = llmService.askQuestion(tokenDTO, request);
        
        // Parse and return response
        return parseLLMResponse(llmResponse);
    }
    
    /**
     * Build document context string for LLM prompt
     */
    private String buildDocumentContext(List<Document> docs) {
        if (docs.isEmpty()) {
            return "";
        }
        
        StringBuilder context = new StringBuilder();
        context.append("Available documentation:\n");
        
        for (Document doc : docs.subList(0, Math.min(docs.size(), maxDocumentsInResponse))) {
            context.append("- ").append(doc.getDocumentName());
            if (doc.getSummary() != null && !doc.getSummary().isEmpty()) {
                context.append(": ").append(doc.getSummary());
            }
            context.append("\n");
        }
        
        return context.toString();
    }
    
    /**
     * Build LLM prompt with query and document context
     */
    private String buildLLMPrompt(String query, String documentContext) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are an AI assistant helping users with terminal commands and system administration. ");
        prompt.append("Provide clear, concise, and helpful responses.\n\n");
        prompt.append("User query: ").append(query).append("\n\n");
        
        if (documentContext != null && !documentContext.isEmpty()) {
            prompt.append(documentContext).append("\n");
        }
        
        prompt.append("Provide a helpful response in 2-3 sentences, focusing on practical guidance.");
        
        return prompt.toString();
    }
    
    /**
     * Parse LLM response to extract text
     */
    private String parseLLMResponse(String llmResponse) throws JsonProcessingException {
        if (llmResponse == null || llmResponse.isEmpty()) {
            return null;
        }
        
        var response = JsonUtil.MAPPER.readTree(llmResponse);
        var choices = response.get("choices");
        if (choices != null && choices.isArray() && choices.size() > 0) {
            var message = choices.get(0).get("message");
            if (message != null) {
                var content = message.get("content");
                if (content != null) {
                    return content.asText();
                }
            }
        }
        
        return null;
    }

    /**
     * Generate contextual help based on query content
     */
    private String generateContextualHelp(String query) {
        String lowerQuery = query.toLowerCase();
        
        // Detect what the user is asking about
        if (lowerQuery.contains("list") || lowerQuery.contains("ls") || lowerQuery.contains("files")) {
            return "To list files and directories:\n" +
                   "• ls - List files in current directory\n" +
                   "• ls -la - List all files with details\n" +
                   "• ls -lh - List with human-readable file sizes\n" +
                   "• find . -name \"pattern\" - Search for files by name";
        }
        
        if (lowerQuery.contains("permission") || lowerQuery.contains("chmod") || lowerQuery.contains("access")) {
            return "Managing file permissions:\n" +
                   "• chmod 644 file - Read/write for owner, read for others\n" +
                   "• chmod 755 file - Execute permissions for owner\n" +
                   "• chown user:group file - Change ownership\n" +
                   "⚠️  Be careful with permission changes - they affect security!";
        }
        
        if (lowerQuery.contains("delete") || lowerQuery.contains("rm") || lowerQuery.contains("remove")) {
            return "⚠️  File deletion commands:\n" +
                   "• rm file - Delete a file (use with caution!)\n" +
                   "• rm -i file - Interactive deletion (asks for confirmation)\n" +
                   "• rm -r directory - Delete directory and contents\n" +
                   "💡 Tip: Always verify the path before deleting. Consider using trash or backup first.";
        }
        
        if (lowerQuery.contains("docker") || lowerQuery.contains("container")) {
            return "Docker container management:\n" +
                   "• docker ps - List running containers\n" +
                   "• docker logs <container> - View container logs\n" +
                   "• docker exec -it <container> bash - Access container shell\n" +
                   "• docker-compose up -d - Start services in background";
        }
        
        if (lowerQuery.contains("kubectl") || lowerQuery.contains("kubernetes") || lowerQuery.contains("k8s")) {
            return "Kubernetes management:\n" +
                   "• kubectl get pods - List pods\n" +
                   "• kubectl describe pod <name> - Get pod details\n" +
                   "• kubectl logs <pod> - View pod logs\n" +
                   "• kubectl exec -it <pod> -- bash - Access pod shell";
        }
        
        if (lowerQuery.contains("service") || lowerQuery.contains("systemctl")) {
            return "Service management with systemctl:\n" +
                   "• systemctl status service - Check service status\n" +
                   "• systemctl start service - Start a service\n" +
                   "• systemctl restart service - Restart a service\n" +
                   "• systemctl enable service - Enable service at boot";
        }
        
        if (lowerQuery.contains("network") || lowerQuery.contains("connection") || lowerQuery.contains("port")) {
            return "Network troubleshooting:\n" +
                   "• netstat -tulpn - Show listening ports\n" +
                   "• ss -tulpn - Modern alternative to netstat\n" +
                   "• ping host - Test connectivity\n" +
                   "• curl -I url - Test HTTP endpoint";
        }
        
        if (lowerQuery.contains("process") || lowerQuery.contains("cpu") || lowerQuery.contains("memory")) {
            return "Process and resource monitoring:\n" +
                   "• top - Interactive process viewer\n" +
                   "• htop - Enhanced process viewer (if installed)\n" +
                   "• ps aux - List all processes\n" +
                   "• free -h - Show memory usage";
        }
        
        // Generic help
        return "I'm your AI assistant for terminal commands and system administration.\n" +
               "I can help you with:\n" +
               "• File and directory management\n" +
               "• Permission and security issues\n" +
               "• Container and Kubernetes operations\n" +
               "• Service management\n" +
               "• Network troubleshooting\n" +
               "• Finding relevant documentation and TSGs\n\n" +
               "Ask me specific questions about commands or operations you'd like to perform!";
    }

    /**
     * Search for relevant documents based on query
     */
    private List<Document> searchRelevantDocuments(String query, String userId) {
        if (documentService == null) {
            log.debug("DocumentService not available");
            return List.of();
        }
        
        try {
            // Build access evaluator for the user
            AccessEvaluator evaluator = documentService.buildAccessEvaluatorForUser(userId);
            
            // Search with semantic search for better context matching
            DocumentSearchDTO searchDTO = DocumentSearchDTO.builder()
                .query(query)
                .useSemanticSearch(true)
                .threshold(0.7)
                .limit(5)
                .build();
            
            // First try to find TSGs
            searchDTO.setDocumentType("TSG");
            List<Document> results = documentService.searchDocuments(searchDTO, userId, evaluator);
            
            // If no TSGs found, search all document types
            if (results.isEmpty()) {
                searchDTO.setDocumentType(null);
                results = documentService.searchDocuments(searchDTO, userId, evaluator);
            }
            
            log.info("Found {} relevant documents for AI Support Agent query", results.size());
            return results;
            
        } catch (Exception e) {
            log.error("Failed to search documents", e);
            return List.of();
        }
    }

    /**
     * Send agent message to web terminal via websocket
     */
    public void sendAgentMessageToTerminal(WebSocketSession webSocketSession, String message, String sender) {
        try {
            // Create a ChatMessage protobuf
            Session.ChatMessage.Builder chatMessage = Session.ChatMessage.newBuilder()
                .setSender(sender != null ? sender : "ai-support-agent")
                .setMessage(message);
            
            // Serialize and encode
            byte[] messageBytes = chatMessage.build().toByteArray();
            String base64Message = Base64.getEncoder().encodeToString(messageBytes);
            
            // Send via websocket
            if (webSocketSession != null && webSocketSession.isOpen()) {
                webSocketSession.sendMessage(new TextMessage(base64Message));
                log.debug("Sent AI Support Agent message to web terminal");
            }
            
        } catch (IOException e) {
            log.error("Failed to send agent message to web terminal", e);
        }
    }

    /**
     * Check if a command should trigger AI assistance
     */
    public boolean shouldOfferAssistance(String command) {
        if (!agentSupportEnabled || command == null || command.trim().isEmpty()) {
            return false;
        }
        
        String lowerCommand = command.toLowerCase();
        
        // Commands that often need help
        return lowerCommand.contains("find") ||
               lowerCommand.contains("awk") ||
               lowerCommand.contains("sed") ||
               lowerCommand.contains("grep") ||
               lowerCommand.contains("xargs") ||
               lowerCommand.contains("docker") ||
               lowerCommand.contains("kubectl") ||
               // Potentially dangerous commands
               lowerCommand.contains("rm ") ||
               lowerCommand.contains("dd ") ||
               lowerCommand.contains("mkfs") ||
               lowerCommand.contains("fdisk");
    }

    /**
     * Generate a proactive suggestion for a command
     */
    public String generateProactiveSuggestion(String command) {
        if (!agentSupportEnabled) {
            return null;
        }
        
        String lowerCommand = command.toLowerCase();
        
        if (lowerCommand.contains("rm ") && (lowerCommand.contains("-rf") || lowerCommand.contains("/*"))) {
            return "⚠️ This rm command is potentially dangerous. Would you like to see safer alternatives?";
        }
        
        if (lowerCommand.contains("dd ") || lowerCommand.contains("mkfs")) {
            return "⚠️ This command can cause data loss. Would you like to review documentation first?";
        }
        
        if (lowerCommand.contains("docker") || lowerCommand.contains("kubectl")) {
            return "💡 I can help with container management. Type '@agent' for assistance.";
        }
        
        if (lowerCommand.contains("find") && lowerCommand.split("\\|").length > 2) {
            return "💡 This looks like a complex command. Need help understanding what it does?";
        }
        
        return null;
    }
}
