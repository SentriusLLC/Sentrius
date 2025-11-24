package io.sentrius.agent.analysis.service;

import io.sentrius.sso.core.model.LLMResponse;
import io.sentrius.sso.core.services.openai.OpenAITwoPartyMonitorService;
import io.sentrius.sso.genai.model.TwoPartyRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * LLM-guided scheduler service for analytics agent.
 * Uses LLM to determine when evaluations should run and what should be evaluated.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "agents.analytics.llm-guided", havingValue = "true", matchIfMissing = false)
public class LLMGuidedSchedulerService {

    private final OpenAITwoPartyMonitorService llmService;

    @Value("${agents.analytics.llm-guidance-interval:300000}")
    private long llmGuidanceInterval; // Default 5 minutes

    /**
     * Ask LLM whether a specific evaluation should run now
     */
    public CompletableFuture<Boolean> shouldRunTrustEvaluation() {
        return askLLMForGuidance(
            "trust evaluation",
            "Determine if trust evaluation for agents and users should run now based on recent activity",
            0.5 // Lower threshold for trust evaluation (should run more often)
        );
    }

    /**
     * Ask LLM whether automation suggestion analysis should run
     */
    public CompletableFuture<Boolean> shouldRunAutomationAnalysis() {
        return askLLMForGuidance(
            "automation suggestion analysis",
            "Determine if automation suggestion analysis should run now based on recent session activity",
            0.6 // Medium threshold
        );
    }

    /**
     * Ask LLM whether session summarization should run
     */
    public CompletableFuture<Boolean> shouldRunSessionSummarization() {
        return askLLMForGuidance(
            "session summarization",
            "Determine if session summarization should run now based on pending sessions",
            0.4 // Lower threshold (should run frequently)
        );
    }

    /**
     * Ask LLM whether memory evaluation should run
     */
    public CompletableFuture<Boolean> shouldRunMemoryEvaluation() {
        return askLLMForGuidance(
            "memory evaluation",
            "Determine if memory evaluation for public classification should run now",
            0.7 // Higher threshold (can run less frequently)
        );
    }

    /**
     * Generic method to ask LLM for guidance on whether to run an evaluation
     */
    private CompletableFuture<Boolean> askLLMForGuidance(String evaluationType, String context, double threshold) {
        if (!llmService.isEnabled()) {
            log.debug("LLM service not available, defaulting to running {}", evaluationType);
            return CompletableFuture.completedFuture(true);
        }

        String prompt = String.format(
            "Current time: %s\nEvaluation type: %s\nContext: %s\n\n" +
            "Should this evaluation run now? Consider system load, recent activity, and priority. " +
            "Respond with a score from 0.0 (definitely skip) to 1.0 (definitely run).",
            java.time.Instant.now(),
            evaluationType,
            context
        );

        TwoPartyRequest request = TwoPartyRequest.builder()
            .systemInput("You are an intelligent scheduler for an analytics system. " +
                "Your role is to optimize when various analytics evaluations should run.")
            .userInput(prompt)
            .build();

        return llmService.analyzeTerminalLogs(request)
            .thenApply(response -> {
                if (response != null) {
                    boolean shouldRun = response.getScore() >= threshold;
                    log.info("LLM guidance for {}: score={}, shouldRun={}", 
                        evaluationType, response.getScore(), shouldRun);
                    return shouldRun;
                }
                log.warn("No LLM response for {}, defaulting to true", evaluationType);
                return true;
            })
            .exceptionally(ex -> {
                log.error("Error getting LLM guidance for {}, defaulting to true", evaluationType, ex);
                return true;
            });
    }

    /**
     * Get LLM recommendation for what specific items should be evaluated
     */
    public CompletableFuture<String> getEvaluationFocus(String evaluationType, String availableItems) {
        if (!llmService.isEnabled()) {
            log.debug("LLM service not available, returning all items");
            return CompletableFuture.completedFuture(availableItems);
        }

        String prompt = String.format(
            "Evaluation type: %s\nAvailable items:\n%s\n\n" +
            "Which items should be prioritized for evaluation? " +
            "Respond with a comma-separated list of the most important items to evaluate.",
            evaluationType,
            availableItems
        );

        TwoPartyRequest request = TwoPartyRequest.builder()
            .systemInput("You are an intelligent scheduler that prioritizes analytics work. " +
                "Focus on items that are most critical or have changed recently.")
            .userInput(prompt)
            .build();

        return llmService.analyzeTerminalLogs(request)
            .thenApply(response -> {
                if (response != null && response.getResponse() != null) {
                    log.info("LLM recommended focus for {}: {}", evaluationType, response.getResponse());
                    return response.getResponse();
                }
                return availableItems;
            })
            .exceptionally(ex -> {
                log.error("Error getting LLM evaluation focus", ex);
                return availableItems;
            });
    }

    public boolean isEnabled() {
        return llmService.isEnabled();
    }
}
