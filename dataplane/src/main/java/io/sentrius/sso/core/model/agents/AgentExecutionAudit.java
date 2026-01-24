package io.sentrius.sso.core.model.agents;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

/**
 * Entity for storing agent execution audit records with summaries and resource links.
 */
@Entity
@Setter
@Getter
@Table(name = "agent_execution_audits")
public class AgentExecutionAudit {

    @Id
    @GeneratedValue
    private UUID id;

    /**
     * The unique agent instance identifier (e.g., pod name).
     */
    @Column(nullable = false)
    private String agentId;

    /**
     * The execution context identifier from AgentExecutionService.
     */
    @Column(nullable = false, unique = true)
    private String executionId;

    /**
     * The type of agent (e.g., chat-helper, coding, mcp, agent-summarizer).
     */
    @Column(nullable = false)
    private String agentType;

    /**
     * The username who triggered this agent execution.
     */
    private String executedBy;

    /**
     * Execution status: RUNNING, COMPLETED, FAILED, ERROR.
     */
    @Column(nullable = false)
    private String status;

    /**
     * Human-readable summary of what the agent did.
     */
    @Lob
    @Column(columnDefinition = "TEXT")
    private String summary;

    /**
     * JSON array of resource links mentioned in the summary.
     * Example: [{"type":"issue","url":"https://github.com/org/repo/issues/123","label":"Issue #123"}]
     */
    @Lob
    @Column(columnDefinition = "TEXT")
    private String resourceLinks;

    /**
     * Raw pod logs captured during execution (optional).
     */
    @Lob
    @Column(columnDefinition = "TEXT")
    private String podLogs;

    /**
     * Exit code from the agent process (if available).
     */
    private Integer exitCode;

    /**
     * Timestamp when agent execution started.
     */
    @Column(nullable = false)
    private Instant startTime;

    /**
     * Timestamp when agent execution ended.
     */
    private Instant endTime;

    /**
     * Duration of execution in milliseconds.
     */
    private Long durationMs;

    /**
     * Number of times this type of execution has occurred.
     * Used for consolidating duplicate audit entries.
     */
    @Column(nullable = false)
    private Integer occurrenceCount = 1;

    /**
     * Timestamp of the last occurrence (when consolidating duplicates).
     */
    private Instant lastOccurrence;

    @PrePersist
    protected void onCreate() {
        if (startTime == null) {
            startTime = Instant.now();
        }
        if (status == null) {
            status = "RUNNING";
        }
    }

    /**
     * Calculate and set the duration based on start and end times.
     */
    public void calculateDuration() {
        if (startTime != null && endTime != null) {
            durationMs = endTime.toEpochMilli() - startTime.toEpochMilli();
        }
    }
}
