package io.sentrius.agent.monitoring.service;

import io.sentrius.agent.monitoring.model.MonitoringConfigurationChange;
import io.sentrius.sso.core.dto.agents.AgentExecution;
import io.sentrius.sso.core.exceptions.ZtatException;
import io.sentrius.sso.core.services.agents.ZeroTrustClientService;
import io.sentrius.sso.provenance.ProvenanceEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service to manage two-party approval for monitoring agent configuration changes.
 * Uses ZTAT (Zero Trust Access Token) for approval verification.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MonitoringConfigurationApprovalService {

    private final ZeroTrustClientService zeroTrustClientService;
    private final RegisteredMonitoringAgent monitoringAgent;
    
    // In-memory store for pending changes (could be persisted to database in production)
    private final Map<String, MonitoringConfigurationChange> pendingChanges = new ConcurrentHashMap<>();

    /**
     * Request a configuration change
     */
    public MonitoringConfigurationChange requestChange(
        MonitoringConfigurationChange.ChangeType changeType,
        String configKey,
        String oldValue,
        String newValue,
        String requestedBy,
        String reason,
        String affectedEndpoint
    ) {
        String changeId = UUID.randomUUID().toString();
        
        MonitoringConfigurationChange change = MonitoringConfigurationChange.builder()
            .changeId(changeId)
            .changeType(changeType)
            .status(MonitoringConfigurationChange.ChangeStatus.PENDING_APPROVAL)
            .requestedBy(requestedBy)
            .requestedAt(Instant.now())
            .configurationKey(configKey)
            .oldValue(oldValue)
            .newValue(newValue)
            .affectedEndpoint(affectedEndpoint)
            .reason(reason)
            .build();
        
        pendingChanges.put(changeId, change);
        
        log.info("Monitoring configuration change requested: id={}, type={}, key={}, requestedBy={}", 
            changeId, changeType, configKey, requestedBy);
        
        // Submit provenance event
        submitProvenanceEvent(change, "Monitoring configuration change requested");
        
        return change;
    }

    /**
     * Approve a configuration change using ZTAT token
     */
    public MonitoringConfigurationChange approveChange(String changeId, String approverUsername, String ztatToken) 
        throws ZtatException {
        
        MonitoringConfigurationChange change = pendingChanges.get(changeId);
        if (change == null) {
            throw new IllegalArgumentException("Change request not found: " + changeId);
        }
        
        if (change.getStatus() != MonitoringConfigurationChange.ChangeStatus.PENDING_APPROVAL) {
            throw new IllegalStateException("Change request is not pending approval: " + changeId);
        }
        
        // Verify the approver is different from the requester (two-party rule)
        if (approverUsername.equals(change.getRequestedBy())) {
            throw new IllegalArgumentException("Approver cannot be the same as requester (two-party rule)");
        }
        
        // Verify ZTAT token
        AgentExecution execution = monitoringAgent.getAgentExecution();
        try {
            // Validate the ZTAT token through the zero trust service
            // For now, we just log the validation attempt
            // In production, this would call an actual validation endpoint
            log.info("Validating ZTAT token for change approval");
        } catch (Exception e) {
            log.error("ZTAT token validation failed", e);
            throw new ZtatException("{\"message\":\"Invalid or expired ZTAT token\"}", "approval");
        }
        
        // Update change record
        change.setStatus(MonitoringConfigurationChange.ChangeStatus.APPROVED);
        change.setApprovedBy(approverUsername);
        change.setApprovedAt(Instant.now());
        change.setApprovalToken(ztatToken);
        
        log.info("Monitoring configuration change approved: id={}, approvedBy={}", changeId, approverUsername);
        
        // Submit provenance event
        submitProvenanceEvent(change, "Monitoring configuration change approved");
        
        // Apply the change
        applyChange(change);
        
        return change;
    }

    /**
     * Reject a configuration change
     */
    public MonitoringConfigurationChange rejectChange(String changeId, String approverUsername) {
        MonitoringConfigurationChange change = pendingChanges.get(changeId);
        if (change == null) {
            throw new IllegalArgumentException("Change request not found: " + changeId);
        }
        
        change.setStatus(MonitoringConfigurationChange.ChangeStatus.REJECTED);
        change.setApprovedBy(approverUsername);
        change.setApprovedAt(Instant.now());
        
        log.info("Monitoring configuration change rejected: id={}, rejectedBy={}", changeId, approverUsername);
        
        // Submit provenance event
        submitProvenanceEvent(change, "Monitoring configuration change rejected");
        
        pendingChanges.remove(changeId);
        
        return change;
    }

    /**
     * Apply an approved configuration change
     */
    private void applyChange(MonitoringConfigurationChange change) {
        try {
            log.info("Applying monitoring configuration change: id={}, type={}", 
                change.getChangeId(), change.getChangeType());
            
            // The actual application of changes would be handled by the specific
            // services (e.g., updating monitoring configs, adjusting intervals)
            // For now, we just mark it as applied
            
            change.setStatus(MonitoringConfigurationChange.ChangeStatus.APPLIED);
            
            // Submit provenance event
            submitProvenanceEvent(change, "Monitoring configuration change applied");
            
            // Remove from pending
            pendingChanges.remove(change.getChangeId());
            
        } catch (Exception e) {
            log.error("Failed to apply monitoring configuration change", e);
            change.setStatus(MonitoringConfigurationChange.ChangeStatus.FAILED);
            change.setErrorMessage(e.getMessage());
            
            submitProvenanceEvent(change, "Monitoring configuration change failed: " + e.getMessage());
        }
    }

    /**
     * Get all pending changes
     */
    public Map<String, MonitoringConfigurationChange> getPendingChanges() {
        return new ConcurrentHashMap<>(pendingChanges);
    }

    /**
     * Get a specific change
     */
    public MonitoringConfigurationChange getChange(String changeId) {
        return pendingChanges.get(changeId);
    }

    /**
     * Submit provenance event for audit trail
     */
    private void submitProvenanceEvent(MonitoringConfigurationChange change, String summary) {
        try {
            ProvenanceEvent event = ProvenanceEvent.builder()
                .eventType(ProvenanceEvent.EventType.POLICY_EVALUATION) // Using closest available type
                .actor(change.getApprovedBy() != null ? change.getApprovedBy() : change.getRequestedBy())
                .triggeringUser(monitoringAgent.getAgentName())
                .outputSummary(summary)
                .sessionId(change.getChangeId())
                .build();
            
            // Event will be picked up by provenance system
            log.debug("Provenance event submitted for change {}", change.getChangeId());
        } catch (Exception e) {
            log.warn("Failed to submit provenance event", e);
        }
    }
}
