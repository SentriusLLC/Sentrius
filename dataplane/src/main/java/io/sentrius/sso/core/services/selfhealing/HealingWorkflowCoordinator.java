package io.sentrius.sso.core.services.selfhealing;

import io.sentrius.sso.core.model.selfhealing.SelfHealingSession;
import io.sentrius.sso.core.model.selfhealing.SelfHealingSession.HealingStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Coordinates the complete self-healing workflow:
 * 1. Launch coding agent to analyze and fix the error
 * 2. Build Docker image with the fixes (via agent-launcher service)
 * 3. Create GitHub PR (if configured)
 * 4. Update healing session with results
 */
@Slf4j
@Service
public class HealingWorkflowCoordinator {

    @Autowired
    private CodingAgentLauncherService codingAgentLauncher;

    @Autowired
    private SelfHealingSessionService sessionService;

    @Value("${self-healing.auto-build-image:true}")
    private boolean autoBuildImage;

    @Value("${self-healing.agent-launcher.url:http://localhost:8080}")
    private String agentLauncherUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Execute the complete healing workflow asynchronously
     * 
     * @param session The healing session
     */
    @Async
    public void executeHealingWorkflow(SelfHealingSession session) {
        try {
            log.info("Starting healing workflow for session {}", session.getId());
            
            // Step 1: Launch coding agent
            String agentId = codingAgentLauncher.launchCodingAgent(session);
            if (agentId == null) {
                failSession(session, "Failed to launch coding agent");
                return;
            }
            
            // Step 2: Wait for agent to complete (with timeout)
            boolean agentCompleted = waitForAgentCompletion(session, 1800); // 30 minute timeout
            if (!agentCompleted) {
                failSession(session, "Coding agent timed out or failed");
                codingAgentLauncher.terminateAgent(session);
                return;
            }
            
            // Reload session to get latest updates
            session = sessionService.getSessionById(session.getId())
                    .orElseThrow(() -> new RuntimeException("Session not found"));
            
            // Check if agent marked it as completed
            if (session.getStatus() == HealingStatus.FAILED) {
                log.error("Coding agent failed for session {}", session.getId());
                return;
            }
            
            // Step 3: Build Docker image if auto-build is enabled
            if (autoBuildImage) {
                log.info("Requesting Docker image build for session {}", session.getId());
                
                String buildJobName = triggerDockerBuild(session);
                
                if (buildJobName != null) {
                    recordBuildJob(session, buildJobName);
                    
                    // Wait for build to complete
                    boolean buildCompleted = waitForBuildCompletion(buildJobName, 1800); // 30 minute timeout
                    if (buildCompleted) {
                        log.info("Docker image built successfully for session {}", session.getId());
                        completeSession(session, "Healing completed with Docker image built");
                    } else {
                        failSession(session, "Docker image build failed or timed out");
                    }
                } else {
                    failSession(session, "Failed to start Docker image build");
                }
            } else {
                // No auto-build, just mark as completed
                completeSession(session, "Healing completed (auto-build disabled)");
            }
            
        } catch (Exception e) {
            log.error("Error in healing workflow for session {}", session.getId(), e);
            failSession(session, "Workflow error: " + e.getMessage());
        }
    }

    /**
     * Trigger Docker image build via the agent-launcher service
     */
    private String triggerDockerBuild(SelfHealingSession session) {
        try {
            String buildUrl = agentLauncherUrl + "/api/v1/builder/build";
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            Map<String, Object> buildRequest = Map.of(
                    "sessionId", session.getId(),
                    "podName", session.getPodName() != null ? session.getPodName() : "unknown",
                    "dockerfilePath", "/workspace/Dockerfile",
                    "contextPath", "/workspace"
            );
            
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(buildRequest, headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(
                    buildUrl,
                    HttpMethod.POST,
                    request,
                    Map.class
            );
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return (String) response.getBody().get("jobName");
            }
            
            return null;
            
        } catch (Exception e) {
            log.error("Error triggering Docker build", e);
            return null;
        }
    }

    /**
     * Wait for the coding agent to complete its work
     * 
     * @param session The healing session
     * @param timeoutSeconds Maximum time to wait
     * @return true if completed successfully, false if timeout or failure
     */
    private boolean waitForAgentCompletion(SelfHealingSession session, int timeoutSeconds) {
        long startTime = System.currentTimeMillis();
        long timeoutMs = timeoutSeconds * 1000L;
        
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            try {
                boolean stillRunning = codingAgentLauncher.monitorAgentProgress(session);
                
                if (!stillRunning) {
                    // Reload session to check final status
                    session = sessionService.getSessionById(session.getId())
                            .orElseThrow(() -> new RuntimeException("Session not found"));
                    
                    return session.getStatus() == HealingStatus.COMPLETED || 
                           session.getStatus() == HealingStatus.FIXING;
                }
                
                // Wait 10 seconds before checking again
                Thread.sleep(10000);
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            } catch (Exception e) {
                log.error("Error waiting for agent completion", e);
                return false;
            }
        }
        
        log.warn("Agent completion timed out for session {}", session.getId());
        return false;
    }

    /**
     * Wait for Docker image build to complete
     * 
     * @param buildJobName The build job name
     * @param timeoutSeconds Maximum time to wait
     * @return true if succeeded, false if failed or timeout
     */
    private boolean waitForBuildCompletion(String buildJobName, int timeoutSeconds) {
        long startTime = System.currentTimeMillis();
        long timeoutMs = timeoutSeconds * 1000L;
        
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            try {
                String statusUrl = agentLauncherUrl + "/api/v1/builder/status?jobName=" + buildJobName;
                
                ResponseEntity<Map> response = restTemplate.getForEntity(statusUrl, Map.class);
                
                if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                    String status = (String) response.getBody().get("status");
                    
                    switch (status) {
                        case "Succeeded":
                            return true;
                        case "Failed":
                            log.error("Docker build failed for job {}", buildJobName);
                            return false;
                        case "Running":
                        case "Pending":
                            // Still building, wait
                            Thread.sleep(15000); // Check every 15 seconds
                            break;
                        default:
                            log.warn("Unknown build status: {}", status);
                            Thread.sleep(15000);
                    }
                }
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            } catch (Exception e) {
                log.error("Error waiting for build completion", e);
                return false;
            }
        }
        
        log.warn("Build completion timed out for job {}", buildJobName);
        return false;
    }

    /**
     * Record the build job name in the session
     */
    private void recordBuildJob(SelfHealingSession session, String buildJobName) {
        String currentActions = session.getHealingActions() != null ? session.getHealingActions() : "";
        String updatedActions = currentActions + "\nDocker build job: " + buildJobName;
        session.setHealingActions(updatedActions);
        sessionService.updateSession(session);
    }

    /**
     * Mark session as completed
     */
    private void completeSession(SelfHealingSession session, String message) {
        session.setStatus(HealingStatus.COMPLETED);
        session.setCompletedAt(new java.sql.Timestamp(System.currentTimeMillis()));
        
        String currentActions = session.getHealingActions() != null ? session.getHealingActions() : "";
        session.setHealingActions(currentActions + "\n" + message);
        
        sessionService.updateSession(session);
        log.info("Session {} marked as completed: {}", session.getId(), message);
    }

    /**
     * Mark session as failed
     */
    private void failSession(SelfHealingSession session, String errorMessage) {
        session.setStatus(HealingStatus.FAILED);
        session.setErrorMessage(errorMessage);
        session.setCompletedAt(new java.sql.Timestamp(System.currentTimeMillis()));
        sessionService.updateSession(session);
        log.error("Session {} marked as failed: {}", session.getId(), errorMessage);
    }
}
