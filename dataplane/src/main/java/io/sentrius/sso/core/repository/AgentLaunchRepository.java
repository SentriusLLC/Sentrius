package io.sentrius.sso.core.repository;

import io.sentrius.sso.core.model.agents.AgentLaunch;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface AgentLaunchRepository extends JpaRepository<AgentLaunch, UUID> {
}
