package io.sentrius.agent.monitoring.service;

import io.sentrius.agent.monitoring.model.MonitoringConfig;
import io.sentrius.sso.core.model.LLMResponse;
import io.sentrius.sso.core.services.openai.OpenAITwoPartyMonitorService;
import io.sentrius.sso.genai.model.TwoPartyRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * LLM-guided monitoring service for monitoring agent.
 * Uses LLM to determine monitoring cadence and what should be evaluated.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "agents.monitoring.llm-guided", havingValue = "true", matchIfMissing = false)
public class LLMGuidedMonitoringService {

    private final OpenAITwoPartyMonitorService llmService;
    private final EndpointMonitoringService endpointMonitoringService;

    @Value("${agents.monitoring.llm-guidance-interval:60000}")
    private long llmGuidanceInterval; // Default 1 minute

    /**
     * Ask LLM whether endpoint monitoring should run now
     */
    public CompletableFuture<Boolean> shouldRunEndpointMonitoring() {
        if (!llmService.isEnabled()) {
            log.debug("LLM service not available, defaulting to running endpoint monitoring");
            return CompletableFuture.completedFuture(true);
        }

        var endpointHealth = endpointMonitoringService.getAllEndpointHealth();
        StringBuilder healthSummary = new StringBuilder();
        endpointHealth.forEach((url, health) -> {
            healthSummary.append(String.format("- %s: %s (last checked: %s)\n", 
                url, health.getStatus(), health.getLastChecked()));
        });

        String prompt = String.format(
            "Current time: %s\nEndpoints being monitored: %d\nCurrent health:\n%s\n\n" +
            "Should endpoint monitoring run now? Consider recent check times, health status, and system load. " +
            "Respond with a score from 0.0 (definitely skip) to 1.0 (definitely run).",
            java.time.Instant.now(),
            endpointHealth.size(),
            healthSummary.toString()
        );

        TwoPartyRequest request = TwoPartyRequest.builder()
            .systemInput("You are an intelligent scheduler for a monitoring system. " +
                "Your role is to optimize when endpoint health checks should run.")
            .userInput(prompt)
            .build();

        return llmService.analyzeTerminalLogs(request)
            .thenApply(response -> {
                if (response != null) {
                    boolean shouldRun = response.getScore() >= 0.5;
                    log.info("LLM guidance for endpoint monitoring: score={}, shouldRun={}", 
                        response.getScore(), shouldRun);
                    return shouldRun;
                }
                return true;
            })
            .exceptionally(ex -> {
                log.error("Error getting LLM guidance for endpoint monitoring, defaulting to true", ex);
                return true;
            });
    }

    /**
     * Ask LLM whether stability evaluation should run now
     */
    public CompletableFuture<Boolean> shouldRunStabilityEvaluation() {
        if (!llmService.isEnabled()) {
            return CompletableFuture.completedFuture(true);
        }

        String prompt = String.format(
            "Current time: %s\n\n" +
            "Should AI-based stability evaluation run now? Consider system activity and recent evaluations. " +
            "Respond with a score from 0.0 (definitely skip) to 1.0 (definitely run).",
            java.time.Instant.now()
        );

        TwoPartyRequest request = TwoPartyRequest.builder()
            .systemInput("You are an intelligent scheduler for stability evaluation. " +
                "Balance thoroughness with resource efficiency.")
            .userInput(prompt)
            .build();

        return llmService.analyzeTerminalLogs(request)
            .thenApply(response -> {
                if (response != null) {
                    boolean shouldRun = response.getScore() >= 0.6;
                    log.info("LLM guidance for stability evaluation: score={}, shouldRun={}", 
                        response.getScore(), shouldRun);
                    return shouldRun;
                }
                return true;
            })
            .exceptionally(ex -> {
                log.error("Error getting LLM guidance for stability evaluation", ex);
                return true;
            });
    }

    /**
     * Get LLM recommendation for which endpoints to prioritize
     */
    public CompletableFuture<String> getEndpointPriorities() {
        if (!llmService.isEnabled()) {
            return CompletableFuture.completedFuture("all");
        }

        var configs = endpointMonitoringService.getAllMonitoringConfigs();
        StringBuilder configSummary = new StringBuilder();
        configs.forEach((url, config) -> {
            configSummary.append(String.format("- %s: service=%s, critical=%s\n", 
                url, config.getServiceName(), config.isNotifyOnDown()));
        });

        String prompt = String.format(
            "Monitored endpoints:\n%s\n\n" +
            "Which endpoints should be prioritized for the next monitoring cycle? " +
            "Consider criticality and recent health patterns. " +
            "Respond with a comma-separated list of URLs or 'all' for all endpoints.",
            configSummary.toString()
        );

        TwoPartyRequest request = TwoPartyRequest.builder()
            .systemInput("You prioritize which endpoints to monitor based on criticality and health patterns.")
            .userInput(prompt)
            .build();

        return llmService.analyzeTerminalLogs(request)
            .thenApply(response -> {
                if (response != null && response.getResponse() != null) {
                    log.info("LLM recommended endpoint priorities: {}", response.getResponse());
                    return response.getResponse();
                }
                return "all";
            })
            .exceptionally(ex -> {
                log.error("Error getting LLM endpoint priorities", ex);
                return "all";
            });
    }

    /**
     * Get LLM recommendation for monitoring interval adjustment
     */
    public CompletableFuture<Long> getRecommendedInterval(String endpointUrl) {
        if (!llmService.isEnabled()) {
            return CompletableFuture.completedFuture(llmGuidanceInterval);
        }

        var health = endpointMonitoringService.getAllEndpointHealth().get(endpointUrl);
        String healthInfo = health != null ? 
            String.format("Status: %s, Error Rate: %.2f%%", health.getStatus(), health.getErrorRate() != null ? health.getErrorRate() : 0.0) :
            "No health data available";

        String prompt = String.format(
            "Endpoint: %s\nCurrent health: %s\n\n" +
            "What should be the monitoring interval in milliseconds? " +
            "Recommend between 30000 (30s) and 300000 (5min) based on health and criticality. " +
            "Respond with just a number.",
            endpointUrl,
            healthInfo
        );

        TwoPartyRequest request = TwoPartyRequest.builder()
            .systemInput("You optimize monitoring intervals based on endpoint health and criticality. " +
                "Unhealthy endpoints need more frequent checks.")
            .userInput(prompt)
            .build();

        return llmService.analyzeTerminalLogs(request)
            .thenApply(response -> {
                if (response != null && response.getResponse() != null) {
                    try {
                        long interval = Long.parseLong(response.getResponse().replaceAll("[^0-9]", ""));
                        // Clamp to reasonable range
                        interval = Math.max(30000, Math.min(300000, interval));
                        log.info("LLM recommended interval for {}: {}ms", endpointUrl, interval);
                        return interval;
                    } catch (NumberFormatException e) {
                        log.warn("Could not parse LLM interval recommendation: {}", response.getResponse());
                    }
                }
                return llmGuidanceInterval;
            })
            .exceptionally(ex -> {
                log.error("Error getting LLM interval recommendation", ex);
                return llmGuidanceInterval;
            });
    }

    public boolean isEnabled() {
        return llmService.isEnabled();
    }
}
