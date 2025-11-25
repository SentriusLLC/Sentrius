package io.sentrius.agent.services;

import io.sentrius.sso.core.dto.feedback.AgentFeedbackDTO;
import io.sentrius.sso.core.dto.feedback.FeedbackSubmissionDTO;
import io.sentrius.sso.core.feedback.FeedbackType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * Service for Java agents to submit and retrieve feedback.
 * Integrates with the RLHF feedback system.
 */
@Slf4j
@Service
public class AgentFeedbackClient {
    
    private final RestTemplate restTemplate;
    private final String apiBaseUrl;
    private final TokenProvider tokenProvider;
    
    public AgentFeedbackClient(RestTemplate restTemplate, String apiBaseUrl, TokenProvider tokenProvider) {
        this.restTemplate = restTemplate;
        this.apiBaseUrl = apiBaseUrl;
        this.tokenProvider = tokenProvider;
    }
    
    /**
     * Submit feedback for this agent.
     */
    public AgentFeedbackDTO submitFeedback(
            String agentId,
            FeedbackType feedbackType,
            String feedbackText,
            String behaviorCategory,
            String context) {
        
        String url = apiBaseUrl + "/api/v1/feedback/submit";
        
        FeedbackSubmissionDTO submission = FeedbackSubmissionDTO.builder()
            .agentId(agentId)
            .feedbackType(feedbackType)
            .feedbackText(feedbackText)
            .behaviorCategory(behaviorCategory)
            .context(context)
            .build();
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(tokenProvider.getToken());
        
        HttpEntity<FeedbackSubmissionDTO> request = new HttpEntity<>(submission, headers);
        
        try {
            ResponseEntity<AgentFeedbackDTO> response = restTemplate.exchange(
                url, HttpMethod.POST, request, AgentFeedbackDTO.class
            );
            
            log.info("Feedback submitted: agentId={}, type={}, id={}", 
                agentId, feedbackType, response.getBody().getId());
            
            return response.getBody();
        } catch (Exception e) {
            log.error("Failed to submit feedback: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to submit feedback", e);
        }
    }
    
    /**
     * Get feedback statistics for this agent.
     */
    public Map<String, Object> getFeedbackStatistics(String agentId, int days) {
        String url = apiBaseUrl + "/api/v1/feedback/agent/" + agentId + "/statistics?days=" + days;
        
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokenProvider.getToken());
        
        HttpEntity<Void> request = new HttpEntity<>(headers);
        
        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                url, HttpMethod.GET, request, Map.class
            );
            
            return (Map<String, Object>) response.getBody();
        } catch (Exception e) {
            log.error("Failed to get feedback statistics: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to get feedback statistics", e);
        }
    }
    
    /**
     * Get feedback score for this agent (0-100).
     */
    public Double getFeedbackScore(String agentId) {
        Map<String, Object> stats = getFeedbackStatistics(agentId, 30);
        Object scoreObj = stats.get("feedback_score");
        
        if (scoreObj instanceof Number) {
            return ((Number) scoreObj).doubleValue();
        }
        
        return 50.0; // Default neutral score
    }
    
    /**
     * Interface for providing authentication tokens.
     */
    public interface TokenProvider {
        String getToken();
    }
}
