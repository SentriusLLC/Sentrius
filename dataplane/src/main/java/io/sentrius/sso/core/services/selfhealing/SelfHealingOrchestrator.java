package io.sentrius.sso.core.services.selfhealing;

import io.sentrius.sso.core.model.ErrorOutput;
import io.sentrius.sso.core.model.selfhealing.SelfHealingConfig.PatchingPolicy;
import io.sentrius.sso.core.model.selfhealing.SelfHealingSession;
import io.sentrius.sso.core.model.selfhealing.SelfHealingSession.HealingStatus;
import io.sentrius.sso.core.services.ErrorOutputService;
import io.sentrius.sso.core.services.security.IntegrationSecurityTokenService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.util.List;

/**
 * Orchestrates the self-healing process by monitoring errors and coordinating healing attempts
 */
@Slf4j
@Service
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SelfHealingOrchestrator {

    @Autowired
    private ErrorOutputService errorOutputService;

    @Autowired
    private ErrorAnalysisService errorAnalysisService;

    @Autowired
    private SelfHealingConfigService configService;

    @Autowired
    private SelfHealingSessionService sessionService;

    @Autowired
    private HealingWorkflowCoordinator workflowCoordinator;

    @Autowired(required = false)
    private IntegrationSecurityTokenService integrationTokenService;

    @Value("${self-healing.enabled:true}")
    private boolean selfHealingEnabled;

    @Value("${self-healing.off-hours.start:22}")
    private int offHoursStart;

    @Value("${self-healing.off-hours.end:6}")
    private int offHoursEnd;

    @Value("${self-healing.github.enabled:false}")
    private boolean githubConfigured;

    /**
     * Check if GitHub integration is available
     * Self-healing requires GitHub integration to submit PRs
     * GitHub integration is considered available if integration tokens exist in the database
     */
    private boolean isGitHubIntegrationAvailable() {
        if (integrationTokenService == null) {
            log.warn("IntegrationSecurityTokenService not available");
            return false;
        }

        try {
            List<?> githubTokens = integrationTokenService.findByConnectionType("github");
            boolean hasTokens = githubTokens != null && !githubTokens.isEmpty();
            
            if (!hasTokens) {
                log.debug("No GitHub integration tokens found in database");
            }
            
            return hasTokens;
        } catch (Exception e) {
            log.error("Error checking GitHub integration tokens", e);
            return false;
        }
    }

    /**
     * Periodically scans for errors that should trigger self-healing
     * Runs every 5 minutes
     */
    @Scheduled(fixedDelay = 300000) // 5 minutes
    public void scanForHealableErrors() {
        if (!selfHealingEnabled) {
            return;
        }

        // Check if GitHub integration is available
        if (!isGitHubIntegrationAvailable()) {
            log.warn("Self-healing is enabled but GitHub integration is not configured. " +
                    "Please configure GitHub integration tokens to enable self-healing.");
            return;
        }

        try {
            log.debug("Scanning for errors that should trigger self-healing");
            
            // Get recent errors
            List<ErrorOutput> recentErrors = errorOutputService.getErrorOutputs(0, 50);
            
            for (ErrorOutput error : recentErrors) {
                // Skip if already being healed or healing is not applicable
                if (error.getHealingStatus() != null && !error.getHealingStatus().equals("NONE")) {
                    continue;
                }

                if (errorAnalysisService.shouldTriggerHealing(error)) {
                    processErrorForHealing(error);
                }
            }
        } catch (Exception e) {
            log.error("Error during healing scan", e);
        }
    }

    /**
     * Process a single error for healing based on its patching policy
     */
    @Async
    public void processErrorForHealing(ErrorOutput error) {
        try {
            String podName = errorAnalysisService.extractPodName(error);
            if (podName == null) {
                log.debug("Cannot process error {}: pod name not found", error.getId());
                return;
            }

            PatchingPolicy policy = configService.getPatchingPolicyForPod(podName);
            
            boolean shouldHealNow = false;
            
            switch (policy) {
                case IMMEDIATE:
                    shouldHealNow = true;
                    log.info("Immediate healing triggered for error {} on pod {}", error.getId(), podName);
                    break;
                    
                case OFF_HOURS:
                    if (isOffHours()) {
                        shouldHealNow = true;
                        log.info("Off-hours healing triggered for error {} on pod {}", error.getId(), podName);
                    } else {
                        log.debug("Queuing error {} for off-hours healing", error.getId());
                        error.setHealingStatus("QUEUED");
                        errorOutputService.saveErrorOutput(error);
                    }
                    break;
                    
                case NEVER:
                default:
                    log.debug("Healing disabled for pod {}", podName);
                    return;
            }

            if (shouldHealNow) {
                initiateHealingSession(error);
            }
        } catch (Exception e) {
            log.error("Error processing error {} for healing", error.getId(), e);
        }
    }

    /**
     * Initiate a healing session for an error
     */
    private void initiateHealingSession(ErrorOutput error) {
        try {
            // Check for security concerns first
            boolean isSecurityConcern = errorAnalysisService.isLikelySecurityConcern(error);
            
            error.setIsSecurityConcern(isSecurityConcern);
            errorOutputService.saveErrorOutput(error);

            // Create the healing session
            SelfHealingSession session = errorAnalysisService.initiateHealing(error);
            
            if (isSecurityConcern) {
                // Mark session as requiring manual review
                sessionService.recordSecurityAnalysis(
                    session.getId(), 
                    true, 
                    "Error flagged as potential security concern. Manual review required before proceeding with healing."
                );
                log.warn("Error {} flagged as security concern, healing paused for review", error.getId());
            } else {
                // Proceed with healing - start the complete workflow
                sessionService.updateSessionStatus(session.getId(), HealingStatus.ANALYZING);
                log.info("Healing session {} started for error {}", session.getId(), error.getId());
                
                // Execute the complete healing workflow asynchronously
                workflowCoordinator.executeHealingWorkflow(session);
            }
        } catch (Exception e) {
            log.error("Error initiating healing session for error {}", error.getId(), e);
        }
    }

    /**
     * Process queued errors during off-hours
     * Runs every hour
     */
    @Scheduled(cron = "0 0 * * * *") // Every hour
    public void processQueuedErrors() {
        if (!selfHealingEnabled || !isOffHours()) {
            return;
        }

        // Check if GitHub integration is available
        if (!isGitHubIntegrationAvailable()) {
            log.warn("Cannot process queued errors: GitHub integration is not configured");
            return;
        }

        try {
            log.info("Processing queued errors during off-hours");
            
            List<ErrorOutput> errors = errorOutputService.getAllErrorOutputs();
            for (ErrorOutput error : errors) {
                if ("QUEUED".equals(error.getHealingStatus())) {
                    String podName = errorAnalysisService.extractPodName(error);
                    PatchingPolicy policy = configService.getPatchingPolicyForPod(podName);
                    
                    if (policy == PatchingPolicy.OFF_HOURS) {
                        initiateHealingSession(error);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error processing queued errors", e);
        }
    }

    /**
     * Determine if current time is within off-hours window
     */
    private boolean isOffHours() {
        LocalTime now = LocalTime.now();
        int currentHour = now.getHour();
        
        // Handle wrap-around (e.g., 22:00 to 06:00)
        if (offHoursStart > offHoursEnd) {
            return currentHour >= offHoursStart || currentHour <= offHoursEnd;
        } else {
            return currentHour >= offHoursStart && currentHour <= offHoursEnd;
        }
    }
}
