package io.sentrius.agent.analysis.agents.feedback;

import io.sentrius.agent.services.AgentFeedbackClient;
import io.sentrius.sso.core.feedback.FeedbackType;
import io.sentrius.sso.core.model.feedback.AgentFeedback;
import io.sentrius.sso.core.repository.feedback.AgentFeedbackRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Example service showing how analytics and monitoring agents actively change
 * based on RLHF feedback using LLM-interpreted guidance.
 * 
 * This demonstrates:
 * 1. Querying feedback for an agent
 * 2. Analyzing feedback patterns to generate behavioral guidance
 * 3. Adjusting agent parameters based on learned patterns
 * 
 * In production, this would integrate with LLMService to interpret feedback.
 * This simplified version analyzes feedback directly.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "sentrius.rlhf.feedback.learning.enabled", havingValue = "true", matchIfMissing = false)
public class FeedbackLearningService {
    
    private final AgentFeedbackRepository feedbackRepository;
    private final AgentFeedbackClient feedbackClient;
    
    // Agent behavior parameters that can be adjusted
    private volatile int alertThreshold = 5; // Number of errors before alerting
    private volatile int checkIntervalSeconds = 60; // How often to check
    private volatile double sensitivityLevel = 0.5; // Detection sensitivity
    
    @Autowired
    public FeedbackLearningService(
            @Autowired(required = false) AgentFeedbackRepository feedbackRepository,
            @Autowired(required = false) AgentFeedbackClient feedbackClient) {
        this.feedbackRepository = feedbackRepository;
        this.feedbackClient = feedbackClient;
    }
    
    /**
     * Periodically learn from feedback and adjust behavior.
     * Runs every 10 minutes.
     */
    @Scheduled(fixedRate = 600000, initialDelay = 120000)
    public void learnFromFeedback() {
        if (feedbackRepository == null) {
            log.debug("Feedback learning disabled - repository not available");
            return;
        }
        
        String agentId = getAgentId();
        log.info("Learning from feedback for agent: {}", agentId);
        
        try {
            // Step 1: Get recent feedback
            LocalDateTime since = LocalDateTime.now().minusDays(7);
            var feedback = feedbackRepository.findByAgentIdAndTimestampBetweenOrderByTimestampDesc(
                agentId, since, LocalDateTime.now()
            );
            
            if (feedback.isEmpty()) {
                log.debug("No recent feedback to learn from");
                return;
            }
            
            log.info("Found {} feedback items to learn from", feedback.size());
            
            // Step 2: Analyze feedback patterns and generate guidance
            String behaviorGuidance = analyzeFeedbackPatterns(feedback);
            
            // Step 3: Adjust behavior based on guidance
            adjustBehaviorBasedOnGuidance(behaviorGuidance);
            
            log.info("Successfully learned from feedback and adjusted behavior");
            
        } catch (Exception e) {
            log.error("Error learning from feedback: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Analyze feedback patterns and generate behavioral guidance.
     * In production, this would use LLMService with askQuestion() to interpret feedback.
     * This simplified version analyzes patterns directly.
     */
    private String analyzeFeedbackPatterns(List<AgentFeedback> feedbackList) {
        // Count feedback by type
        long negativeCount = feedbackList.stream()
            .filter(f -> "NEGATIVE".equals(f.getFeedbackType()))
            .count();
        long positiveCount = feedbackList.stream()
            .filter(f -> "POSITIVE".equals(f.getFeedbackType()))
            .count();
        long correctiveCount = feedbackList.stream()
            .filter(f -> "CORRECTIVE".equals(f.getFeedbackType()))
            .count();
        
        // Analyze feedback text for common patterns
        String allFeedbackText = feedbackList.stream()
            .map(AgentFeedback::getFeedbackText)
            .collect(Collectors.joining(" "))
            .toLowerCase();
        
        StringBuilder guidance = new StringBuilder("Behavioral guidance based on feedback analysis:\n");
        
        // Check for "noisy" or "too many alerts" patterns
        if (allFeedbackText.contains("noisy") || allFeedbackText.contains("too many") || negativeCount > positiveCount) {
            guidance.append("increase alert_threshold ");
            guidance.append("decrease sensitivity ");
        }
        
        // Check for "missed" or "didn't catch" patterns
        if (allFeedbackText.contains("missed") || allFeedbackText.contains("didn't catch")) {
            guidance.append("decrease alert_threshold ");
            guidance.append("increase sensitivity ");
        }
        
        // Check for timing issues
        if (allFeedbackText.contains("slow") || allFeedbackText.contains("delayed")) {
            guidance.append("decrease check_interval ");
        }
        
        log.info("Analyzed {} feedback items: {} positive, {} negative, {} corrective", 
            feedbackList.size(), positiveCount, negativeCount, correctiveCount);
        
        return guidance.toString();
    }
    
    /**
     * Adjust agent behavior based on LLM guidance.
     * This is where the agent actively changes!
     */
    private void adjustBehaviorBasedOnGuidance(String guidance) {
        log.info("Adjusting agent behavior based on LLM guidance");
        
        try {
            if (guidance.contains("increase") && guidance.contains("alert_threshold")) {
                alertThreshold = Math.min(alertThreshold + 2, 20);
                log.info("BEHAVIOR CHANGE: Increased alert threshold to {}", alertThreshold);
            } else if (guidance.contains("decrease") && guidance.contains("alert_threshold")) {
                alertThreshold = Math.max(alertThreshold - 2, 1);
                log.info("BEHAVIOR CHANGE: Decreased alert threshold to {}", alertThreshold);
            }
            
            if (guidance.contains("increase") && guidance.contains("sensitivity")) {
                sensitivityLevel = Math.min(sensitivityLevel + 0.1, 1.0);
                log.info("BEHAVIOR CHANGE: Increased sensitivity to {}", sensitivityLevel);
            } else if (guidance.contains("decrease") && guidance.contains("sensitivity")) {
                sensitivityLevel = Math.max(sensitivityLevel - 0.1, 0.1);
                log.info("BEHAVIOR CHANGE: Decreased sensitivity to {}", sensitivityLevel);
            }
            
            log.info("Current behavior: alertThreshold={}, sensitivity={}", alertThreshold, sensitivityLevel);
            
        } catch (Exception e) {
            log.error("Failed to adjust behavior: {}", e.getMessage(), e);
        }
    }
    
    public boolean shouldTriggerAlert(int errorCount) {
        return errorCount >= alertThreshold;
    }
    
    private String getAgentId() {
        return System.getProperty("agent.id", "analytics-agent-001");
    }
}
