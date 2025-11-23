package io.sentrius.sso.core.services.automation;

import io.sentrius.sso.core.model.automation.AutomationSuggestion;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

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
public class AutomationAgentService {
    
    private final RestTemplate restTemplate;
    
    @Value("${sentrius.integration.proxy.url:http://localhost:8080}")
    private String integrationProxyUrl;
    
    @Value("${sentrius.llm.model:gpt-4}")
    private String defaultModel;
    
    /**
     * Generate automation code based on a suggestion
     */
    public String generateAutomationCode(AutomationSuggestion suggestion, String userPrompt) {
        log.info("Generating automation code for suggestion {} with user prompt: {}", 
                 suggestion.getId(), userPrompt);
        
        String systemPrompt = buildSystemPrompt(suggestion);
        String prompt = buildGenerationPrompt(suggestion, userPrompt);
        
        return callLLM(systemPrompt, prompt);
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
        
        return callLLM(systemPrompt, prompt);
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
        
        String response = callLLMWithMessages(messages);
        
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
        
        String response = callLLM(systemPrompt, prompt);
        
        Map<String, Object> result = new HashMap<>();
        try {
            result = parseJsonResponse(response);
        } catch (Exception e) {
            log.warn("Failed to parse analysis response as JSON, using fallback", e);
            result.put("isDestructive", false);
            result.put("destructiveOperations", new ArrayList<>());
            result.put("securityIssues", new ArrayList<>());
            result.put("qualityIssues", new ArrayList<>());
            result.put("suggestions", new ArrayList<>());
            result.put("overallRisk", "UNKNOWN");
            result.put("rawResponse", response);
        }
        
        return result;
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
    
    private String callLLM(String systemPrompt, String userPrompt) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));
        messages.add(Map.of("role", "user", "content", userPrompt));
        
        return callLLMWithMessages(messages);
    }
    
    private String callLLMWithMessages(List<Map<String, String>> messages) {
        try {
            Map<String, Object> request = new HashMap<>();
            request.put("messages", messages);
            request.put("model", defaultModel);
            request.put("temperature", 0.7);
            
            String llmEndpoint = integrationProxyUrl + "/api/v1/llm/chat";
            
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(
                llmEndpoint, 
                request, 
                Map.class
            );
            
            if (response != null && response.containsKey("choices")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
                if (!choices.isEmpty()) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                    return (String) message.get("content");
                }
            }
            
            log.warn("Unexpected LLM response format: {}", response);
            return "Error: Unexpected response format from LLM";
            
        } catch (Exception e) {
            log.error("Error calling LLM endpoint", e);
            return "Error: Failed to communicate with AI agent - " + e.getMessage();
        }
    }
    
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJsonResponse(String jsonString) {
        jsonString = jsonString.trim();
        
        int startIdx = jsonString.indexOf('{');
        int endIdx = jsonString.lastIndexOf('}');
        
        if (startIdx >= 0 && endIdx > startIdx) {
            jsonString = jsonString.substring(startIdx, endIdx + 1);
        }
        
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = 
                new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.readValue(jsonString, Map.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse JSON response", e);
        }
    }
}
