package io.sentrius.sso.core.services.selfhealing;

import io.sentrius.sso.core.dto.AgentRegistrationDTO;
import io.sentrius.sso.core.model.selfhealing.SelfHealingSession;
import io.sentrius.sso.core.model.selfhealing.SelfHealingSession.HealingStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Service for launching coding agents to perform self-healing repairs
 */
@Slf4j
@Service
public class CodingAgentLauncherService {

    @Autowired
    private SelfHealingSessionService sessionService;

    @Value("${self-healing.agent-launcher.url:http://localhost:8080}")
    private String agentLauncherUrl;

    @Value("${self-healing.coding-agent.client-id:coding-agents}")
    private String codingAgentClientId;

    @Value("${self-healing.coding-agent.client-secret:}")
    private String codingAgentClientSecret;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Launch a coding agent pod to analyze and fix the error
     * 
     * @param session The healing session to work on
     * @return The agent ID if successful, null otherwise
     */
    public String launchCodingAgent(SelfHealingSession session) {
        try {
            String agentName = generateAgentName(session);
            
            log.info("Launching coding agent {} for healing session {}", agentName, session.getId());
            
            // Build agent registration DTO
            AgentRegistrationDTO agentDto = AgentRegistrationDTO.builder()
                    .agentName(agentName)
                    .agentType("self-healing-coder")
                    .clientId(codingAgentClientId)
                    .clientSecret(codingAgentClientSecret)
                    .agentContextId(String.valueOf(session.getId()))
                    .build();

            // Call agent launcher API to create the pod
            String launchUrl = agentLauncherUrl + "/api/v1/agent/launcher/create";
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            // Note: In production, this should use proper authentication
            headers.set("Authorization", "Bearer " + getSystemAuthToken());
            
            HttpEntity<AgentRegistrationDTO> request = new HttpEntity<>(agentDto, headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(
                    launchUrl, 
                    HttpMethod.POST, 
                    request, 
                    Map.class
            );
            
            if (response.getStatusCode() == HttpStatus.OK) {
                // Update session with agent ID
                session.setAgentId(agentName);
                session.setStatus(HealingStatus.FIXING);
                sessionService.updateSession(session);
                
                log.info("Successfully launched coding agent {} for session {}", agentName, session.getId());
                return agentName;
            } else {
                log.error("Failed to launch coding agent, status: {}", response.getStatusCode());
                updateSessionWithError(session, "Failed to launch coding agent: " + response.getStatusCode());
                return null;
            }
            
        } catch (Exception e) {
            log.error("Error launching coding agent for session {}", session.getId(), e);
            updateSessionWithError(session, "Error launching coding agent: " + e.getMessage());
            return null;
        }
    }

    /**
     * Monitor the progress of a coding agent
     * 
     * @param session The healing session
     * @return true if agent is still running, false if completed or failed
     */
    public boolean monitorAgentProgress(SelfHealingSession session) {
        try {
            if (session.getAgentId() == null) {
                return false;
            }

            String statusUrl = agentLauncherUrl + "/api/v1/agent/launcher/status?agentId=" + session.getAgentId();
            
            ResponseEntity<Map> response = restTemplate.getForEntity(statusUrl, Map.class);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                String status = (String) response.getBody().get("status");
                
                log.debug("Coding agent {} status: {}", session.getAgentId(), status);
                
                switch (status) {
                    case "Running":
                        return true;
                    case "Succeeded":
                        session.setStatus(HealingStatus.COMPLETED);
                        sessionService.updateSession(session);
                        return false;
                    case "Failed":
                        updateSessionWithError(session, "Coding agent pod failed");
                        return false;
                    default:
                        return true; // Still pending/unknown
                }
            }
            
            return false;
            
        } catch (Exception e) {
            log.error("Error monitoring agent progress for session {}", session.getId(), e);
            return false;
        }
    }

    /**
     * Terminate a coding agent
     * 
     * @param session The healing session
     */
    public void terminateAgent(SelfHealingSession session) {
        try {
            if (session.getAgentId() == null) {
                return;
            }

            String killUrl = agentLauncherUrl + "/api/v1/agent/launcher/kill?agentId=" + session.getAgentId();
            
            restTemplate.getForEntity(killUrl, String.class);
            
            log.info("Terminated coding agent {} for session {}", session.getAgentId(), session.getId());
            
        } catch (Exception e) {
            log.error("Error terminating agent for session {}", session.getId(), e);
        }
    }

    /**
     * Generate a unique agent name for the healing session
     */
    private String generateAgentName(SelfHealingSession session) {
        String podName = session.getPodName() != null ? session.getPodName() : "unknown";
        String sanitizedPodName = podName.replaceAll("[^a-z0-9-]", "-").toLowerCase();
        return String.format("healing-%s-%s", sanitizedPodName, 
                UUID.randomUUID().toString().substring(0, 8));
    }

    /**
     * Update session with error information
     */
    private void updateSessionWithError(SelfHealingSession session, String errorMessage) {
        session.setStatus(HealingStatus.FAILED);
        session.setErrorMessage(errorMessage);
        session.setCompletedAt(new java.sql.Timestamp(System.currentTimeMillis()));
        sessionService.updateSession(session);
    }

    /**
     * Get system authentication token
     * In production, this should retrieve a proper service account token
     */
    private String getSystemAuthToken() {
        // TODO: Implement proper service account token retrieval
        // For now, return empty string - the agent launcher will need to handle this
        return "";
    }
}
