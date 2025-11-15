package io.sentrius.sso.promptadvisor.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.sentrius.sso.core.config.SystemOptions;
import io.sentrius.sso.promptadvisor.model.ValidatePromptRequest;
import io.sentrius.sso.promptadvisor.model.ValidatePromptResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
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

    public String refinePrompt(String originalPrompt, Map<String, Object> context) {
        if (!systemOptions.getEnablePromptAdvisor()) {
            return originalPrompt;
        }

        String currentPrompt = originalPrompt;
        int maxIterations = systemOptions.getPromptAdvisorMaxIterations();
        int threshold = systemOptions.getPromptAdvisorThreshold();

        for (int i = 0; i < maxIterations; i++) {
            ValidatePromptResponse validation = validatePrompt(currentPrompt, context);
            
            if (validation == null || validation.getScore() == null) {
                log.warn("Prompt validation failed, returning original prompt");
                return originalPrompt;
            }

            if (validation.getScore() >= threshold) {
                log.info("Prompt meets threshold after {} iterations with score {}", i + 1, validation.getScore());
                return currentPrompt;
            }

            if (validation.getRecommendations() == null || validation.getRecommendations().isEmpty()) {
                log.warn("No recommendations provided, cannot refine further");
                return currentPrompt;
            }

            currentPrompt = applyRecommendations(currentPrompt, validation.getRecommendations(), validation.getExplanation());
            log.debug("Refined prompt (iteration {}): {}", i + 1, currentPrompt);
        }

        log.warn("Max iterations reached without meeting threshold, using last refined version");
        return currentPrompt;
    }

    private String applyRecommendations(String prompt, java.util.List<String> recommendations, String explanation) {
        StringBuilder refinedPrompt = new StringBuilder(prompt);
        refinedPrompt.append("\n\nAdditional guidance to improve prompt quality:");
        
        for (String recommendation : recommendations) {
            refinedPrompt.append("\n- ").append(recommendation);
        }

        if (explanation != null && !explanation.isEmpty()) {
            refinedPrompt.append("\n\nContext: ").append(explanation);
        }

        return refinedPrompt.toString();
    }
}
