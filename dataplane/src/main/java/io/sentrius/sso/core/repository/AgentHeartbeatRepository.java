package io.sentrius.sso.core.repository;

import io.sentrius.sso.core.model.AgentHeartbeat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AgentHeartbeatRepository extends JpaRepository<AgentHeartbeat, Long> {
    Optional<AgentHeartbeat> findByAgentId(String agentId);
    
    /**
     * Find all agents with a heartbeat after the specified time
     */
    @Query("SELECT a FROM AgentHeartbeat a WHERE a.lastHeartbeat > :since")
    List<AgentHeartbeat> findByLastHeartbeatAfter(@Param("since") LocalDateTime since);
}