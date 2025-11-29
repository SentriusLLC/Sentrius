package io.sentrius.sso.promptadvisor.controller;

import io.sentrius.sso.core.config.SystemOptions;
import io.sentrius.sso.core.controllers.BaseController;
import io.sentrius.sso.core.model.verbs.Endpoint;
import io.sentrius.sso.core.services.ErrorOutputService;
import io.sentrius.sso.core.services.UserService;
import io.sentrius.sso.core.promptadvisor.model.RefinePromptResponse;
import io.sentrius.sso.core.promptadvisor.model.ValidatePromptRequest;
import io.sentrius.sso.core.promptadvisor.model.ValidatePromptResponse;
import io.sentrius.sso.core.promptadvisor.service.PromptAdvisorService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/prompt-advisor")
@Slf4j
public class PromptAdvisorController extends BaseController {

    private final PromptAdvisorService promptAdvisorService;

    protected PromptAdvisorController(
        UserService userService,
        SystemOptions systemOptions,
        ErrorOutputService errorOutputService,
        PromptAdvisorService promptAdvisorService
    ) {
        super(userService, systemOptions, errorOutputService);
        this.promptAdvisorService = promptAdvisorService;
    }

    @PostMapping("/validate")
    @Endpoint(description = "Validate a prompt against ATPL criteria")
    public ResponseEntity<?> validatePrompt(
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

        log.info("Validating prompt for user: {}", operatingUser.getUsername());

        ValidatePromptResponse response = promptAdvisorService.validatePrompt(
            request.getPrompt(),
            request.getContext()
        );

        if (response == null) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to validate prompt"));
        }

        return ResponseEntity.ok(response);
    }

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

    @GetMapping("/status")
    @Endpoint(description = "Get prompt advisor service status")
    public ResponseEntity<?> getStatus(
        HttpServletRequest httpRequest,
        HttpServletResponse httpResponse
    ) {
        var operatingUser = getOperatingUser(httpRequest, httpResponse);
        if (operatingUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "Authentication required"));
        }

        Map<String, Object> status = new HashMap<>();
        status.put("enabled", systemOptions.getEnablePromptAdvisor());
        status.put("threshold", systemOptions.getPromptAdvisorThreshold());
        status.put("max_iterations", systemOptions.getPromptAdvisorMaxIterations());
        status.put("endpoint", systemOptions.getPromptAdvisorEndpoint());

        return ResponseEntity.ok(status);
    }
}
