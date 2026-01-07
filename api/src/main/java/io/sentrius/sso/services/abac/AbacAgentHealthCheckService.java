package io.sentrius.sso.services.abac;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for monitoring ABAC agent health.
 * Periodically checks if the ABAC agent is alive and records health status.
 */
@Slf4j
@Service
@ConditionalOnProperty(value = "sentrius.abac.agent.health-check.enabled", havingValue = "true", matchIfMissing = true)
public class AbacAgentHealthCheckService {

    private final RestTemplate restTemplate;
    private final Map<String, LocalDateTime> lastHealthCheck = new ConcurrentHashMap<>();
    private final Map<String, String> healthStatus = new ConcurrentHashMap<>();

    @Value("${sentrius.abac.agent.enabled:true}")
    private boolean abacAgentEnabled;

    @Value("${sentrius.agent.launcher.url:http://localhost:8090}")
    private String agentLauncherUrl;

    @Value("${sentrius.abac.agent.health-check.interval:300000}") // Default 5 minutes
    private long healthCheckInterval;

    public AbacAgentHealthCheckService() {
        this.restTemplate = new RestTemplate();
    }

    /**
     * Periodically check the health of all ABAC agents.
     * Runs every 5 minutes by default (configurable via sentrius.abac.agent.health-check.interval).
     */
    @Scheduled(fixedDelayString = "${sentrius.abac.agent.health-check.interval:300000}")
    public void checkAbacAgentHealth() {
        if (!abacAgentEnabled) {
            log.debug("ABAC agent health check skipped - agent is disabled");
            return;
        }

        try {
            log.debug("Performing ABAC agent health check");

            // Check the agent launcher for ABAC agents
            String healthUrl = agentLauncherUrl + "/api/v1/agent/launcher/status";

            // For simplicity, we're checking the general health endpoint
            // In production, this would query for specific ABAC agent instances
            Map<String, Object> response = restTemplate.getForObject(healthUrl, Map.class);

            if (response != null) {
                String status = response.getOrDefault("status", "unknown").toString();
                healthStatus.put("abac-agent-general", status);
                lastHealthCheck.put("abac-agent-general", LocalDateTime.now());

                if (!"running".equalsIgnoreCase(status) && !"Running".equals(status)) {
                    log.warn("ABAC agent health check indicates agent may not be running: {}", status);
                } else {
                    log.debug("ABAC agent health check successful: {}", status);
                }
            }

        } catch (Exception e) {
            log.error("Error during ABAC agent health check", e);
            healthStatus.put("abac-agent-general", "error");
            lastHealthCheck.put("abac-agent-general", LocalDateTime.now());
        }
    }

    /**
     * Get the last health check time for an agent.
     */
    public LocalDateTime getLastHealthCheck(String agentId) {
        return lastHealthCheck.getOrDefault(agentId, null);
    }

    /**
     * Get the current health status for an agent.
     */
    public String getHealthStatus(String agentId) {
        return healthStatus.getOrDefault(agentId, "unknown");
    }

    /**
     * Check if the agent is currently healthy (last check was successful and recent).
     */
    public boolean isAgentHealthy(String agentId) {
        LocalDateTime lastCheck = lastHealthCheck.get(agentId);
        String status = healthStatus.get(agentId);

        if (lastCheck == null || status == null) {
            return false;
        }

        // Consider healthy if last check was within 2x the health check interval
        long minutesSinceCheck = java.time.Duration.between(lastCheck, LocalDateTime.now()).toMinutes();
        long maxMinutes = (healthCheckInterval / 60000) * 2; // Convert ms to minutes and double it

        return "running".equalsIgnoreCase(status) && minutesSinceCheck < maxMinutes;
    }

    /**
     * Get all health statuses.
     */
    public Map<String, String> getAllHealthStatuses() {
        return new ConcurrentHashMap<>(healthStatus);
    }
}
