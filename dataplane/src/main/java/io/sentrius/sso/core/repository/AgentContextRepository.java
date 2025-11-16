package io.sentrius.sso.core.repository;

import io.sentrius.sso.core.model.agents.AgentContext;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface AgentContextRepository extends JpaRepository<AgentContext, UUID> {
    Optional<AgentContext> findByName(String name);
}

