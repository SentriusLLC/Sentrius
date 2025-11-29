package io.sentrius.sso.controllers.api;

import io.sentrius.sso.core.annotations.LimitAccess;
import io.sentrius.sso.core.model.security.enums.ApplicationAccessEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * REST API controller for Prompt Advisor functionality.
 * Proxies requests to the prompt-advisor microservice.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/prompt-advisor")
public class PromptAdvisorApiController {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${sentrius.prompt-advisor.url:http://sentrius-prompt-advisor:80}")
    private String promptAdvisorUrl;

    /**
     * Get current ATPL criteria and their weights
     */
    @GetMapping("/criteria")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public ResponseEntity<Map<String, Object>> getCriteria() {
        try {
            String url = promptAdvisorUrl + "/criteria";
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            return ResponseEntity.ok(response.getBody());
        } catch (Exception e) {
            log.error("Error fetching criteria from prompt-advisor", e);
            Map<String, Object> error = new HashMap<>();
            error.put("status", "error");
            error.put("message", "Failed to fetch criteria: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error);
        }
    }

    /**
     * Validate a prompt against ATPL criteria
     */
    @PostMapping("/validate")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public ResponseEntity<Map<String, Object>> validatePrompt(@RequestBody Map<String, Object> request) {
        try {
            String url = promptAdvisorUrl + "/validate_prompt";
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
            
            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);
            return ResponseEntity.ok(response.getBody());
        } catch (Exception e) {
            log.error("Error validating prompt with prompt-advisor", e);
            Map<String, Object> error = new HashMap<>();
            error.put("status", "error");
            error.put("message", "Failed to validate prompt: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error);
        }
    }

    /**
     * Interactive prompt refinement session
     */
    @PostMapping("/refine")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public ResponseEntity<Map<String, Object>> refinePrompt(@RequestBody Map<String, Object> request) {
        try {
            // First validate the prompt
            String prompt = (String) request.get("prompt");
            String sessionId = (String) request.getOrDefault("sessionId", UUID.randomUUID().toString());
            
            Map<String, Object> validateRequest = new HashMap<>();
            validateRequest.put("prompt", prompt);
            
            // Only include context if it's a non-empty Map (prompt-advisor expects Dict or null)
            Object contextObj = request.get("context");
            if (contextObj instanceof Map && !((Map<?, ?>) contextObj).isEmpty()) {
                validateRequest.put("context", contextObj);
            }
            // If context is null or not provided, don't include it - let the service use its default
            
            String url = promptAdvisorUrl + "/validate_prompt";
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(validateRequest, headers);
            
            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);
            Map<String, Object> validationResult = response.getBody();
            
            // Build refinement response with suggestions
            Map<String, Object> refinementResponse = new HashMap<>();
            refinementResponse.put("sessionId", sessionId);
            refinementResponse.put("originalPrompt", prompt);
            refinementResponse.put("score", validationResult.get("score"));
            refinementResponse.put("ratings", validationResult.get("ratings"));
            refinementResponse.put("explanation", validationResult.get("explanation"));
            refinementResponse.put("recommendations", validationResult.get("recommendations"));
            
            // Generate refinement suggestions based on scores
            List<String> suggestions = generateRefinementSuggestions(validationResult);
            refinementResponse.put("suggestions", suggestions);
            
            return ResponseEntity.ok(refinementResponse);
        } catch (Exception e) {
            log.error("Error refining prompt with prompt-advisor", e);
            Map<String, Object> error = new HashMap<>();
            error.put("status", "error");
            error.put("message", "Failed to refine prompt: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error);
        }
    }

    /**
     * Get health status of prompt-advisor service
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> getHealth() {
        try {
            String url = promptAdvisorUrl + "/health";
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            
            Map<String, Object> health = new HashMap<>();
            health.put("status", "healthy");
            health.put("promptAdvisorStatus", response.getBody());
            return ResponseEntity.ok(health);
        } catch (Exception e) {
            log.warn("Prompt advisor service unavailable", e);
            Map<String, Object> health = new HashMap<>();
            health.put("status", "unhealthy");
            health.put("message", "Prompt advisor service is unavailable");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(health);
        }
    }

    /**
     * Generate refinement suggestions based on validation scores
     */
    private List<String> generateRefinementSuggestions(Map<String, Object> validationResult) {
        List<String> suggestions = new ArrayList<>();
        
        @SuppressWarnings("unchecked")
        Map<String, Object> ratings = (Map<String, Object>) validationResult.get("ratings");
        
        if (ratings != null) {
            Integer purposeScore = getScore(ratings, "purpose");
            Integer safetyScore = getScore(ratings, "safety");
            Integer complianceScore = getScore(ratings, "compliance");
            Integer provenanceScore = getScore(ratings, "provenance");
            Integer autonomyScore = getScore(ratings, "autonomy");
            
            if (purposeScore != null && purposeScore < 7) {
                suggestions.add("Consider clarifying the specific goal or objective of your prompt");
            }
            if (safetyScore != null && safetyScore < 7) {
                suggestions.add("Review the prompt for potential safety concerns or prohibited content");
            }
            if (complianceScore != null && complianceScore < 7) {
                suggestions.add("Ensure the prompt adheres to data sensitivity and compliance requirements");
            }
            if (provenanceScore != null && provenanceScore < 7) {
                suggestions.add("Add context about the source and trustworthiness of data being used");
            }
            if (autonomyScore != null && autonomyScore < 7) {
                suggestions.add("Define clearer boundaries for agent autonomy and decision-making");
            }
        }
        
        // Add recommendations from validation result
        @SuppressWarnings("unchecked")
        List<String> recommendations = (List<String>) validationResult.get("recommendations");
        if (recommendations != null && !recommendations.isEmpty()) {
            suggestions.addAll(recommendations);
        }
        
        return suggestions;
    }

    private Integer getScore(Map<String, Object> ratings, String key) {
        Object value = ratings.get(key);
        if (value instanceof Integer) {
            return (Integer) value;
        } else if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return null;
    }
}
