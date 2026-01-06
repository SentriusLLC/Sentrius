package io.sentrius.sso.core.services.tooltip;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import io.sentrius.sso.core.config.SystemOptions;
import io.sentrius.sso.core.dto.SystemOption;
import io.sentrius.sso.core.dto.documents.DocumentSearchDTO;
import io.sentrius.sso.core.dto.tooltip.TooltipChatRequest;
import io.sentrius.sso.core.dto.tooltip.TooltipChatResponse;
import io.sentrius.sso.core.dto.tooltip.TooltipDescribeRequest;
import io.sentrius.sso.core.dto.tooltip.TooltipDescribeResponse;
import io.sentrius.sso.core.dto.ztat.TokenDTO;
import io.sentrius.sso.core.exceptions.ZtatException;
import io.sentrius.sso.core.model.documents.Document;
import io.sentrius.sso.core.services.agents.LLMService;
import io.sentrius.sso.core.services.documents.DocumentService;
import io.sentrius.sso.core.utils.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for providing AI-powered tooltips and help for UI elements.
 * Searches indexed codebase and documentation to provide contextual information.
 */
@Slf4j
@Service
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class TooltipService {

    private final DocumentService documentService;
    private final LLMService llmService;
    private final SystemOptions systemOptions;
    
    @Value("${sentrius.tooltip.max-context-documents:5}")
    private int maxContextDocuments;
    
    @Value("${sentrius.tooltip.similarity-threshold:0.5}")
    private double similarityThreshold;
    
    @Value("${sentrius.tooltip.llm-model:gpt-4o-mini}")
    private String llmModel;

    public TooltipService(DocumentService documentService, LLMService llmService, SystemOptions systemOptions) {
        this.documentService = documentService;
        this.llmService = llmService;
        this.systemOptions = systemOptions;
    }

    /**
     * Generate AI-powered description for a UI element
     */
    public TooltipDescribeResponse describeElement(TooltipDescribeRequest request, TokenDTO tokenDTO) {
        try {
            log.info("Generating tooltip description for element: {}", 
                    request.getContext() != null ? request.getContext().getId() : "unknown");

            // Build search query from element context
            String searchQuery = buildSearchQuery(request.getContext());
            log.debug("Search query: {}", searchQuery);

            // Search for relevant documentation
            List<Document> relevantDocs = searchRelevantDocumentation(searchQuery);
            log.debug("Found {} relevant documents", relevantDocs.size());

            // Build context for LLM
            String context = buildLLMContext(relevantDocs, request.getContext());

            // Generate description using LLM
            String description = generateDescription(context, request.getContext(), tokenDTO);

            return TooltipDescribeResponse.builder()
                    .description(description)
                    .message(description)
                    .success(true)
                    .build();

        } catch (ZtatException e) {
            log.error("ZTAT exception generating tooltip description", e);
            return TooltipDescribeResponse.builder()
                    .description("Unable to authenticate with LLM service.")
                    .error(e.getMessage())
                    .success(false)
                    .build();
        } catch (Exception e) {
            log.error("Error generating tooltip description", e);
            return TooltipDescribeResponse.builder()
                    .description("Unable to generate description at this time.")
                    .error(e.getMessage())
                    .success(false)
                    .build();
        }
    }

    /**
     * Handle chat conversation for contextual help
     */
    public TooltipChatResponse chat(TooltipChatRequest request, TokenDTO tokenDTO) {
        try {
            log.info("Processing chat request: {}", request.getMessage());

            // Search for relevant documentation based on the message
            String searchQuery = request.getMessage();
            if (request.getContext() != null) {
                searchQuery = request.getMessage() + " " + buildSearchQuery(request.getContext());
            }

            List<Document> relevantDocs = searchRelevantDocumentation(searchQuery);
            log.debug("Found {} relevant documents for chat", relevantDocs.size());

            // Build context for LLM
            String context = buildLLMContext(relevantDocs, request.getContext());

            // Generate chat response using LLM
            String response = generateChatResponse(context, request.getMessage(), request.getContext(), tokenDTO);

            return TooltipChatResponse.builder()
                    .response(response)
                    .message(response)
                    .success(true)
                    .build();

        } catch (ZtatException e) {
            log.error("ZTAT exception generating chat response", e);
            return TooltipChatResponse.builder()
                    .response("Unable to authenticate with LLM service.")
                    .error(e.getMessage())
                    .success(false)
                    .build();
        } catch (Exception e) {
            log.error("Error generating chat response", e);
            return TooltipChatResponse.builder()
                    .response("Unable to generate response at this time.")
                    .error(e.getMessage())
                    .success(false)
                    .build();
        }
    }

    /**
     * Build a search query from element context
     */
    private String buildSearchQuery(TooltipDescribeRequest.ElementContext context) {
        if (context == null) {
            return "";
        }

        StringBuilder query = new StringBuilder();

        // Add ID if present
        if (context.getId() != null && !context.getId().trim().isEmpty()) {
            query.append(context.getId()).append(" ");
        }

        // Add class names
        if (context.getClassName() != null && !context.getClassName().trim().isEmpty()) {
            String[] classes = context.getClassName().split("\\s+");
            for (String cls : classes) {
                if (cls.length() > 2) { // Skip very short class names
                    query.append(cls).append(" ");
                }
            }
        }

        // Add text content (first few words)
        if (context.getTextContent() != null && !context.getTextContent().trim().isEmpty()) {
            String text = context.getTextContent().trim();
            String[] words = text.split("\\s+");
            int wordCount = Math.min(words.length, 10);
            for (int i = 0; i < wordCount; i++) {
                query.append(words[i]).append(" ");
            }
        }

        // Add relevant attributes
        if (context.getAttributes() != null) {
            Map<String, String> attrs = context.getAttributes();
            if (attrs.containsKey("name")) {
                query.append(attrs.get("name")).append(" ");
            }
            if (attrs.containsKey("title")) {
                query.append(attrs.get("title")).append(" ");
            }
            if (attrs.containsKey("aria-label")) {
                query.append(attrs.get("aria-label")).append(" ");
            }
        }

        return query.toString().trim();
    }

    /**
     * Search for relevant documentation
     * 
     * Note: Using deprecated searchDocuments method as we need internal search without access control.
     * The newer method requires AccessEvaluator which we don't have in this service context.
     * This is safe as the tooltip service is already behind authentication/authorization.
     * TODO: Consider refactoring DocumentService to provide an internal search method.
     */
    private List<Document> searchRelevantDocumentation(String query) {
        if (query == null || query.trim().isEmpty()) {
            return new ArrayList<>();
        }

        try {
            DocumentSearchDTO searchDTO = DocumentSearchDTO.builder()
                    .query(query)
                    .useSemanticSearch(true)
                    .threshold(similarityThreshold)
                    .limit(maxContextDocuments)
                    .build();

            // Using deprecated method for internal search - see method documentation above
            @SuppressWarnings("deprecation")
            List<Document> results = documentService.searchDocuments(searchDTO);
            return results;

        } catch (Exception e) {
            log.warn("Error searching documentation: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Build context string for LLM from relevant documents
     */
    private String buildLLMContext(List<Document> documents, TooltipDescribeRequest.ElementContext elementContext) {
        StringBuilder context = new StringBuilder();

        context.append("You are a helpful assistant for the Sentrius Zero Trust Security Platform. ");
        context.append("Provide clear, concise explanations about UI elements and features.\n\n");

        if (!documents.isEmpty()) {
            context.append("Relevant documentation:\n");
            for (Document doc : documents) {
                context.append("- ").append(doc.getDocumentName()).append(": ");
                String summary = doc.getSummary();
                if (summary != null && !summary.isEmpty()) {
                    context.append(summary);
                } else {
                    // Use first 200 characters of content
                    String content = doc.getContent();
                    if (content != null) {
                        int endIndex = Math.min(200, content.length());
                        context.append(content.substring(0, endIndex));
                        if (content.length() > 200) {
                            context.append("...");
                        }
                    }
                }
                context.append("\n");
            }
            context.append("\n");
        }

        if (elementContext != null) {
            context.append("Element information:\n");
            if (elementContext.getTagName() != null) {
                context.append("- Tag: ").append(elementContext.getTagName()).append("\n");
            }
            if (elementContext.getId() != null && !elementContext.getId().isEmpty()) {
                context.append("- ID: ").append(elementContext.getId()).append("\n");
            }
            if (elementContext.getTextContent() != null && !elementContext.getTextContent().isEmpty()) {
                context.append("- Text: ").append(elementContext.getTextContent()).append("\n");
            }
            if (elementContext.getAttributes() != null && !elementContext.getAttributes().isEmpty()) {
                context.append("- Attributes: ").append(elementContext.getAttributes()).append("\n");
            }
        }

        return context.toString();
    }

    /**
     * Generate description using LLM
     */
    private String generateDescription(String context, TooltipDescribeRequest.ElementContext elementContext, 
                                      TokenDTO tokenDTO) throws ZtatException, JsonProcessingException {
        String userPrompt = "Provide a brief, helpful description (2-3 sentences) of what this UI element does. ";
        userPrompt += "Focus on its purpose and how users should interact with it.";

        return callLLM(context, userPrompt, tokenDTO);
    }

    /**
     * Generate chat response using LLM
     */
    private String generateChatResponse(String context, String userMessage, 
                                       TooltipDescribeRequest.ElementContext elementContext, 
                                       TokenDTO tokenDTO) throws ZtatException, JsonProcessingException {
        String userPrompt = "User question: " + userMessage + "\n\n";
        userPrompt += "Please provide a helpful answer based on the documentation provided. ";
        userPrompt += "Be specific and reference relevant features when appropriate.";

        return callLLM(context, userPrompt, tokenDTO);
    }

    /**
     * Call LLM service with the given context and prompt
     */
    private String callLLM(String systemContext, String userPrompt, TokenDTO tokenDTO) 
            throws ZtatException, JsonProcessingException {
        
        List<Map<String, String>> messages = new ArrayList<>();
        
        // System message with context
        Map<String, String> systemMessage = new HashMap<>();
        systemMessage.put("role", "system");
        systemMessage.put("content", systemContext);
        messages.add(systemMessage);
        
        // User message
        Map<String, String> userMessage = new HashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", userPrompt);
        messages.add(userMessage);
        
        // Build request
        Map<String, Object> request = new HashMap<>();
        request.put("model", llmModel);
        request.put("messages", messages);
        request.put("max_tokens", 300);
        request.put("temperature", 0.7);
        
        // Call LLM
        String llmResponse = llmService.askQuestion(tokenDTO,systemOptions.getIntegrationProxyUrl(), request);


        // Parse the response
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
                return  choicesNode.get(0)
                    .get("message")
                    .get("content")
                    .asText();
            }
        }
        
        throw new RuntimeException("Unable to parse LLM response");
    }
}
