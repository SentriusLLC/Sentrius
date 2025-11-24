package io.sentrius.agent.analysis.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Represents a configuration change request for the analytics agent.
 * Requires two-party approval through ZTAT tokens.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentConfigurationChange {
    
    /**
     * Type of configuration change
     */
    public enum ChangeType {
        ENABLE_LLM_GUIDANCE,
        DISABLE_LLM_GUIDANCE,
        UPDATE_HEARTBEAT_INTERVAL,
        ENABLE_EVALUATION,
        DISABLE_EVALUATION,
        UPDATE_EVALUATION_THRESHOLD,
        UPDATE_SCHEDULE
    }
    
    /**
     * Status of the configuration change request
     */
    public enum ChangeStatus {
        PENDING_APPROVAL,
        APPROVED,
        REJECTED,
        APPLIED,
        FAILED
    }
    
    private String changeId;
    private ChangeType changeType;
    private ChangeStatus status;
    
    private String requestedBy;
    private Instant requestedAt;
    
    private String approvedBy;
    private Instant approvedAt;
    
    private String approvalToken; // ZTAT token used for approval
    
    private String configurationKey;
    private String oldValue;
    private String newValue;
    
    private String reason;
    private String errorMessage;
}
