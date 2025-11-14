package io.sentrius.sso.controllers.api;

import io.sentrius.sso.config.ApiPaths;
import io.sentrius.sso.core.annotations.LimitAccess;
import io.sentrius.sso.core.model.ErrorOutput;
import io.sentrius.sso.core.model.selfhealing.SelfHealingConfig;
import io.sentrius.sso.core.model.selfhealing.SelfHealingSession;
import io.sentrius.sso.core.model.security.enums.ApplicationAccessEnum;
import io.sentrius.sso.core.services.ErrorOutputService;
import io.sentrius.sso.core.services.security.IntegrationSecurityTokenService;
import io.sentrius.sso.core.services.selfhealing.ErrorAnalysisService;
import io.sentrius.sso.core.services.selfhealing.SelfHealingConfigService;
import io.sentrius.sso.core.services.selfhealing.SelfHealingSessionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping(ApiPaths.API_V1 + "/self-healing")
public class SelfHealingApiController {

    @Autowired
    private SelfHealingConfigService configService;

    @Autowired
    private SelfHealingSessionService sessionService;

    @Autowired
    private ErrorAnalysisService errorAnalysisService;

    @Autowired
    private ErrorOutputService errorOutputService;

    @Autowired(required = false)
    private IntegrationSecurityTokenService integrationTokenService;

    @Value("${self-healing.github.enabled:false}")
    private boolean githubConfigured;

    /**
     * Check if GitHub integration is available
     */
    private boolean isGitHubIntegrationAvailable() {
        if (!githubConfigured) {
            return false;
        }

        if (integrationTokenService == null) {
            return false;
        }

        try {
            List<?> githubTokens = integrationTokenService.findByConnectionType("github");
            return githubTokens != null && !githubTokens.isEmpty();
        } catch (Exception e) {
            log.error("Error checking GitHub integration tokens", e);
            return false;
        }
    }

    /**
     * Get all self-healing configurations
     */
    @GetMapping("/config")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public ResponseEntity<List<SelfHealingConfig>> getAllConfigs() {
        try {
            List<SelfHealingConfig> configs = configService.getAllConfigs();
            return ResponseEntity.ok(configs);
        } catch (Exception e) {
            log.error("Error fetching self-healing configs", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get configuration for a specific pod
     */
    @GetMapping("/config/{podName}")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public ResponseEntity<SelfHealingConfig> getConfigByPodName(@PathVariable String podName) {
        try {
            return configService.getConfigByPodName(podName)
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            log.error("Error fetching config for pod: {}", podName, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Create or update self-healing configuration
     */
    @PostMapping("/config")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public ResponseEntity<SelfHealingConfig> saveConfig(@RequestBody SelfHealingConfig config) {
        try {
            SelfHealingConfig saved = configService.saveConfig(config);
            return ResponseEntity.ok(saved);
        } catch (Exception e) {
            log.error("Error saving self-healing config", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Delete self-healing configuration
     */
    @DeleteMapping("/config/{id}")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public ResponseEntity<Void> deleteConfig(@PathVariable Long id) {
        try {
            configService.deleteConfig(id);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Error deleting self-healing config: {}", id, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get all self-healing sessions
     */
    @GetMapping("/sessions")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public ResponseEntity<List<SelfHealingSession>> getAllSessions() {
        try {
            List<SelfHealingSession> sessions = sessionService.getAllSessions();
            return ResponseEntity.ok(sessions);
        } catch (Exception e) {
            log.error("Error fetching self-healing sessions", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get a specific self-healing session
     */
    @GetMapping("/sessions/{id}")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public ResponseEntity<SelfHealingSession> getSessionById(@PathVariable Long id) {
        try {
            return sessionService.getSessionById(id)
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            log.error("Error fetching session: {}", id, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Manually trigger self-healing for a specific error
     */
    @PostMapping("/trigger/{errorId}")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public ResponseEntity<Map<String, Object>> triggerHealing(@PathVariable Long errorId) {
        try {
            Map<String, Object> response = new HashMap<>();
            
            // Check if GitHub integration is available
            if (!isGitHubIntegrationAvailable()) {
                response.put("success", false);
                response.put("message", "Self-healing requires GitHub integration to be configured. " +
                        "Please add a GitHub integration token before triggering self-healing.");
                return ResponseEntity.ok(response);
            }

            ErrorOutput error = errorOutputService.getErrorOutputById(errorId);
            
            if (!errorAnalysisService.shouldTriggerHealing(error)) {
                response.put("success", false);
                response.put("message", "Self-healing not enabled or already in progress for this error");
                return ResponseEntity.ok(response);
            }

            SelfHealingSession session = errorAnalysisService.initiateHealing(error);
            
            response.put("success", true);
            response.put("sessionId", session.getId());
            response.put("message", "Self-healing initiated");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error triggering self-healing for error: {}", errorId, e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error triggering self-healing: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
}
