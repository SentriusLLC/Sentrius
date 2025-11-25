package io.sentrius.sso.core.repository.feedback;

import io.sentrius.sso.core.feedback.FeedbackType;
import io.sentrius.sso.core.model.feedback.AgentFeedback;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AgentFeedbackRepository extends JpaRepository<AgentFeedback, Long> {
    
    List<AgentFeedback> findByAgentIdOrderByTimestampDesc(String agentId);
    
    Page<AgentFeedback> findByAgentIdOrderByTimestampDesc(String agentId, Pageable pageable);
    
    List<AgentFeedback> findByAgentIdAndTimestampBetweenOrderByTimestampDesc(
        String agentId, LocalDateTime start, LocalDateTime end);
    
    List<AgentFeedback> findByAgentIdAndFeedbackTypeOrderByTimestampDesc(
        String agentId, FeedbackType feedbackType);
    
    List<AgentFeedback> findByProcessedOrderByTimestampAsc(Boolean processed);
    
    @Query("SELECT f FROM AgentFeedback f WHERE f.timestamp >= :since ORDER BY f.timestamp DESC")
    List<AgentFeedback> findRecentFeedback(@Param("since") LocalDateTime since);
    
    @Query("SELECT DISTINCT f.agentId FROM AgentFeedback f")
    List<String> findDistinctAgentIds();
    
    @Query("SELECT AVG(f.reinforcementWeight) FROM AgentFeedback f " +
           "WHERE f.agentId = :agentId AND f.timestamp >= :since AND f.processed = true")
    Double getAverageReinforcementWeight(@Param("agentId") String agentId, @Param("since") LocalDateTime since);
    
    @Query("SELECT COUNT(f) FROM AgentFeedback f " +
           "WHERE f.agentId = :agentId AND f.feedbackType = :type AND f.timestamp >= :since")
    Long countByAgentIdAndTypeAndSince(
        @Param("agentId") String agentId, 
        @Param("type") FeedbackType type, 
        @Param("since") LocalDateTime since);
    
    @Query("SELECT f FROM AgentFeedback f " +
           "WHERE f.agentId = :agentId AND f.behaviorCategory = :category " +
           "ORDER BY f.timestamp DESC")
    List<AgentFeedback> findByAgentIdAndBehaviorCategory(
        @Param("agentId") String agentId, 
        @Param("category") String category);
}
