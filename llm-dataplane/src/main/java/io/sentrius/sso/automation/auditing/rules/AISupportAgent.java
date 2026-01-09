package io.sentrius.sso.automation.auditing.rules;

import io.sentrius.sso.automation.auditing.SessionTokenEvaluator;
import io.sentrius.sso.automation.auditing.Trigger;
import io.sentrius.sso.automation.auditing.TriggerAction;
import io.sentrius.sso.core.config.SystemOptions;
import io.sentrius.sso.core.dto.documents.DocumentSearchDTO;
import io.sentrius.sso.core.model.ConnectedSystem;
import io.sentrius.sso.core.model.documents.Document;
import io.sentrius.sso.core.services.documents.DocumentService;
import io.sentrius.sso.core.services.documents.retrieval.QueryEnhancementService;
import io.sentrius.sso.core.services.terminal.SessionTrackingService;
import io.sentrius.sso.protobuf.Session;
import lombok.extern.slf4j.Slf4j;
import org.apache.accumulo.access.AccessEvaluator;

import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * AI Support Agent - A pluggable feature that provides intelligent assistance
 * to users during SSH sessions by analyzing commands and suggesting helpful
 * documentation, TSGs, and best practices.
 * 
 * This agent:
 * - Monitors commands for potential issues or learning opportunities
 * - Uses LLM proxy service via AISupportLLMService to generate intelligent suggestions
 * - Searches documentation and TSGs for relevant information
 * - Proactively offers suggestions via chat dialog
 * - Responds to explicit @agent queries with context-aware help
 * - Integrates with the existing SSH agent infrastructure
 */
@Slf4j
public class AISupportAgent extends SessionTokenEvaluator {

    private static final String CLASS_NAME = AISupportAgent.class.getName();
    
    private ConnectedSystem connectedSystem;
    private SessionTrackingService sessionTrackingService;
    private DocumentService documentService;
    private QueryEnhancementService queryEnhancementService;
    
    // Configuration options
    private boolean enabled = true;
    private boolean proactiveMode = true;
    private int commandBufferSize = 5;
    private double suggestionThreshold = 0.7;
    private boolean useEnhancedSearch = true;
    
    // Command buffer for context analysis
    private final Queue<String> recentCommands = new LinkedList<>();
    
    // Patterns to detect commands that might need help
    private static final Pattern COMPLEX_COMMAND_PATTERN = Pattern.compile(
        ".*\\b(find|grep|awk|sed|xargs|tar|docker|kubectl|systemctl)\\b.*",
        Pattern.CASE_INSENSITIVE
    );
    
    private static final Pattern ERROR_PRONE_PATTERN = Pattern.compile(
        ".*\\b(rm|dd|mkfs|fdisk|parted|chmod|chown)\\b.*",
        Pattern.CASE_INSENSITIVE
    );
    
    // Patterns for detecting common command mistakes
    private static final Pattern CHOWN_WITH_NUMERIC = Pattern.compile(
        ".*\\bchown\\s+\\d{3,4}\\b.*",
        Pattern.CASE_INSENSITIVE
    );
    
    private static final Pattern CHMOD_WITH_USER = Pattern.compile(
        ".*\\bchmod\\s+[a-zA-Z]+:[a-zA-Z]+\\b.*",
        Pattern.CASE_INSENSITIVE
    );
    
    // Patterns for detecting file operations
    private static final Pattern FILE_CREATION = Pattern.compile(
        ".*\\b(touch|echo.*>|cat.*>|vim|nano|vi|cp|mv)\\b.*",
        Pattern.CASE_INSENSITIVE
    );
    
    private static final Pattern PERMISSION_CHANGE = Pattern.compile(
        ".*\\b(chmod|chown|chgrp)\\b.*",
        Pattern.CASE_INSENSITIVE
    );
    
    @Override
    public void setConnectedSystem(ConnectedSystem connectedSystem) {
        this.connectedSystem = connectedSystem;
    }

    @Override
    public void setTrackingService(SessionTrackingService sessionTrackingService) {
        this.sessionTrackingService = sessionTrackingService;
    }

    public void setDocumentService(DocumentService documentService) {
        this.documentService = documentService;
    }

    public void setQueryEnhancementService(QueryEnhancementService queryEnhancementService) {
        this.queryEnhancementService = queryEnhancementService;
    }

    @Override
    public Optional<Trigger> trigger(String cmd) {
        if (!enabled || cmd == null || cmd.trim().isEmpty()) {
            return Optional.of(new Trigger(TriggerAction.NO_ACTION, ""));
        }
        
        String command = cmd.trim();
        
        // Add to recent commands for context
        if (recentCommands.size() >= commandBufferSize) {
            recentCommands.poll();
        }
        recentCommands.offer(command);
        
        // Only provide proactive suggestions if enabled
        if (!proactiveMode) {
            return Optional.of(new Trigger(TriggerAction.NO_ACTION, ""));
        }
        
        // First, check for immediate command mistakes (like chown 755 instead of chmod 755)
        // This doesn't require LLM service
        String mistakeDetection = detectCommonMistakes(command);
        if (mistakeDetection != null) {
            log.info("AI Support Agent detected potential mistake: {}", command);
            return Optional.of(new Trigger(
                TriggerAction.PROMPT_ACTION,
                "⚠️ Potential command mistake detected",
                mistakeDetection
            ));
        }
        
        // Check if AI Support LLM service is available for more advanced analysis
        var aiSupportService = pluggableServices.get("aisupport");
        if (aiSupportService == null || !aiSupportService.isEnabled()) {
            log.debug("AI Support LLM service not available - only basic mistake detection active");
            return Optional.of(new Trigger(TriggerAction.NO_ACTION, ""));
        }
        
        // Then check for context-based issues (e.g., permission changes after file creation)
        String contextIssue = analyzeCommandContext(command);
        if (contextIssue != null) {
            try {
                String context = buildContextSummary();
                String suggestion = generateLLMSuggestion(command, context, aiSupportService);
                
                if (suggestion != null && !suggestion.isEmpty()) {
                    log.info("AI Support Agent offering context-based help for command: {}", command);
                    return Optional.of(new Trigger(
                        TriggerAction.PROMPT_ACTION,
                        "💡 AI Assistant noticed your recent activity",
                        suggestion
                    ));
                }
            } catch (Exception e) {
                log.error("Failed to generate context-based suggestion", e);
            }
        }
        
        // Detect commands that might benefit from general assistance
        if (shouldOfferHelp(command)) {
            try {
                String context = buildContextSummary();
                String suggestion = generateLLMSuggestion(command, context, aiSupportService);
                
                if (suggestion != null && !suggestion.isEmpty()) {
                    log.info("AI Support Agent offering proactive help for command: {}", command);
                    return Optional.of(new Trigger(
                        TriggerAction.PROMPT_ACTION,
                        "AI Assistant has suggestions for this command",
                        suggestion
                    ));
                }
            } catch (Exception e) {
                log.error("Failed to generate LLM suggestion", e);
            }
        }
        
        return Optional.of(new Trigger(TriggerAction.NO_ACTION, ""));
    }

    @Override
    public boolean configure(SystemOptions systemOptions, String configuration) {
        // Parse configuration if provided
        if (configuration != null && !configuration.isEmpty()) {
            try {
                // Configuration format: "enabled=true;proactiveMode=true;bufferSize=5;threshold=0.7"
                String[] parts = configuration.split(";");
                for (String part : parts) {
                    String[] kv = part.split("=");
                    if (kv.length == 2) {
                        switch (kv[0].trim()) {
                            case "enabled":
                                enabled = Boolean.parseBoolean(kv[1].trim());
                                break;
                            case "proactiveMode":
                                proactiveMode = Boolean.parseBoolean(kv[1].trim());
                                break;
                            case "bufferSize":
                                commandBufferSize = Integer.parseInt(kv[1].trim());
                                break;
                            case "threshold":
                                suggestionThreshold = Double.parseDouble(kv[1].trim());
                                break;
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Failed to parse AI Support Agent configuration: {}", configuration, e);
                return false;
            }
        }
        
        log.info("AI Support Agent configured: enabled={}, proactiveMode={}, bufferSize={}, threshold={}",
            enabled, proactiveMode, commandBufferSize, suggestionThreshold);
        return true;
    }

    @Override
    public TriggerAction describeAction() {
        return TriggerAction.PROMPT_ACTION;
    }

    @Override
    public boolean requiresSanitized() {
        return false;
    }

    @Override
    public Optional<Trigger> onMessage(Session.TerminalMessage text) {
        // Handle user responses to agent prompts
        if (text.getType() == Session.MessageType.USER_PROMPT) {
            log.info("AI Support Agent received user response to prompt");
            // User has responded to our suggestion, acknowledge and continue
            return Optional.of(new Trigger(TriggerAction.NO_ACTION, ""));
        }
        
        // No special handling needed for other message types
        return Optional.of(new Trigger(TriggerAction.NO_ACTION, ""));
    }

    @Override
    public boolean isOnlySessionRule() {
        return false;
    }

    @Override
    public boolean onFullCommand() {
        return true;
    }
    
    /**
     * Determine if a command warrants proactive help
     */
    private boolean shouldOfferHelp(String command) {
        // Complex commands that users often struggle with
        if (COMPLEX_COMMAND_PATTERN.matcher(command).matches()) {
            return true;
        }
        
        // Potentially dangerous commands
        if (ERROR_PRONE_PATTERN.matcher(command).matches()) {
            return true;
        }
        
        // Unusual command patterns (very long commands, multiple pipes, etc.)
        if (command.length() > 100 || command.split("\\|").length > 3) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Detect common command mistakes immediately
     * Returns a suggestion if a mistake is detected, null otherwise
     */
    private String detectCommonMistakes(String command) {
        // Detect chown with numeric permissions (should be chmod)
        if (CHOWN_WITH_NUMERIC.matcher(command).matches()) {
            return "⚠️ Did you mean `chmod` instead of `chown`?\n\n" +
                   "• `chown` changes file ownership (e.g., chown user:group file)\n" +
                   "• `chmod` changes file permissions (e.g., chmod 755 file)\n\n" +
                   "Your command appears to use numeric permissions with chown, which typically indicates you meant chmod.";
        }
        
        // Detect chmod with user:group format (should be chown)
        if (CHMOD_WITH_USER.matcher(command).matches()) {
            return "⚠️ Did you mean `chown` instead of `chmod`?\n\n" +
                   "• `chmod` changes file permissions (e.g., chmod 755 file)\n" +
                   "• `chown` changes file ownership (e.g., chown user:group file)\n\n" +
                   "Your command appears to use user:group format with chmod, which typically indicates you meant chown.";
        }
        
        return null;
    }
    
    /**
     * Analyze command context to detect workflow-based issues
     * Returns a message if an issue is detected, null otherwise
     */
    private String analyzeCommandContext(String command) {
        if (recentCommands.size() < 2) {
            return null; // Need at least 2 commands for context analysis
        }
        
        // Check if user recently created files and is now changing permissions
        boolean hasRecentFileCreation = false;
        for (String recentCmd : recentCommands) {
            if (FILE_CREATION.matcher(recentCmd).matches()) {
                hasRecentFileCreation = true;
                break;
            }
        }
        
        // If user is changing permissions after creating files, flag for LLM analysis
        if (hasRecentFileCreation && PERMISSION_CHANGE.matcher(command).matches()) {
            return "permission_after_creation";
        }
        
        return null;
    }
    
    /**
     * Build a summarized context from command history for LLM
     */
    private String buildContextSummary() {
        if (recentCommands.isEmpty()) {
            return "";
        }
        
        StringBuilder context = new StringBuilder();
        context.append("Recent command history:\n");
        
        int cmdNum = 1;
        for (String cmd : recentCommands) {
            context.append(cmdNum++).append(". ").append(cmd).append("\n");
        }
        
        // Add activity summary
        boolean hasFileOps = false;
        boolean hasPermOps = false;
        
        for (String cmd : recentCommands) {
            if (FILE_CREATION.matcher(cmd).matches()) {
                hasFileOps = true;
            }
            if (PERMISSION_CHANGE.matcher(cmd).matches()) {
                hasPermOps = true;
            }
        }
        
        if (hasFileOps && hasPermOps) {
            context.append("\nNote: User has been creating files and modifying permissions.");
        } else if (hasFileOps) {
            context.append("\nNote: User has been creating or modifying files.");
        } else if (hasPermOps) {
            context.append("\nNote: User has been modifying file permissions.");
        }
        
        return context.toString();
    }
    
    /**
     * Generate a proactive suggestion using AI Support LLM service with document context
     */
    private String generateLLMSuggestion(String command, String context, Object aiSupportService) throws ExecutionException, InterruptedException {
        if (!(aiSupportService instanceof io.sentrius.sso.core.services.openai.AISupportLLMService)) {
            return null;
        }
        
        io.sentrius.sso.core.services.openai.AISupportLLMService service = 
            (io.sentrius.sso.core.services.openai.AISupportLLMService) aiSupportService;
        
        // Search for relevant documents to enrich context
        String enhancedContext = context;
        if (documentService != null && connectedSystem != null) {
            try {
                String userId = connectedSystem.getUser().getUserId();
                List<Document> relevantDocs = searchRelevantDocs(command, userId);
                
                if (!relevantDocs.isEmpty()) {
                    String docContext = buildDocumentContext(relevantDocs);
                    enhancedContext = context + "\n\n" + docContext;
                    log.debug("Enhanced LLM context with {} relevant documents", relevantDocs.size());
                }
            } catch (Exception e) {
                log.warn("Failed to search documents for context enrichment, continuing without docs", e);
            }
        }
        
        var responseFuture = service.generateSuggestion(command, enhancedContext);
        return responseFuture.get();
    }
    
    /**
     * Build document context string for LLM prompt (RAG approach)
     */
    private String buildDocumentContext(List<Document> docs) {
        if (docs.isEmpty()) {
            return "";
        }
        
        StringBuilder context = new StringBuilder();
        context.append("Relevant documentation available:\n");
        
        int count = 0;
        for (Document doc : docs) {
            if (count >= 3) break; // Limit to top 3 for prompt efficiency
            
            context.append("- ").append(doc.getDocumentName());
            context.append(" (").append(doc.getDocumentType()).append(")");
            
            if (doc.getSummary() != null && !doc.getSummary().isEmpty()) {
                // Truncate summary if too long
                String summary = doc.getSummary();
                if (summary.length() > 150) {
                    summary = summary.substring(0, 147) + "...";
                }
                context.append(": ").append(summary);
            }
            context.append("\n");
            count++;
        }
        
        context.append("\nYou may reference these documents in your suggestions if relevant.");
        
        return context.toString();
    }
    
    /**
     * Search for relevant documentation based on command or query.
     * Uses semantic search to find TSGs and documentation that can help
     * users understand commands or resolve issues.
     * 
     * This implements an enhanced RAG (Retrieval-Augmented Generation) approach where:
     * 1. Query is expanded and keywords are extracted
     * 2. Multiple search queries are generated using disjunction (OR) logic
     * 3. Documents are retrieved using semantic + keyword-based search
     * 4. Document summaries are injected into LLM prompts
     * 5. LLM generates responses informed by relevant documentation
     * 
     * This fixes the issue where "/ask what type of agents exist in sentrius" would
     * fail to match documents that "/ask agents" would match.
     * 
     * @param query The command or query to search for
     * @param userId User ID for access control
     * @return List of relevant documents accessible to the user
     */
    public List<Document> searchRelevantDocs(String query, String userId) {
        if (documentService == null) {
            log.warn("DocumentService not available for AI Support Agent");
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
            log.error("Failed to search documents for AI Support Agent", e);
            return List.of();
        }
    }
    
    /**
     * Perform enhanced RAG search with keyword extraction and query expansion.
     */
    private List<Document> performEnhancedRAGSearch(String query, String userId, AccessEvaluator evaluator) {
        log.info("AI Support Agent performing enhanced RAG search for: '{}'", query);
        
        // Extract keywords from the query
        List<String> keywords = queryEnhancementService.extractKeywords(query);
        log.debug("Extracted keywords: {}", keywords);
        
        // Generate multiple search queries
        List<String> searchQueries = queryEnhancementService.generateSearchQueries(query);
        log.debug("Generated {} search queries", searchQueries.size());
        
        // Collect all matching documents using disjunction (OR logic)
        Set<Document> allResults = new LinkedHashSet<>();
        
        // Search with each query variation
        for (String searchQuery : searchQueries) {
            try {
                // Search TSGs first with lower threshold for better recall
                DocumentSearchDTO searchDTO = DocumentSearchDTO.builder()
                    .query(searchQuery)
                    .useSemanticSearch(true)
                    .threshold(Math.max(0.5, suggestionThreshold - 0.2))  // Lower threshold
                    .limit(5)
                    .documentType("TSG")
                    .build();
                
                List<Document> tsgResults = documentService.searchDocuments(searchDTO, userId, evaluator);
                allResults.addAll(tsgResults);
                
                // Also search all document types
                searchDTO.setDocumentType(null);
                List<Document> allTypeResults = documentService.searchDocuments(searchDTO, userId, evaluator);
                allResults.addAll(allTypeResults);
                
                // Early stopping if we have enough results
                if (allResults.size() >= 10) {
                    break;
                }
            } catch (Exception e) {
                log.warn("Error searching with query '{}': {}", searchQuery, e.getMessage());
            }
        }
        
        // If no results, try keyword-based text search
        if (allResults.isEmpty() && !keywords.isEmpty()) {
            log.info("Semantic search returned no results, trying keyword-based search");
            for (String keyword : keywords) {
                try {
                    DocumentSearchDTO textSearch = DocumentSearchDTO.builder()
                        .query(keyword)
                        .useSemanticSearch(false)
                        .limit(3)
                        .build();
                    
                    List<Document> keywordResults = documentService.searchDocuments(textSearch, userId, evaluator);
                    allResults.addAll(keywordResults);
                } catch (Exception e) {
                    log.warn("Error in keyword search for '{}': {}", keyword, e.getMessage());
                }
            }
        }
        
        // Rank and return top results
        List<Document> rankedResults = allResults.stream()
            .sorted((d1, d2) -> {
                String doc1Text = buildDocText(d1);
                String doc2Text = buildDocText(d2);
                
                double score1 = queryEnhancementService.calculateKeywordRelevance(keywords, doc1Text);
                double score2 = queryEnhancementService.calculateKeywordRelevance(keywords, doc2Text);
                
                return Double.compare(score2, score1);
            })
            .limit(5)
            .collect(Collectors.toList());
        
        log.info("Enhanced RAG search found {} documents (from {} total matches)", 
                rankedResults.size(), allResults.size());
        
        return rankedResults;
    }
    
    /**
     * Perform basic search (original implementation).
     */
    private List<Document> performBasicSearch(String query, String userId, AccessEvaluator evaluator) {
        DocumentSearchDTO searchDTO = DocumentSearchDTO.builder()
            .query(query)
            .useSemanticSearch(true)
            .threshold(suggestionThreshold)
            .limit(5)
            .documentType("TSG")
            .build();
        
        List<Document> results = documentService.searchDocuments(searchDTO, userId, evaluator);
        
        if (results.isEmpty()) {
            searchDTO.setDocumentType(null);
            results = documentService.searchDocuments(searchDTO, userId, evaluator);
        }
        
        log.info("Basic search found {} documents", results.size());
        return results;
    }
    
    /**
     * Build combined text from document for relevance scoring
     */
    private String buildDocText(Document doc) {
        StringBuilder text = new StringBuilder();
        
        if (doc.getDocumentName() != null) {
            text.append(doc.getDocumentName()).append(" ");
        }
        if (doc.getSummary() != null) {
            text.append(doc.getSummary()).append(" ");
        }
        if (doc.getContent() != null) {
            String content = doc.getContent();
            if (content.length() > 500) {
                content = content.substring(0, 500);
            }
            text.append(content);
        }
        
        return text.toString();
    }
}
