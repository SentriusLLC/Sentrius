package io.sentrius.sso.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import io.sentrius.sso.core.config.SystemOptions;
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
import io.sentrius.sso.core.services.documents.retrieval.QueryEnhancementService;
import io.sentrius.sso.core.utils.JsonUtil;
import io.sentrius.sso.genai.Message;
import io.sentrius.sso.genai.model.LLMRequest;
import io.sentrius.sso.protobuf.Session;
import lombok.extern.slf4j.Slf4j;
import org.apache.accumulo.access.AccessEvaluator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service for handling AI Support Agent interactions in web terminal sessions.
 * Integrates document search, TSG lookup, and intelligent assistance into the web UI.
 * Uses LLM proxy service for generating intelligent responses.
 */
@Slf4j
@Service
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class WebTerminalAISupportService {

    private final ChatService chatService;
    private final AgentExecutionService agentExecutionService;
    private final SystemOptions systemOptions;;

    private final DocumentService documentService;

    private final LLMService llmService;

    @Autowired(required = false)
    private QueryEnhancementService queryEnhancementService;
    
    @Value("${agent.support.include.documents:true}")
    private boolean includeDocuments;
    
    @Value("${agent.support.max.docs:3}")
    private int maxDocumentsInResponse;
    
    @Value("${agent.support.use.enhanced.search:true}")
    private boolean useEnhancedSearch;

    public WebTerminalAISupportService(
        ChatService chatService,
        AgentExecutionService agentExecutionService, SystemOptions systemOptions, DocumentService documentService,
        LLMService llmService
    ) {
        this.chatService = chatService;
        this.agentExecutionService = agentExecutionService;
        this.systemOptions = systemOptions;
        this.documentService = documentService;
        this.llmService = llmService;
    }

    /**
     * Set the query enhancement service (used for testing)
     */
    public void setQueryEnhancementService(QueryEnhancementService queryEnhancementService) {
        this.queryEnhancementService = queryEnhancementService;
    }

    /**
     * Process an agent query from web terminal
     * 
     * @param connectedSystem The connected system/session
     * @param query The user's query
     * @return Response message to send to user
     */
    public String processAgentQuery(ConnectedSystem connectedSystem, String query) {
        if (!systemOptions.getAgentSupportEnabled()) {
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
            log.info("Generating LLM response for AI Support Agent query");
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
            log.info("LLM service not available, using contextual help");
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
        
        return sanitizeForTerminal(response.toString());
    }
    
    /**
     * Generate LLM-based response using the LLM proxy service
     */
    private String generateLLMResponse(String query, String userId) throws ZtatException, JsonProcessingException {
        // Build TokenDTO for LLM service (only needs ztatToken and communicationId which can be empty for this use case)
        TokenDTO tokenDTO = TokenDTO.builder().communicationId(UUID.randomUUID().toString()).build();
        
        // Get relevant documents for context
        List<Document> docs = searchRelevantDocuments(query, userId);
        String documentContext = buildDocumentContext(docs);
        
        // Build prompt with document context
        //String prompt = buildLLMPrompt(query, documentContext);
        
        // Build LLM request

        List<Message> messages = new ArrayList<>();
        StringBuilder systemPrompt = new StringBuilder();
        systemPrompt.append("You are an AI assistant helping users with terminal commands and system administration. ");
        systemPrompt.append("Provide clear, concise, and helpful responses.");
        systemPrompt.append("Provide a helpful response in 2-3 sentences, focusing on practical guidance.");


        messages.add( Message.builder()
            .role("system")
            .content(systemPrompt.toString())
            .build());
        //systemPrompt.append("User query: ").append(query).append("\n\n");

        if (documentContext != null && !documentContext.isEmpty()) {
            messages.add( Message.builder()
                .role("system")
                .content(documentContext)
                .build());
        }
        messages.add( Message.builder()
            .role("user")
            .content(query)
            .build());


        
        LLMRequest request = LLMRequest.builder()
            .model("gpt-4o-mini")
            .messages(messages)
            .build();
        
        // Call LLM service
        String llmResponse = llmService.askQuestion(tokenDTO,systemOptions.getIntegrationProxyUrl(), request);

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

        JsonNode responseJson = JsonUtil.MAPPER.readTree(llmResponse);

        // Handle both old format (choices) and new format (output)
        JsonNode outputNode = responseJson.get("output");
        if (outputNode != null && outputNode.isArray() && outputNode.size() > 0) {
            // New format: output array
            JsonNode messageNode = outputNode.get(0);
            JsonNode contentArray = messageNode.get("content");
            if (contentArray != null && contentArray.isArray() && contentArray.size() > 0) {
                return contentArray.get(0).get("text").asText();
            }
        } else {
            // Old format: choices array
            JsonNode choicesNode = responseJson.get("choices");
            if (choicesNode != null && choicesNode.isArray() && choicesNode.size() > 0) {
                return choicesNode.get(0)
                    .get("message")
                    .get("content")
                    .asText();
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
     * Search for relevant documents based on query using enhanced RAG approach.
     * 
     * This implements proper RAG by:
     * 1. Extracting keywords from the query (e.g., "what type of agents" → ["type", "agents"])
     * 2. Performing disjunction search (OR) across extracted keywords
     * 3. Combining semantic vector search with keyword-based text search
     * 4. Ranking results by relevance
     * 
     * This addresses the issue where "/ask what type of agents exist in sentrius" 
     * previously failed to match documents that "/ask agents" would match.
     * 
     * @param query The user's natural language query
     * @param userId User ID for access control
     * @return List of relevant documents ranked by relevance
     */
    private List<Document> searchRelevantDocuments(String query, String userId) {
        if (documentService == null) {
            log.debug("DocumentService not available");
            return List.of();
        }
        
        try {
            // Build access evaluator for the user
            AccessEvaluator evaluator = documentService.buildAccessEvaluatorForUser(userId);
            
            // Use enhanced RAG search if available and enabled
            if (useEnhancedSearch && queryEnhancementService != null) {
                return performEnhancedRAGSearch(query, userId, evaluator);
            } else {
                // Fallback to basic search
                return performBasicSearch(query, userId, evaluator);
            }
            
        } catch (Exception e) {
            log.error("Failed to search documents", e);
            return List.of();
        }
    }
    
    /**
     * Perform enhanced RAG search with keyword extraction and query expansion.
     * This method implements the core RAG improvements.
     */
    private List<Document> performEnhancedRAGSearch(String query, String userId, AccessEvaluator evaluator) {
        log.info("Performing enhanced RAG search for query: '{}'", query);
        
        // Extract keywords from the query
        List<String> keywords = queryEnhancementService.extractKeywords(query);
        log.info("Extracted keywords: {}", keywords);
        
        // Generate multiple search queries using keyword expansion
        List<String> searchQueries = queryEnhancementService.generateSearchQueries(query);
        log.info("Generated {} search queries for disjunction", searchQueries.size());
        
        // Collect all matching documents using disjunction (OR logic)
        Set<Document> allResults = new LinkedHashSet<>();
        
        // Search with each query variation
        for (String searchQuery : searchQueries) {
            try {
                // First, try semantic search with TSGs
                DocumentSearchDTO searchDTO = DocumentSearchDTO.builder()
                    .query(searchQuery)
                    .useSemanticSearch(true)
                    .threshold(0.6)  // Lower threshold for better recall
                    .limit(5)
                    .documentType("TSG")
                    .build();
                
                List<Document> tsgResults = documentService.searchDocuments(searchDTO, userId, evaluator);
                if (!tsgResults.isEmpty()) {
                    log.debug("Found {} TSG results for query: '{}'", tsgResults.size(), searchQuery);
                    allResults.addAll(tsgResults);
                }
                
                // Also search all document types
                searchDTO.setDocumentType(null);
                List<Document> allTypeResults = documentService.searchDocuments(searchDTO, userId, evaluator);
                if (!allTypeResults.isEmpty()) {
                    log.debug("Found {} results (all types) for query: '{}'", allTypeResults.size(), searchQuery);
                    allResults.addAll(allTypeResults);
                }
                
                // If we already have good results, we can stop early
                if (allResults.size() >= 10) {
                    log.debug("Early stopping: collected {} documents", allResults.size());
                    break;
                }
            } catch (Exception e) {
                log.warn("Error searching with query '{}': {}", searchQuery, e.getMessage());
            }
        }
        
        // If semantic search didn't yield results, try text-based keyword search
        if (allResults.isEmpty() && !keywords.isEmpty()) {
            log.info("Semantic search returned no results, trying keyword-based text search");
            for (String keyword : keywords) {
                try {
                    DocumentSearchDTO textSearch = DocumentSearchDTO.builder()
                        .query(keyword)
                        .useSemanticSearch(false)  // Force text search
                        .limit(3)
                        .build();
                    
                    List<Document> keywordResults = documentService.searchDocuments(textSearch, userId, evaluator);
                    allResults.addAll(keywordResults);
                } catch (Exception e) {
                    log.warn("Error in keyword search for '{}': {}", keyword, e.getMessage());
                }
            }
        }
        
        // Rank results by keyword relevance
        List<Document> rankedResults = allResults.stream()
            .sorted((d1, d2) -> {
                // Calculate relevance scores
                String doc1Text = buildDocumentText(d1);
                String doc2Text = buildDocumentText(d2);
                
                double score1 = queryEnhancementService.calculateKeywordRelevance(keywords, doc1Text);
                double score2 = queryEnhancementService.calculateKeywordRelevance(keywords, doc2Text);
                
                return Double.compare(score2, score1);  // Descending order
            })
            .limit(5)  // Return top 5 results
            .collect(Collectors.toList());
        
        log.info("Enhanced RAG search found {} relevant documents (from {} total matches)", 
                rankedResults.size(), allResults.size());
        
        return rankedResults;
    }
    
    /**
     * Perform basic search (original implementation).
     * Used as fallback when enhanced search is disabled.
     */
    private List<Document> performBasicSearch(String query, String userId, AccessEvaluator evaluator) {
        log.info("Performing basic search for query: '{}'", query);
        
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
        
        log.info("Basic search found {} relevant documents", results.size());
        return results;
    }
    
    /**
     * Build combined text from document for relevance calculation
     */
    private String buildDocumentText(Document doc) {
        StringBuilder text = new StringBuilder();
        
        if (doc.getDocumentName() != null) {
            text.append(doc.getDocumentName()).append(" ");
        }
        
        if (doc.getSummary() != null) {
            text.append(doc.getSummary()).append(" ");
        }
        
        if (doc.getContent() != null) {
            // Limit content length for performance
            String content = doc.getContent();
            if (content.length() > 500) {
                content = content.substring(0, 500);
            }
            text.append(content);
        }
        
        return text.toString();
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
        if (!systemOptions.getAgentSupportEnabled() || command == null || command.trim().isEmpty()) {
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
        if (!systemOptions.getAgentSupportEnabled()) {
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

    public boolean isEnabled(){
        return systemOptions.getAgentSupportEnabled();
    }

    public static String sanitizeForTerminal(String input) {
        if (input == null) return null;

        return "\r\n" +
            sanitizeContent(input)
                .replace("\r\n", "\n")  // normalize mixed input
                .replace("\n", "\r\n")  // TERMINAL-SAFE
            + "\r\n";
    }

    public static String sanitizeContent(String input) {
        if (input == null) return null;

        return input
            // Replace Unicode bullets
            .replace("•", "-")

            // Replace emojis & symbols
            .replaceAll("[📚💡⚠️❗❓]", "")

            // Replace smart quotes
            .replace("“", "\"")
            .replace("”", "\"")
            .replace("’", "'")

            // Replace em/en dashes
            .replace("–", "-")
            .replace("—", "-")

            // Strip remaining non-ASCII (keep LF for now)
            .replaceAll("[^\\x20-\\x7E\\n\\t]", "");
    }

}
