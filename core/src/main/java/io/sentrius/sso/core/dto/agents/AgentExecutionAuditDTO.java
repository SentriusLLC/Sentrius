package io.sentrius.sso.core.dto.agents;

import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO for agent execution audit records.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AgentExecutionAuditDTO {
    private UUID id;
    private String agentId;
    private String executionId;
    private String agentType;
    private String executedBy;
    private String status;
    private String summary;
    private String resourceLinks;
    private String podLogs;
    private Integer exitCode;
    private Instant startTime;
    private Instant endTime;
    private Long durationMs;
    private Integer occurrenceCount;
    private Instant lastOccurrence;
}
