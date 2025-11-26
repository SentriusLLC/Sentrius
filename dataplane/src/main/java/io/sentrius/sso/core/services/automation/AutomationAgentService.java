package io.sentrius.sso.core.services.automation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import io.sentrius.sso.core.exceptions.ZtatException;
import io.sentrius.sso.core.model.automation.AutomationSuggestion;
import io.sentrius.sso.core.services.agents.ZeroTrustClientService;
import io.sentrius.sso.core.utils.JsonUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for interacting with AI agents to generate and improve automation code.
 * Uses the integration proxy for LLM communication which can be configured via ConfigMap.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class AutomationAgentService {

    @Value("${integrationproxy.externalUrl:http://localhost:8080}")
    private String integrationProxyUrl;
    
    @Value("${sentrius.llm.model:gpt-4}")
    private String defaultModel;

    final ZeroTrustClientService zeroTrustClientService;
    
    /**
     * Generate automation code based on a suggestion
     */
    public String generateAutomationCode(AutomationSuggestion suggestion, String userPrompt) {
        log.info("Generating automation code for suggestion {} with user prompt: {}", 
                 suggestion.getId(), userPrompt);
        
        String systemPrompt = buildSystemPrompt(suggestion);
        String prompt = buildGenerationPrompt(suggestion, userPrompt);

        var resp = callLLM(systemPrompt, prompt);
        return asString(resp);
    }

    private String asString(Object obj) {
        if (obj == null) return "";
        if (obj instanceof String s) return s;
        return obj.toString();
    }
    /**
     * Improve existing automation code based on user feedback
     */
    public String improveAutomationCode(String existingCode, String scriptType,
                                       String userFeedback, String context) {
        log.info("Improving automation code of type {} based on feedback: {}", 
                 scriptType, userFeedback);
        
        String systemPrompt = "You are an expert automation engineer specializing in " + 
                            scriptType + " scripts for system administration and DevOps tasks.";
        
        String prompt = String.format("""
            I have the following %s automation script:
            
            ```%s
            %s
            ```
            
            Context: %s
            
            User Feedback: %s
            
            Please improve this script based on the user's feedback. Return only the improved script code 
            without any explanation or markdown formatting.
            """, scriptType, scriptType, existingCode, context, userFeedback);

        var resp = callLLM(systemPrompt, prompt);
        return asString(resp);
    }
    
    /**
     * Chat with the automation agent for general guidance
     */
    public Map<String, Object> chatWithAgent(List<Map<String, String>> conversationHistory, 
                                            String newMessage, String context) {
        log.info("Processing chat message with automation agent");
        
        String systemPrompt = buildChatSystemPrompt(context);
        
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));
        messages.addAll(conversationHistory);
        messages.add(Map.of("role", "user", "content", newMessage));
        
        Object response = callLLMWithMessages(messages);
        
        Map<String, Object> result = new HashMap<>();
        result.put("response", response);
        result.put("timestamp", System.currentTimeMillis());
        
        return result;
    }
    
    /**
     * Analyze automation code for potential issues
     */
    public Map<String, Object> analyzeAutomationCode(String code, String scriptType) {
        log.info("Analyzing {} automation code for potential issues", scriptType);
        
        String systemPrompt = "You are a security and code quality expert for " + scriptType + 
                            " automation scripts.";
        
        String prompt = String.format("""
            Analyze the following %s automation script for:
            1. Security vulnerabilities
            2. Potential destructive operations (file deletion, system modifications, etc.)
            3. Code quality issues
            4. Best practice violations
            
            Script:
            ```%s
            %s
            ```
            
            Return your analysis in the following JSON format:
            {
              "isDestructive": boolean,
              "destructiveOperations": ["list of destructive commands found"],
              "securityIssues": ["list of security concerns"],
              "qualityIssues": ["list of code quality issues"],
              "suggestions": ["list of improvement suggestions"],
              "overallRisk": "LOW|MEDIUM|HIGH"
            }
            
            Return ONLY the JSON, no additional text.
            """, scriptType, scriptType, code);

        Object rawResponse = callLLM(systemPrompt, prompt);
        log.info("Analysis response: {}", rawResponse);

        try {

            if (rawResponse instanceof Map<?, ?> map &&
                map.containsKey("isDestructive")) {
                return (Map<String, Object>) map;
            }

            if (rawResponse instanceof Map<?, ?> root &&
                root.containsKey("output")) {

                List<?> output = (List<?>) root.get("output");
                Map<?, ?> msg = (Map<?, ?>) output.get(0);
                List<?> content = (List<?>) msg.get("content");
                Map<?, ?> textItem = (Map<?, ?>) content.get(0);

                String jsonText = (String) textItem.get("text");
                return JsonUtil.MAPPER.readValue(jsonText, Map.class);
            }

            // ✅ CASE 3: Raw JSON string
            if (rawResponse instanceof String s) {
                return JsonUtil.MAPPER.readValue(s, Map.class);
            }

            throw new IllegalStateException("Unsupported LLM response type: "
                + rawResponse.getClass());

        } catch (Exception e) {
            log.warn("Failed to parse analysis response, using fallback", e);

            Map<String, Object> fallback = new HashMap<>();
            fallback.put("isDestructive", false);
            fallback.put("destructiveOperations", List.of());
            fallback.put("securityIssues", List.of());
            fallback.put("qualityIssues", List.of());
            fallback.put("suggestions", List.of());
            fallback.put("overallRisk", "UNKNOWN");
            fallback.put("rawResponse", rawResponse);
            return fallback;
        }
    }

    private String buildSystemPrompt(AutomationSuggestion suggestion) {
        return String.format("""
            You are an expert automation engineer specializing in %s scripts for system administration.
            Your role is to help generate safe, efficient, and well-documented automation scripts.
            
            Key principles:
            1. Safety first - avoid destructive operations unless explicitly requested
            2. Include error handling and validation
            3. Add clear comments explaining what the script does
            4. Follow best practices for %s scripting
            5. Make scripts idempotent when possible
            """, suggestion.getScriptType(), suggestion.getScriptType());
    }
    
    private String buildGenerationPrompt(AutomationSuggestion suggestion, String userPrompt) {
        return String.format("""
            Generate a %s automation script based on the following requirements:
            
            Description: %s
            Target System: %s
            Observed Pattern: Analyzed from %s sessions
            Confidence Score: %.2f
            
            User Requirements:
            %s
            
            Please generate a complete, production-ready script that accomplishes this automation task.
            Include appropriate error handling, logging, and comments.
            Return only the script code without any explanation or markdown formatting.
            """, 
            suggestion.getScriptType(),
            suggestion.getDescription(),
            suggestion.getTargetSystem(),
            suggestion.getPatternFrequency(),
            suggestion.getConfidenceScore(),
            userPrompt != null ? userPrompt : "Create automation based on the description"
        );
    }
    
    private String buildChatSystemPrompt(String context) {
        return String.format("""
            You are an AI assistant specialized in automation development for system administration.
            You help users create, modify, and understand automation scripts (bash, python, etc.).
            
            Context: %s
            
            Provide clear, concise, and actionable guidance. When suggesting code changes, 
            explain why they're beneficial. Focus on safety, reliability, and best practices.
            """, context != null ? context : "General automation assistance");
    }
    
    private Object callLLM(String systemPrompt, String userPrompt) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));
        messages.add(Map.of("role", "user", "content", userPrompt));
        
        return callLLMWithMessages(messages);
    }

    private Object callLLMWithMessages(List<Map<String, String>> messages) {
        try {
            Map<String, Object> request = new HashMap<>();
            request.put("messages", messages);
            request.put("model", defaultModel);
            request.put("temperature", 0.7);

            String llmEndpoint  = "/api/v1/chat/completions";

            // ---- FIX 1: Do NOT assume resp is a JSON string ----



            Object rawResp = zeroTrustClientService.callAuthenticatedPostOnApi(integrationProxyUrl, llmEndpoint,
                request);
            log.info("Raw LLM response: {}", rawResp);

            JsonNode root;
            if (rawResp instanceof String s) {
                root = JsonUtil.MAPPER.readTree(s);
            } else {
                root = JsonUtil.MAPPER.valueToTree(rawResp);
            }
            JsonNode output = root.path("output");
            if (!output.isArray() || output.isEmpty()) {
                log.warn("LLM response missing output: {}", root);
                return "Error: LLM returned no output";
            }

            for (JsonNode item : output) {
                if (!"message".equals(item.path("type").asText())) continue;

                JsonNode content = item.path("content");
                if (!content.isArray()) continue;

                for (JsonNode c : content) {
                    if ("output_text".equals(c.path("type").asText())) {
                        return c.path("text").asText();
                    }
                }
            }

            log.warn("Unable to extract output_text from response: {}", root);
            return "Error: Unable to extract LLM output";


        } catch (Exception e) {
            log.error("Error calling LLM endpoint", e);
            return "Error: Failed to communicate with AI agent - " + e.getMessage();
        } catch (ZtatException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJsonResponse(String jsonString) throws JsonProcessingException {
        jsonString = jsonString.trim();
        return JsonUtil.MAPPER.readValue(jsonString, Map.class);
    }
}
