package io.sentrius.sso.core.services.openai;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.sentrius.sso.core.integrations.external.ExternalIntegrationDTO;
import io.sentrius.sso.core.model.security.IntegrationSecurityToken;
import io.sentrius.sso.core.services.security.IntegrationSecurityTokenService;
import io.sentrius.sso.core.utils.JsonUtil;
import io.sentrius.sso.genai.GenerativeAPI;
import io.sentrius.sso.genai.Message;
import io.sentrius.sso.security.ApiKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * AI Support service that provides intelligent command assistance
 * using LLM proxy. Implements PluggableServices to be available to rules.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AISupportLLMService implements io.sentrius.sso.core.services.PluggableServices {

    final IntegrationSecurityTokenService integrationSecurityTokenService;

    IntegrationSecurityToken openAiToken = null;
    GenerativeAPI generativeAPI = null;

    @Override
    public String getName() {
        return "aisupport";
    }

    @Override
    public boolean isEnabled() {
        if (null == openAiToken) {
            synchronized (this) {
                if (null == openAiToken) {
                    log.info("Setting up AI Support LLM service");
                    openAiToken = integrationSecurityTokenService.selectToken("openai").orElse(null);
                    if (openAiToken == null) {
                        log.info("No OpenAI integration found");
                        return false;
                    }
                    try {
                        ExternalIntegrationDTO externalIntegrationDTO = JsonUtil.MAPPER.readValue(
                            openAiToken.getConnectionInfo(),
                            ExternalIntegrationDTO.class
                        );
                        ApiKey key = ApiKey.builder()
                            .apiKey(externalIntegrationDTO.getApiToken())
                            .principal(externalIntegrationDTO.getUsername())
                            .build();
                        generativeAPI = new GenerativeAPI(key);
                    } catch (JsonProcessingException e) {
                        log.error("Failed to parse OpenAI integration config", e);
                        throw new RuntimeException(e);
                    }
                }
            }
        }
        log.debug("AI Support LLM service enabled: {}", (openAiToken != null));
        return openAiToken != null;
    }

    /**
     * Generate a suggestion for a command using LLM
     * 
     * @param command The command to analyze
     * @param context Recent command history for context
     * @return AI-generated suggestion
     */
    public CompletableFuture<String> generateSuggestion(String command, String context) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (!isEnabled()) {
                    return null;
                }

                String prompt = buildPrompt(command, context);
                
                Message message = Message.builder()
                    .role("user")
                    .content(prompt)
                    .build();
                
                // Build LLMRequest first
                io.sentrius.sso.genai.model.LLMRequest llmRequest = 
                    io.sentrius.sso.genai.model.LLMRequest.builder()
                        .model("gpt-4o-mini")
                        .messages(List.of(message))
                        .maxTokens(300)
                        .build();
                
                // Use RawConversationRequest which wraps LLMRequest
                io.sentrius.sso.genai.model.endpoints.RawConversationRequest request = 
                    io.sentrius.sso.genai.model.endpoints.RawConversationRequest.builder()
                        .request(llmRequest)
                        .build();
                
                // Call GenerativeAPI.sample() which returns raw response
                String response = generativeAPI.sample(request);
                return parseResponse(response);
                
            } catch (Exception e) {
                log.error("Failed to generate AI suggestion", e);
                return null;
            }
        });
    }

    /**
     * Build prompt for LLM
     */
    private String buildPrompt(String command, String context) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are an AI assistant helping users with terminal commands. ");
        prompt.append("Analyze the command history and current command to detect mistakes and provide helpful suggestions.\n\n");
        
        prompt.append("Current command: ").append(command).append("\n\n");
        
        if (context != null && !context.isEmpty()) {
            prompt.append(context).append("\n\n");
        }
        
        prompt.append("IMPORTANT: Look for common mistakes such as:\n");
        prompt.append("- Using 'chown' with numeric permissions (should be 'chmod')\n");
        prompt.append("- Using 'chmod' with user:group format (should be 'chown')\n");
        prompt.append("- Incorrect permission values or syntax\n");
        prompt.append("- Commands that might conflict with recent actions\n");
        prompt.append("- Missing flags or incorrect command usage\n\n");
        
        prompt.append("If the user recently created files and is now changing permissions, ");
        prompt.append("check if their command is correct and suggest improvements if needed.\n\n");
        
        prompt.append("Provide a brief, actionable suggestion in 2-3 sentences. ");
        
        // Add specific guidance based on command patterns
        if (command.matches(".*\\b(rm|dd|mkfs|fdisk|parted)\\b.*")) {
            prompt.append("This command can be dangerous - warn the user clearly and suggest safer alternatives.");
        } else if (command.matches(".*\\b(chmod|chown|chgrp)\\b.*")) {
            prompt.append("Focus on verifying the permission/ownership syntax is correct.");
        } else if (command.matches(".*\\b(find|grep|awk|sed|docker|kubectl)\\b.*")) {
            prompt.append("This is complex - offer concise tips or highlight common pitfalls.");
        } else {
            prompt.append("Offer helpful context based on the command history.");
        }
        
        return prompt.toString();
    }

    /**
     * Parse LLM response to extract suggestion text
     */
    private String parseResponse(String llmResponse) {
        if (llmResponse == null || llmResponse.isEmpty()) {
            return null;
        }
        
        try {
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
        } catch (JsonProcessingException e) {
            log.error("Failed to parse LLM response", e);
        }
        
        return null;
    }
}
