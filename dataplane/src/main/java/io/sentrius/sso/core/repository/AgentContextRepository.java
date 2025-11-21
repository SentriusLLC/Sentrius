package io.sentrius.sso.core.repository;

import io.sentrius.sso.core.model.agents.AgentContext;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AgentContextRepository extends JpaRepository<AgentContext, UUID> {
    Optional<AgentContext> findByName(String name);
    List<AgentContext> findByParentId(UUID parentId);
    
    /**
     * Find the agent context with the highest generation number for a given name.
     * This is useful when multiple generations exist for the same agent.
     */
    @Query("SELECT ac FROM AgentContext ac WHERE ac.name = :name ORDER BY ac.generation DESC LIMIT 1")
    Optional<AgentContext> findLatestByName(@Param("name") String name);
    
    /**
     * Find the first generation (root) agent context for a given name.
     * This is the original agent before any generations were created.
     */
    @Query("SELECT ac FROM AgentContext ac WHERE ac.name = :name AND ac.parentId IS NULL ORDER BY ac.generation ASC LIMIT 1")
    Optional<AgentContext> findFirstGenerationByName(@Param("name") String name);
}

