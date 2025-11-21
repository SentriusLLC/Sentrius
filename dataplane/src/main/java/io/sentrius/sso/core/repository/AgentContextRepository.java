package io.sentrius.sso.core.repository;

import io.sentrius.sso.core.dto.agents.AgentContextLineageProjection;
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

    @Query("""
    SELECT 
        ac.id AS id,
        ac.name AS name,
        ac.description AS description,
        ac.createdAt AS createdAt,
        ac.updatedAt AS updatedAt,
        ac.generation AS generation,
        ac.parentId AS parentId,
        ac.memoryNamespace AS memoryNamespace,
        ac.trustScore AS trustScore,
        ac.policyId AS policyId
    FROM AgentContext ac
    WHERE ac.name = :name
    ORDER BY ac.generation DESC
""")
    List<AgentContextLineageProjection> findLineageByName(@Param("name") String name);

    @Query("""
    SELECT ac.id as id,
           ac.name as name,
           ac.description as description,
           ac.createdAt as createdAt,
           ac.updatedAt as updatedAt,
           ac.generation as generation,
           ac.parentId as parentId,
           ac.memoryNamespace as memoryNamespace,
           ac.trustScore as trustScore,
           ac.policyId as policyId
    FROM AgentContext ac
    WHERE ac.id = :id
""")
    Optional<AgentContextLineageProjection> findProjectionById(UUID id);

    @Query("""
    SELECT ac.id as id,
           ac.name as name,
           ac.description as description,
           ac.createdAt as createdAt,
           ac.updatedAt as updatedAt,
           ac.generation as generation,
           ac.parentId as parentId,
           ac.memoryNamespace as memoryNamespace,
           ac.trustScore as trustScore,
           ac.policyId as policyId
    FROM AgentContext ac
    WHERE ac.parentId = :parentId
""")
    List<AgentContextLineageProjection> findProjectionByParentId(UUID parentId);

    /**
     * Find the first generation (root) agent context for a given name.
     * This is the original agent before any generations were created.
     */
    @Query("SELECT ac FROM AgentContext ac WHERE ac.name = :name AND ac.parentId IS NULL ORDER BY ac.generation ASC LIMIT 1")
    Optional<AgentContext> findFirstGenerationByName(@Param("name") String name);

    @Query("""
    SELECT ac.id as id,
           ac.name as name,
           ac.description as description,
           ac.createdAt as createdAt,
           ac.updatedAt as updatedAt,
           ac.generation as generation,
           ac.parentId as parentId,
           ac.memoryNamespace as memoryNamespace,
           ac.trustScore as trustScore,
           ac.policyId as policyId
    FROM AgentContext ac
    WHERE ac.name = :name
    ORDER BY ac.generation DESC
    LIMIT 1
""")
    Optional<AgentContextLineageProjection> findLatestProjectionByName(String name);
}

