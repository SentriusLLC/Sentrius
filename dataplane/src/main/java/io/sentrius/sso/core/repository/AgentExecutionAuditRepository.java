package io.sentrius.sso.core.repository;

import io.sentrius.sso.core.model.agents.AgentExecutionAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AgentExecutionAuditRepository extends JpaRepository<AgentExecutionAudit, UUID> {
    
    /**
     * Find all agent execution audits ordered by start time descending (most recent first).
     */
    @Query("SELECT aea FROM AgentExecutionAudit aea ORDER BY aea.startTime DESC")
    List<AgentExecutionAudit> findAllOrderByStartTimeDesc();

    /**
     * Find agent execution audit by execution ID.
     */
    Optional<AgentExecutionAudit> findByExecutionId(String executionId);

    /**
     * Find all agent execution audits by agent ID.
     */
    List<AgentExecutionAudit> findByAgentIdOrderByStartTimeDesc(String agentId);

    /**
     * Find all agent execution audits by status.
     */
    List<AgentExecutionAudit> findByStatusOrderByStartTimeDesc(String status);

    /**
     * Find all agent execution audits by agent type.
     */
    List<AgentExecutionAudit> findByAgentTypeOrderByStartTimeDesc(String agentType);

    /**
     * Find all agent execution audits executed by a specific user.
     */
    List<AgentExecutionAudit> findByExecutedByOrderByStartTimeDesc(String executedBy);

    /**
     * Find all agent execution audits within a time range.
     */
    @Query("SELECT aea FROM AgentExecutionAudit aea WHERE aea.startTime >= :startTime AND aea.startTime <= :endTime ORDER BY aea.startTime DESC")
    List<AgentExecutionAudit> findByStartTimeBetween(@Param("startTime") Instant startTime, @Param("endTime") Instant endTime);

    /**
     * Find completed agent executions without summaries (summary is null or empty).
     * This is used by the AgentExecutionSummarizationAgent to process executions.
     */
    @Query("SELECT aea.executionId FROM AgentExecutionAudit aea WHERE " +
           "aea.status IN ('COMPLETED', 'FAILED', 'ERROR') AND " +
           "(aea.summary IS NULL OR aea.summary = '') AND " +
           "aea.endTime IS NOT NULL " +
           "ORDER BY aea.endTime DESC")
    List<String> findCompletedExecutionsWithoutSummaries();
}
