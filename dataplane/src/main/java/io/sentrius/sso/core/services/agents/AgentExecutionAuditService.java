package io.sentrius.sso.core.services.agents;

import io.sentrius.sso.core.model.agents.AgentExecutionAudit;
import io.sentrius.sso.core.repository.AgentExecutionAuditRepository;
import io.sentrius.sso.core.services.documents.KnowledgeGraphIngestionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing agent execution audits.
 */
@Service
public class AgentExecutionAuditService {

    private static final Logger logger = LoggerFactory.getLogger(AgentExecutionAuditService.class);

    private final AgentExecutionAuditRepository repository;

    @Autowired(required = false)
    private KnowledgeGraphIngestionService knowledgeGraphIngestionService;

    public AgentExecutionAuditService(AgentExecutionAuditRepository repository) {
        this.repository = repository;
    }

    /**
     * Create a new agent execution audit record.
     */
    @Transactional
    public AgentExecutionAudit createAudit(String agentId, String executionId, String agentType, String executedBy) {
        AgentExecutionAudit audit = new AgentExecutionAudit();
        audit.setAgentId(agentId);
        audit.setExecutionId(executionId);
        audit.setAgentType(agentType);
        audit.setExecutedBy(executedBy);
        audit.setStatus("RUNNING");
        audit.setStartTime(Instant.now());
        
        AgentExecutionAudit saved = repository.save(audit);
        logger.info("Created agent execution audit: {} for agent: {}", saved.getId(), agentId);
        return saved;
    }

    /**
     * Update an existing agent execution audit with completion details.
     */
    @Transactional
    public AgentExecutionAudit updateAuditCompletion(String executionId, String status, String summary, 
                                                      String resourceLinks, Integer exitCode) {
        Optional<AgentExecutionAudit> auditOpt = repository.findByExecutionId(executionId);
        if (auditOpt.isEmpty()) {
            logger.warn("Agent execution audit not found for executionId: {}", executionId);
            return null;
        }

        AgentExecutionAudit audit = auditOpt.get();
        audit.setStatus(status);
        audit.setSummary(summary);
        audit.setResourceLinks(resourceLinks);
        audit.setExitCode(exitCode);
        audit.setEndTime(Instant.now());
        audit.calculateDuration();

        AgentExecutionAudit updated = repository.save(audit);
        logger.info("Updated agent execution audit: {} with status: {}", updated.getId(), status);
        return updated;
    }

    /**
     * Update pod logs for an agent execution audit.
     */
    @Transactional
    public void updatePodLogs(String executionId, String podLogs) {
        Optional<AgentExecutionAudit> auditOpt = repository.findByExecutionId(executionId);
        if (auditOpt.isPresent()) {
            AgentExecutionAudit audit = auditOpt.get();
            audit.setPodLogs(podLogs);
            repository.save(audit);
            logger.debug("Updated pod logs for execution: {}", executionId);
        }
    }

    /**
     * Get all agent execution audits.
     */
    @Transactional(readOnly = true)
    public List<AgentExecutionAudit> getAllAudits() {
        return repository.findAllOrderByStartTimeDesc();
    }

    /**
     * Get agent execution audit by ID.
     */
    @Transactional(readOnly = true)
    public Optional<AgentExecutionAudit> getAuditById(UUID id) {
        return repository.findById(id);
    }

    /**
     * Get agent execution audit by execution ID.
     */
    @Transactional(readOnly = true)
    public Optional<AgentExecutionAudit> getAuditByExecutionId(String executionId) {
        return repository.findByExecutionId(executionId);
    }

    /**
     * Get all audits for a specific agent.
     */
    @Transactional(readOnly = true)
    public List<AgentExecutionAudit> getAuditsByAgentId(String agentId) {
        return repository.findByAgentIdOrderByStartTimeDesc(agentId);
    }

    /**
     * Get all audits with a specific status.
     */
    @Transactional(readOnly = true)
    public List<AgentExecutionAudit> getAuditsByStatus(String status) {
        return repository.findByStatusOrderByStartTimeDesc(status);
    }

    /**
     * Get all audits for a specific agent type.
     */
    @Transactional(readOnly = true)
    public List<AgentExecutionAudit> getAuditsByAgentType(String agentType) {
        return repository.findByAgentTypeOrderByStartTimeDesc(agentType);
    }

    /**
     * Get all audits executed by a specific user.
     */
    @Transactional(readOnly = true)
    public List<AgentExecutionAudit> getAuditsByExecutedBy(String username) {
        return repository.findByExecutedByOrderByStartTimeDesc(username);
    }

    /**
     * Get audits within a time range.
     */
    @Transactional(readOnly = true)
    public List<AgentExecutionAudit> getAuditsByTimeRange(Instant startTime, Instant endTime) {
        return repository.findByStartTimeBetween(startTime, endTime);
    }

    /**
     * Get execution IDs for completed executions without summaries.
     * Used by AgentExecutionSummarizationAgent to process executions.
     */
    @Transactional(readOnly = true)
    public List<String> getExecutionsWithoutSummaries() {
        return repository.findCompletedExecutionsWithoutSummaries();
    }

    /**
     * Close an agent execution audit by setting endTime and status.
     * This marks the execution as completed without requiring a summary.
     * The summary will be generated later by AgentExecutionSummarizationAgent.
     */
    @Transactional
    public AgentExecutionAudit closeAudit(String executionId, String finalStatus) {
        Optional<AgentExecutionAudit> auditOpt = repository.findByExecutionId(executionId);
        if (auditOpt.isEmpty()) {
            logger.warn("Agent execution audit not found for executionId: {}", executionId);
            return null;
        }

        AgentExecutionAudit audit = auditOpt.get();

        // Only update if not already closed
        if (audit.getEndTime() == null) {
            audit.setEndTime(Instant.now());
            audit.setStatus(finalStatus != null ? finalStatus : "COMPLETED");
            audit.calculateDuration();

            AgentExecutionAudit updated = repository.save(audit);
            logger.info("Closed agent execution audit: {} with status: {}, duration: {}ms",
                updated.getId(), updated.getStatus(), updated.getDurationMs());

            // Ingest agent execution into knowledge graph (async, non-blocking)
            try {
                if (knowledgeGraphIngestionService != null && knowledgeGraphIngestionService.isAgentIngestionEnabled()) {
                    knowledgeGraphIngestionService.ingestAgentExecution(updated);
                    logger.debug("Ingested agent execution {} into knowledge graph", executionId);
                }
            } catch (Exception e) {
                logger.warn("Failed to ingest agent execution {} into knowledge graph: {}", executionId, e.getMessage());
                // Don't fail the audit close if ingestion fails
            }

            return updated;
        } else {
            logger.debug("Agent execution audit {} already closed at {}", executionId, audit.getEndTime());
            return audit;
        }
    }

    /**
     * Delete all agent execution audit records.
     * Use with caution - this permanently removes all audit history.
     *
     * @return the number of records deleted
     */
    @Transactional
    public long deleteAllAudits() {
        long count = repository.count();
        repository.deleteAll();
        logger.info("Deleted {} agent execution audit records", count);
        return count;
    }

    /**
     * Delete audit records older than the specified number of days.
     *
     * @param days number of days to retain
     * @return the number of records deleted
     */
    @Transactional
    public long deleteAuditsOlderThan(int days) {
        Instant cutoff = Instant.now().minus(java.time.Duration.ofDays(days));
        List<AgentExecutionAudit> oldAudits = repository.findByStartTimeBetween(Instant.EPOCH, cutoff);
        long count = oldAudits.size();
        repository.deleteAll(oldAudits);
        logger.info("Deleted {} agent execution audit records older than {} days", count, days);
        return count;
    }

    /**
     * Consolidate duplicate audit entries based on agentType, agentId, status, and executedBy.
     * Duplicates are merged into a single record with occurrenceCount incremented.
     *
     * @return the number of records consolidated (deleted)
     */
    @Transactional
    public long consolidateDuplicates() {
        List<AgentExecutionAudit> allAudits = repository.findAllOrderByStartTimeDesc();

        // Group audits by key: agentType + agentId + status + executedBy
        java.util.Map<String, java.util.List<AgentExecutionAudit>> grouped = new java.util.HashMap<>();

        for (AgentExecutionAudit audit : allAudits) {
            // Only consolidate completed executions (not RUNNING)
            if ("RUNNING".equals(audit.getStatus())) {
                continue;
            }

            String key = buildConsolidationKey(audit);
            grouped.computeIfAbsent(key, k -> new java.util.ArrayList<>()).add(audit);
        }

        long consolidated = 0;

        for (java.util.Map.Entry<String, java.util.List<AgentExecutionAudit>> entry : grouped.entrySet()) {
            java.util.List<AgentExecutionAudit> duplicates = entry.getValue();

            if (duplicates.size() <= 1) {
                continue; // No duplicates to consolidate
            }

            // Sort by startTime descending to keep the most recent
            duplicates.sort((a, b) -> b.getStartTime().compareTo(a.getStartTime()));

            // Keep the most recent one and merge others into it
            AgentExecutionAudit primary = duplicates.get(0);

            int totalOccurrences = primary.getOccurrenceCount() != null ? primary.getOccurrenceCount() : 1;
            Instant earliestStart = primary.getStartTime();
            Instant latestEnd = primary.getEndTime();
            Long totalDuration = primary.getDurationMs() != null ? primary.getDurationMs() : 0L;

            for (int i = 1; i < duplicates.size(); i++) {
                AgentExecutionAudit duplicate = duplicates.get(i);

                // Accumulate occurrence count
                totalOccurrences += duplicate.getOccurrenceCount() != null ? duplicate.getOccurrenceCount() : 1;

                // Track earliest start time
                if (duplicate.getStartTime() != null && duplicate.getStartTime().isBefore(earliestStart)) {
                    earliestStart = duplicate.getStartTime();
                }

                // Track latest end time
                if (duplicate.getEndTime() != null && (latestEnd == null || duplicate.getEndTime().isAfter(latestEnd))) {
                    latestEnd = duplicate.getEndTime();
                }

                // Accumulate duration
                if (duplicate.getDurationMs() != null) {
                    totalDuration += duplicate.getDurationMs();
                }

                // Delete the duplicate
                repository.delete(duplicate);
                consolidated++;
            }

            // Update the primary record
            primary.setOccurrenceCount(totalOccurrences);
            primary.setLastOccurrence(primary.getStartTime()); // Most recent occurrence
            primary.setStartTime(earliestStart); // Earliest start
            primary.setDurationMs(totalDuration); // Total duration across all occurrences

            repository.save(primary);
        }

        logger.info("Consolidated {} duplicate agent execution audit records", consolidated);
        return consolidated;
    }

    /**
     * Build a key for grouping duplicate audits.
     * Audits with the same key are considered duplicates.
     */
    private String buildConsolidationKey(AgentExecutionAudit audit) {
        return String.join("|",
            audit.getAgentType() != null ? audit.getAgentType() : "",
            audit.getAgentId() != null ? audit.getAgentId() : "",
            audit.getStatus() != null ? audit.getStatus() : "",
            audit.getExecutedBy() != null ? audit.getExecutedBy() : ""
        );
    }
}
