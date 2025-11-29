package io.sentrius.sso.controllers.api;

import io.sentrius.sso.core.annotations.LimitAccess;
import io.sentrius.sso.core.config.SystemOptions;
import io.sentrius.sso.core.controllers.BaseController;
import io.sentrius.sso.core.model.security.enums.ApplicationAccessEnum;
import io.sentrius.sso.core.model.verbs.Endpoint;
import io.sentrius.sso.core.promptadvisor.model.RefinePromptResponse;
import io.sentrius.sso.core.promptadvisor.model.ValidatePromptRequest;
import io.sentrius.sso.core.promptadvisor.service.PromptAdvisorService;
import io.sentrius.sso.core.services.ErrorOutputService;
import io.sentrius.sso.core.services.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
public class PromptAdvisorApiController extends BaseController {

    private final RestTemplate restTemplate = new RestTemplate();
    private final PromptAdvisorService promptAdvisorService;


    @Value("${sentrius.prompt-advisor.url:http://sentrius-prompt-advisor:80}")
    private String promptAdvisorUrl;

    protected PromptAdvisorApiController(
        UserService userService, SystemOptions systemOptions,
        ErrorOutputService errorOutputService, PromptAdvisorService promptAdvisorService
    ) {
        super(userService, systemOptions, errorOutputService);
        this.promptAdvisorService = promptAdvisorService;
    }

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
    @Endpoint(description = "Refine a prompt using LLM to apply recommendations and improve quality")
    public ResponseEntity<?> refinePrompt(
        @RequestBody ValidatePromptRequest request,
        HttpServletRequest httpRequest,
        HttpServletResponse httpResponse
    ) {
        if (!systemOptions.getEnablePromptAdvisor()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("error", "Prompt advisor service is disabled"));
        }

        var operatingUser = getOperatingUser(httpRequest, httpResponse);
        if (operatingUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "Authentication required"));
        }

        log.info("Refining prompt using LLM for user: {}", operatingUser.getUsername());

        // Use the new LLM-based refinement that actually rewrites the prompt
        RefinePromptResponse refineResponse = promptAdvisorService.refinePromptWithLLM(
            request.getPrompt(),
            request.getContext()
        );

        if (refineResponse == null) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to refine prompt"));
        }

        // Build response with all refinement data
        Map<String, Object> result = new HashMap<>();
        result.put("original_prompt", refineResponse.getOriginalPrompt());
        result.put("refined_prompt", refineResponse.getRefinedPrompt());
        if (refineResponse.getScore() != null) {
            result.put("score", refineResponse.getScore());
        }
        if (refineResponse.getRatings() != null) {
            result.put("ratings", refineResponse.getRatings());
        }
        if (refineResponse.getExplanation() != null) {
            result.put("explanation", refineResponse.getExplanation());
        }
        if (refineResponse.getRecommendations() != null) {
            result.put("recommendations", refineResponse.getRecommendations());
        }

        return ResponseEntity.ok(result);
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
