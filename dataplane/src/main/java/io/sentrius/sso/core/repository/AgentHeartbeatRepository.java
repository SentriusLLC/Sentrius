package io.sentrius.sso.core.repository;

import io.sentrius.sso.core.model.AgentHeartbeat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface AgentHeartbeatRepository extends JpaRepository<AgentHeartbeat, Long> {
    Optional<AgentHeartbeat> findByAgentId(String agentId);
}