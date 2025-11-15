package io.sentrius.sso.core.repository.trust;

import io.sentrius.sso.core.model.trust.AgentTrustScoreHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AgentTrustScoreHistoryRepository extends JpaRepository<AgentTrustScoreHistory, Long> {
    
    List<AgentTrustScoreHistory> findByAgentIdOrderByTimestampDesc(String agentId);
    
    Page<AgentTrustScoreHistory> findByAgentIdOrderByTimestampDesc(String agentId, Pageable pageable);
    
    List<AgentTrustScoreHistory> findByAgentIdAndTimestampBetweenOrderByTimestampDesc(
        String agentId, LocalDateTime start, LocalDateTime end);
    
    Optional<AgentTrustScoreHistory> findTopByAgentIdOrderByTimestampDesc(String agentId);
    
    @Query("SELECT h FROM AgentTrustScoreHistory h WHERE h.timestamp >= :since ORDER BY h.timestamp DESC")
    List<AgentTrustScoreHistory> findRecentScores(@Param("since") LocalDateTime since);
    
    @Query("SELECT DISTINCT h.agentId FROM AgentTrustScoreHistory h")
    List<String> findDistinctAgentIds();
    
    @Query("SELECT AVG(h.trustScore) FROM AgentTrustScoreHistory h WHERE h.agentId = :agentId AND h.timestamp >= :since")
    Double getAverageTrustScore(@Param("agentId") String agentId, @Param("since") LocalDateTime since);
}
