package io.sentrius.sso.core.repository;

import io.sentrius.sso.core.model.agents.AgentLaunch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;

public interface AgentLaunchRepository extends JpaRepository<AgentLaunch, UUID> {
    /**
     * Find the most recent launch record for a given agent ID.
     */
    @Query("SELECT al FROM AgentLaunch al WHERE al.agentId = :agentId ORDER BY al.createdAt DESC LIMIT 1")
    Optional<AgentLaunch> findLatestByAgentId(@Param("agentId") String agentId);
}
