package io.sentrius.agent.monitoring.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Represents a configuration change request for the monitoring agent.
 * Requires two-party approval through ZTAT tokens.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonitoringConfigurationChange {
    
    /**
     * Type of configuration change
     */
    public enum ChangeType {
        ENABLE_LLM_GUIDANCE,
        DISABLE_LLM_GUIDANCE,
        UPDATE_CHECK_INTERVAL,
        ADD_ENDPOINT,
        REMOVE_ENDPOINT,
        UPDATE_THRESHOLD,
        ENABLE_NOTIFICATION,
        DISABLE_NOTIFICATION
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
    
    private String affectedEndpoint;
    private String reason;
    private String errorMessage;
}
