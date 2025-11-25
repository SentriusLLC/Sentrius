package io.sentrius.agent.services;

import io.sentrius.sso.core.dto.UserDTO;
import io.sentrius.sso.core.dto.agents.AgentExecution;
import io.sentrius.sso.core.dto.feedback.AgentFeedbackDTO;
import io.sentrius.sso.core.dto.feedback.FeedbackSubmissionDTO;
import io.sentrius.sso.core.exceptions.ZtatException;
import io.sentrius.sso.core.feedback.FeedbackType;
import io.sentrius.sso.core.services.agents.AgentClientService;
import io.sentrius.sso.core.services.agents.AgentExecutionService;
import io.sentrius.sso.core.services.agents.ZeroTrustClientService;
import io.sentrius.sso.core.utils.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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

    private final AgentExecution agentExecution;
    @Value("${agent.api.url:http://localhost:8080}")
    private String apiUrl;

    @Value("${agents.analytics.name:analytics-agent}")
    private String agentName;

    private final AgentExecutionService agentExecutionService;
    private final ZeroTrustClientService zeroTrustClientService;
    
    public AgentFeedbackClient(AgentExecutionService agentExecutionService, ZeroTrustClientService zeroTrustClientService) {
        this.agentExecutionService = agentExecutionService;
        this.zeroTrustClientService = zeroTrustClientService;

        UserDTO user = UserDTO.builder()
            .username(agentName)
            .build();

        agentExecution = agentExecutionService.getAgentExecution(user);
    }
    
    /**
     * Submit feedback for this agent.
     */
    public AgentFeedbackDTO submitFeedback(
            String agentId,
            FeedbackType feedbackType,
            String feedbackText,
            String behaviorCategory,
            String context) throws ZtatException {
        
        String url =  "/api/v1/feedback/submit";

        FeedbackSubmissionDTO submission = FeedbackSubmissionDTO.builder()
            .agentId(agentId)
            .feedbackType(feedbackType)
            .feedbackText(feedbackText)
            .behaviorCategory(behaviorCategory)
            .context(context)
            .build();



        var resp = zeroTrustClientService.callPostOnApi(url, submission);

        AgentFeedbackDTO responseBody = JsonUtil.MAPPER.convertValue(resp, AgentFeedbackDTO.class);

        log.info("Feedback submitted: agentId={}, type={}, id={}",
            agentId, feedbackType, responseBody.getId());

        return responseBody;
    }
    
    /**
     * Get feedback statistics for this agent.
     */
    public Map<String, Object> getFeedbackStatistics(String agentId, int days) throws ZtatException {
        String url = "/api/v1/feedback/agent/" + agentId + "/statistics?days=" + days;

        String resp = zeroTrustClientService.callAuthenticatedGetOnApi(apiUrl, url);
        return JsonUtil.MAPPER.convertValue(resp, Map.class);
    }
    
    /**
     * Get feedback score for this agent (0-100).
     */
    public Double getFeedbackScore(String agentId) throws ZtatException {
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
