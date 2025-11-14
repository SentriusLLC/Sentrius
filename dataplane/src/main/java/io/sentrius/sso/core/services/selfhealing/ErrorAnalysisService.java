package io.sentrius.sso.core.services.selfhealing;

import io.sentrius.sso.core.model.ErrorOutput;
import io.sentrius.sso.core.model.selfhealing.SelfHealingConfig.PatchingPolicy;
import io.sentrius.sso.core.model.selfhealing.SelfHealingSession;
import io.sentrius.sso.core.model.selfhealing.SelfHealingSession.HealingStatus;
import io.sentrius.sso.core.services.ErrorOutputService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service responsible for analyzing errors and determining if self-healing should be triggered
 */
@Slf4j
@Service
public class ErrorAnalysisService {

    @Autowired
    private SelfHealingConfigService configService;

    @Autowired
    private SelfHealingSessionService sessionService;

    @Autowired
    private ErrorOutputService errorOutputService;

    // Pattern to extract pod/service names from error logs
    private static final Pattern POD_NAME_PATTERN = Pattern.compile(
            "(?:pod|service|container)\\s+['\"]?([a-z0-9-]+)['\"]?",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * Analyzes an error and determines if it should trigger self-healing
     */
    public boolean shouldTriggerHealing(ErrorOutput error) {
        String podName = extractPodName(error);
        if (podName == null) {
            log.debug("Could not extract pod name from error {}", error.getId());
            return false;
        }

        if (!configService.isHealingEnabledForPod(podName)) {
            log.debug("Self-healing not enabled for pod: {}", podName);
            return false;
        }

        PatchingPolicy policy = configService.getPatchingPolicyForPod(podName);
        if (policy == PatchingPolicy.NEVER) {
            log.debug("Patching policy is NEVER for pod: {}", podName);
            return false;
        }

        // Check if there's already an active session for this error
        if (sessionService.getSessionByErrorOutputId(error.getId()).isPresent()) {
            log.debug("Active healing session already exists for error {}", error.getId());
            return false;
        }

        return true;
    }

    /**
     * Initiates the self-healing process for an error
     */
    public SelfHealingSession initiateHealing(ErrorOutput error) {
        String podName = extractPodName(error);
        if (podName == null) {
            throw new IllegalArgumentException("Cannot initiate healing: pod name not found in error");
        }

        log.info("Initiating self-healing for error {} on pod {}", error.getId(), podName);
        
        SelfHealingSession session = sessionService.createSession(error, podName);
        
        // Update error output status
        error.setHealingStatus("QUEUED");
        error.setHealingSessionId(session.getId());
        errorOutputService.saveErrorOutput(error);

        return session;
    }

    /**
     * Extracts pod/service name from error logs
     */
    public String extractPodName(ErrorOutput error) {
        if (error.getErrorLocation() != null && !error.getErrorLocation().isEmpty()) {
            return error.getErrorLocation();
        }

        if (error.getErrorLogs() != null) {
            Matcher matcher = POD_NAME_PATTERN.matcher(error.getErrorLogs());
            if (matcher.find()) {
                return matcher.group(1);
            }
        }

        return null;
    }

    /**
     * Simple heuristic to determine if an error might be a security concern
     * This is a basic implementation - in production, this would use more sophisticated analysis
     */
    public boolean isLikelySecurityConcern(ErrorOutput error) {
        String logs = error.getErrorLogs().toLowerCase();
        
        // Check for common security-related keywords
        String[] securityKeywords = {
                "authentication", "authorization", "access denied", "forbidden",
                "security", "vulnerability", "exploit", "injection", "xss",
                "csrf", "unauthorized", "privilege", "crypto", "ssl", "tls"
        };

        for (String keyword : securityKeywords) {
            if (logs.contains(keyword)) {
                log.info("Error {} flagged as potential security concern due to keyword: {}", 
                        error.getId(), keyword);
                return true;
            }
        }

        return false;
    }
}
