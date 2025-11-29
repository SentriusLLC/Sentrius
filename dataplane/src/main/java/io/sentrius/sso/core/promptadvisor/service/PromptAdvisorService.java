package io.sentrius.sso.core.promptadvisor.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.sentrius.sso.core.config.SystemOptions;
import io.sentrius.sso.core.promptadvisor.model.RefinePromptRequest;
import io.sentrius.sso.core.promptadvisor.model.RefinePromptResponse;
import io.sentrius.sso.core.promptadvisor.model.ValidatePromptRequest;
import io.sentrius.sso.core.promptadvisor.model.ValidatePromptResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Slf4j
@Service
public class PromptAdvisorService {

    private final SystemOptions systemOptions;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public PromptAdvisorService(SystemOptions systemOptions, RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.systemOptions = systemOptions;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    public ValidatePromptResponse validatePrompt(String prompt, Map<String, Object> context) {
        if (!systemOptions.getEnablePromptAdvisor()) {
            log.debug("Prompt advisor is disabled, skipping validation");
            return null;
        }

        try {
            ValidatePromptRequest request = ValidatePromptRequest.builder()
                .prompt(prompt)
                .context(context)
                .build();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<ValidatePromptRequest> entity = new HttpEntity<>(request, headers);

            ResponseEntity<ValidatePromptResponse> response = restTemplate.exchange(
                systemOptions.getPromptAdvisorEndpoint(),
                HttpMethod.POST,
                entity,
                ValidatePromptResponse.class
            );

            return response.getBody();
        } catch (Exception e) {
            log.error("Error validating prompt with advisor service", e);
            return null;
        }
    }

    /**
     * Refines a prompt by calling the LLM to apply recommendations and improve quality.
     * This method:
     * 1. Validates the original prompt to get recommendations
     * 2. Calls the LLM-based refine endpoint to rewrite the prompt
     * 3. Returns the refined prompt with its new score
     * 
     * @param originalPrompt The prompt to refine
     * @param context Optional context for refinement
     * @return RefinePromptResponse containing the refined prompt and new score,
     *         or a response with original prompt if refinement fails,
     *         or null if service is unavailable
     */
    public RefinePromptResponse refinePromptWithLLM(String originalPrompt, Map<String, Object> context) {
        if (!systemOptions.getEnablePromptAdvisor()) {
            log.debug("Prompt advisor is disabled, returning original prompt");
            return RefinePromptResponse.builder()
                .originalPrompt(originalPrompt)
                .refinedPrompt(originalPrompt)
                .build();
        }

        try {
            // Step 1: Validate the original prompt to get recommendations
            ValidatePromptResponse validation = validatePrompt(originalPrompt, context);
            
            if (validation == null) {
                log.warn("Initial validation failed, returning original prompt");
                return RefinePromptResponse.builder()
                    .originalPrompt(originalPrompt)
                    .refinedPrompt(originalPrompt)
                    .build();
            }

            // If prompt already meets threshold, no refinement needed
            int threshold = systemOptions.getPromptAdvisorThreshold();
            if (validation.getScore() != null && validation.getScore() >= threshold) {
                log.info("Prompt already meets threshold with score {}, no refinement needed", validation.getScore());
                return RefinePromptResponse.builder()
                    .originalPrompt(originalPrompt)
                    .refinedPrompt(originalPrompt)
                    .score(validation.getScore())
                    .ratings(validation.getRatings())
                    .explanation(validation.getExplanation())
                    .recommendations(validation.getRecommendations())
                    .build();
            }

            // Step 2: Call the LLM-based refine endpoint
            RefinePromptRequest refineRequest = RefinePromptRequest.builder()
                .prompt(originalPrompt)
                .recommendations(validation.getRecommendations())
                .explanation(validation.getExplanation())
                .context(context)
                .build();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<RefinePromptRequest> entity = new HttpEntity<>(refineRequest, headers);

            String refineEndpoint = getRefineEndpoint();
            log.info("Calling LLM refine endpoint: {}", refineEndpoint);

            ResponseEntity<RefinePromptResponse> response = restTemplate.exchange(
                refineEndpoint,
                HttpMethod.POST,
                entity,
                RefinePromptResponse.class
            );

            RefinePromptResponse refineResponse = response.getBody();
            if (refineResponse != null) {
                log.info("Prompt refined successfully. Original score: {}, New score: {}", 
                    validation.getScore(), refineResponse.getScore());
            }

            return refineResponse;

        } catch (Exception e) {
            log.error("Error refining prompt with LLM", e);
            return RefinePromptResponse.builder()
                .originalPrompt(originalPrompt)
                .refinedPrompt(originalPrompt)
                .build();
        }
    }

    /**
     * Legacy method for backward compatibility.
     * Refines a prompt iteratively until it meets the threshold or max iterations.
     */
    public String refinePrompt(String originalPrompt, Map<String, Object> context) {
        RefinePromptResponse response = refinePromptWithLLM(originalPrompt, context);
        return response != null && response.getRefinedPrompt() != null 
            ? response.getRefinedPrompt() 
            : originalPrompt;
    }

    private String getRefineEndpoint() {
        // Derive the refine endpoint from the validate endpoint
        // e.g., http://localhost:8001/validate_prompt -> http://localhost:8001/refine_prompt
        String validateEndpoint = systemOptions.getPromptAdvisorEndpoint();
        if (validateEndpoint == null || validateEndpoint.isEmpty()) {
            log.warn("Prompt advisor endpoint not configured");
            return null;
        }
        if (validateEndpoint.endsWith("/validate_prompt")) {
            return validateEndpoint.replace("/validate_prompt", "/refine_prompt");
        }
        // Default: append /refine_prompt to the base URL
        if (validateEndpoint.endsWith("/")) {
            return validateEndpoint + "refine_prompt";
        }
        int lastSlashIndex = validateEndpoint.lastIndexOf('/');
        if (lastSlashIndex > 0) {
            return validateEndpoint.substring(0, lastSlashIndex) + "/refine_prompt";
        }
        // If no slash found, just append the path
        return validateEndpoint + "/refine_prompt";
    }
}
