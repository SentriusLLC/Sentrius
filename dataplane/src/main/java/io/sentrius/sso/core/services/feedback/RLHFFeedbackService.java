package io.sentrius.sso.core.services.feedback;

import io.sentrius.sso.core.feedback.FeedbackType;
import io.sentrius.sso.core.model.feedback.AgentFeedback;
import io.sentrius.sso.core.repository.feedback.AgentFeedbackRepository;
import io.sentrius.sso.core.repository.AgentMemoryRepository;
import io.sentrius.sso.core.services.agents.VectorAgentMemoryStore;
import io.sentrius.sso.core.model.agents.AgentMemory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * RLHF (Reinforcement Learning from Human Feedback) Processing Service.
 * Integrates human feedback with agent trust scores and behavior patterns.
 */
@Service
@Slf4j
@ConditionalOnProperty(name = "sentrius.rlhf.enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class RLHFFeedbackService {
    
    private final AgentFeedbackRepository feedbackRepository;
    private final AgentFeedbackService feedbackService;
    private final AgentMemoryRepository agentMemoryRepository;
    private final VectorAgentMemoryStore vectorMemoryStore;
    
    // RLHF configuration constants
    private static final double POSITIVE_FEEDBACK_TRUST_BOOST = 2.0;
    private static final double NEGATIVE_FEEDBACK_TRUST_PENALTY = -5.0;
    private static final double CORRECTIVE_FEEDBACK_TRUST_BOOST = 1.0;
    private static final double NEUTRAL_FEEDBACK_TRUST_IMPACT = 0.0;
    
    private static final int MIN_FEEDBACK_FOR_LEARNING = 3;
    private static final double FEEDBACK_DECAY_DAYS = 30.0;
    
    public RLHFFeedbackService(
            AgentFeedbackRepository feedbackRepository,
            AgentFeedbackService feedbackService,
            AgentMemoryRepository agentMemoryRepository,
            VectorAgentMemoryStore vectorMemoryStore) {
        this.feedbackRepository = feedbackRepository;
        this.feedbackService = feedbackService;
        this.agentMemoryRepository = agentMemoryRepository;
        this.vectorMemoryStore = vectorMemoryStore;
    }
    
    /**
     * Process unprocessed feedback and update agent trust scores.
     * Scheduled to run every 5 minutes.
     */
    @Scheduled(fixedRate = 300000, initialDelay = 30000)
    @Transactional
    public void processPendingFeedback() {
        log.info("Processing pending RLHF feedback");
        
        List<AgentFeedback> unprocessed = feedbackRepository.findByProcessedOrderByTimestampAsc(false);
        
        if (unprocessed.isEmpty()) {
            log.debug("No unprocessed feedback found");
            return;
        }
        
        log.info("Found {} unprocessed feedback items to process", unprocessed.size());
        
        // Group by agent ID
        Map<String, List<AgentFeedback>> feedbackByAgent = unprocessed.stream()
            .collect(Collectors.groupingBy(AgentFeedback::getAgentId));
        
        for (Map.Entry<String, List<AgentFeedback>> entry : feedbackByAgent.entrySet()) {
            String agentId = entry.getKey();
            List<AgentFeedback> agentFeedback = entry.getValue();
            
            try {
                processAgentFeedback(agentId, agentFeedback);
            } catch (Exception e) {
                log.error("Error processing feedback for agent {}: {}", agentId, e.getMessage(), e);
            }
        }
    }
    
    /**
     * Process feedback for a specific agent.
     */
    @Transactional
    public void processAgentFeedback(String agentId, List<AgentFeedback> feedbackList) {
        log.debug("Processing {} feedback items for agent {}", feedbackList.size(), agentId);
        
        for (AgentFeedback feedback : feedbackList) {
            int trustImpact = calculateTrustImpact(feedback);
            
            // Store feedback as agent memory for learning
            storeFeedbackAsMemory(feedback);
            
            // Mark as processed
            feedbackService.markAsProcessed(feedback.getId(), trustImpact);
        }
        
        // Check if we have enough feedback to generate learned behaviors
        if (shouldGenerateBehaviorPatterns(agentId)) {
            generateBehaviorPatterns(agentId);
        }
        
        log.info("Completed processing feedback for agent {}", agentId);
    }
    
    /**
     * Calculate the trust score impact from a piece of feedback.
     */
    public int calculateTrustImpact(AgentFeedback feedback) {
        double baseImpact = switch (feedback.getFeedbackType()) {
            case POSITIVE -> POSITIVE_FEEDBACK_TRUST_BOOST;
            case NEGATIVE -> NEGATIVE_FEEDBACK_TRUST_PENALTY;
            case CORRECTIVE -> CORRECTIVE_FEEDBACK_TRUST_BOOST;
            case NEUTRAL -> NEUTRAL_FEEDBACK_TRUST_IMPACT;
        };
        
        // Apply time decay - recent feedback has more impact
        double daysSinceFeedback = java.time.Duration.between(
            feedback.getTimestamp(), LocalDateTime.now()
        ).toDays();
        double decayFactor = Math.exp(-daysSinceFeedback / FEEDBACK_DECAY_DAYS);
        
        return (int) Math.round(baseImpact * decayFactor);
    }
    
    /**
     * Calculate aggregated feedback impact for trust score calculation.
     */
    public double calculateFeedbackScore(String agentId) {
        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
        
        // Get recent feedback
        List<AgentFeedback> recentFeedback = feedbackRepository
            .findByAgentIdAndTimestampBetweenOrderByTimestampDesc(
                agentId, thirtyDaysAgo, LocalDateTime.now()
            );
        
        if (recentFeedback.isEmpty()) {
            return 50.0; // Neutral score for no feedback
        }
        
        // Calculate weighted average
        double totalWeight = 0.0;
        double weightedSum = 0.0;
        
        for (AgentFeedback feedback : recentFeedback) {
            double weight = feedback.getReinforcementWeight();
            double daysSince = java.time.Duration.between(
                feedback.getTimestamp(), LocalDateTime.now()
            ).toDays();
            double decayFactor = Math.exp(-daysSince / FEEDBACK_DECAY_DAYS);
            
            totalWeight += Math.abs(weight) * decayFactor;
            weightedSum += weight * decayFactor * 50.0; // Scale to 0-100
        }
        
        if (totalWeight == 0.0) {
            return 50.0;
        }
        
        // Normalize to 0-100 range
        double score = 50.0 + (weightedSum / totalWeight);
        return Math.max(0.0, Math.min(100.0, score));
    }
    
    /**
     * Store feedback as semantic memory for agent learning.
     */
    @Transactional
    protected void storeFeedbackAsMemory(AgentFeedback feedback) {
        String memoryKey = "feedback/" + feedback.getFeedbackType().name().toLowerCase() + 
                          "/" + UUID.randomUUID();
        
        String memoryValue = buildFeedbackMemoryValue(feedback);
        
        String[] markings = {
            "FEEDBACK",
            "RLHF",
            feedback.getFeedbackType().name(),
            feedback.getBehaviorCategory() != null ? feedback.getBehaviorCategory() : "GENERAL"
        };
        
        AgentMemory memory = vectorMemoryStore.storeMemoryWithEmbedding(
            feedback.getAgentId(),
            memoryKey,
            memoryValue,
            "PRIVATE",
            markings,
            feedback.getProvidedBy()
        );
        
        log.debug("Stored feedback {} as memory {} for agent {}", 
            feedback.getId(), memory.getId(), feedback.getAgentId());
    }
    
    /**
     * Build memory value from feedback.
     */
    private String buildFeedbackMemoryValue(AgentFeedback feedback) {
        Map<String, Object> memoryData = new HashMap<>();
        memoryData.put("type", "human_feedback");
        memoryData.put("feedback_type", feedback.getFeedbackType().name());
        memoryData.put("feedback_text", feedback.getFeedbackText());
        memoryData.put("context", feedback.getContext());
        memoryData.put("action_id", feedback.getActionId());
        memoryData.put("behavior_category", feedback.getBehaviorCategory());
        memoryData.put("reinforcement_weight", feedback.getReinforcementWeight());
        memoryData.put("timestamp", feedback.getTimestamp().toString());
        
        // Convert to JSON-like string
        return memoryData.entrySet().stream()
            .map(e -> "\"" + e.getKey() + "\":\"" + e.getValue() + "\"")
            .collect(Collectors.joining(",", "{", "}"));
    }
    
    /**
     * Check if agent has enough feedback to generate behavior patterns.
     */
    private boolean shouldGenerateBehaviorPatterns(String agentId) {
        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
        List<AgentFeedback> recentFeedback = feedbackRepository
            .findByAgentIdAndTimestampBetweenOrderByTimestampDesc(
                agentId, thirtyDaysAgo, LocalDateTime.now()
            );
        
        return recentFeedback.size() >= MIN_FEEDBACK_FOR_LEARNING;
    }
    
    /**
     * Generate learned behavior patterns from feedback.
     */
    @Transactional
    protected void generateBehaviorPatterns(String agentId) {
        log.info("Generating behavior patterns for agent {}", agentId);
        
        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
        List<AgentFeedback> recentFeedback = feedbackRepository
            .findByAgentIdAndTimestampBetweenOrderByTimestampDesc(
                agentId, thirtyDaysAgo, LocalDateTime.now()
            );
        
        // Group by behavior category
        Map<String, List<AgentFeedback>> feedbackByCategory = recentFeedback.stream()
            .filter(f -> f.getBehaviorCategory() != null)
            .collect(Collectors.groupingBy(AgentFeedback::getBehaviorCategory));
        
        for (Map.Entry<String, List<AgentFeedback>> entry : feedbackByCategory.entrySet()) {
            String category = entry.getKey();
            List<AgentFeedback> categoryFeedback = entry.getValue();
            
            if (categoryFeedback.size() >= MIN_FEEDBACK_FOR_LEARNING) {
                createBehaviorPattern(agentId, category, categoryFeedback);
            }
        }
    }
    
    /**
     * Create a learned behavior pattern from feedback.
     */
    private void createBehaviorPattern(String agentId, String category, List<AgentFeedback> feedback) {
        // Calculate category sentiment
        double avgWeight = feedback.stream()
            .mapToDouble(AgentFeedback::getReinforcementWeight)
            .average()
            .orElse(0.0);
        
        String sentiment = avgWeight > 0.3 ? "REINFORCE" : 
                          avgWeight < -0.3 ? "DISCOURAGE" : "NEUTRAL";
        
        String memoryKey = "behavior_pattern/" + category + "/" + UUID.randomUUID();
        String memoryValue = String.format(
            "{\"category\":\"%s\",\"sentiment\":\"%s\",\"avg_weight\":%.2f," +
            "\"feedback_count\":%d,\"learned_at\":\"%s\"}",
            category, sentiment, avgWeight, feedback.size(), LocalDateTime.now()
        );
        
        AgentMemory pattern = vectorMemoryStore.storeMemoryWithEmbedding(
            agentId,
            memoryKey,
            memoryValue,
            "PRIVATE",
            new String[]{"BEHAVIOR_PATTERN", "LEARNED", category, sentiment},
            "system"
        );
        
        log.info("Created behavior pattern {} for agent {}: category={}, sentiment={}", 
            pattern.getId(), agentId, category, sentiment);
    }
    
    /**
     * Get aggregated feedback statistics for an agent.
     */
    public Map<String, Object> getFeedbackStatistics(String agentId, LocalDateTime since) {
        Map<String, Object> stats = new HashMap<>();
        
        long positiveCount = feedbackRepository.countByAgentIdAndTypeAndSince(
            agentId, FeedbackType.POSITIVE, since);
        long negativeCount = feedbackRepository.countByAgentIdAndTypeAndSince(
            agentId, FeedbackType.NEGATIVE, since);
        long correctiveCount = feedbackRepository.countByAgentIdAndTypeAndSince(
            agentId, FeedbackType.CORRECTIVE, since);
        long neutralCount = feedbackRepository.countByAgentIdAndTypeAndSince(
            agentId, FeedbackType.NEUTRAL, since);
        
        stats.put("positive_count", positiveCount);
        stats.put("negative_count", negativeCount);
        stats.put("corrective_count", correctiveCount);
        stats.put("neutral_count", neutralCount);
        stats.put("total_count", positiveCount + negativeCount + correctiveCount + neutralCount);
        
        Double avgWeight = feedbackRepository.getAverageReinforcementWeight(agentId, since);
        stats.put("average_reinforcement_weight", avgWeight != null ? avgWeight : 0.0);
        
        stats.put("feedback_score", calculateFeedbackScore(agentId));
        
        return stats;
    }
}
