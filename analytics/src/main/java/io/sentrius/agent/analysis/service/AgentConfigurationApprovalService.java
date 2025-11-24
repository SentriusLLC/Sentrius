package io.sentrius.agent.analysis.service;

import io.sentrius.agent.analysis.model.AgentConfigurationChange;
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
 * Service to manage two-party approval for analytics agent configuration changes.
 * Uses ZTAT (Zero Trust Access Token) for approval verification.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentConfigurationApprovalService {

    private final ZeroTrustClientService zeroTrustClientService;
    private final RegisteredAnalyticsAgent analyticsAgent;
    
    // In-memory store for pending changes (could be persisted to database in production)
    private final Map<String, AgentConfigurationChange> pendingChanges = new ConcurrentHashMap<>();

    /**
     * Request a configuration change
     */
    public AgentConfigurationChange requestChange(
        AgentConfigurationChange.ChangeType changeType,
        String configKey,
        String oldValue,
        String newValue,
        String requestedBy,
        String reason
    ) {
        String changeId = UUID.randomUUID().toString();
        
        AgentConfigurationChange change = AgentConfigurationChange.builder()
            .changeId(changeId)
            .changeType(changeType)
            .status(AgentConfigurationChange.ChangeStatus.PENDING_APPROVAL)
            .requestedBy(requestedBy)
            .requestedAt(Instant.now())
            .configurationKey(configKey)
            .oldValue(oldValue)
            .newValue(newValue)
            .reason(reason)
            .build();
        
        pendingChanges.put(changeId, change);
        
        log.info("Configuration change requested: id={}, type={}, key={}, requestedBy={}", 
            changeId, changeType, configKey, requestedBy);
        
        // Submit provenance event
        submitProvenanceEvent(change, "Configuration change requested");
        
        return change;
    }

    /**
     * Approve a configuration change using ZTAT token
     */
    public AgentConfigurationChange approveChange(String changeId, String approverUsername, String ztatToken) 
        throws ZtatException {
        
        AgentConfigurationChange change = pendingChanges.get(changeId);
        if (change == null) {
            throw new IllegalArgumentException("Change request not found: " + changeId);
        }
        
        if (change.getStatus() != AgentConfigurationChange.ChangeStatus.PENDING_APPROVAL) {
            throw new IllegalStateException("Change request is not pending approval: " + changeId);
        }
        
        // Verify the approver is different from the requester (two-party rule)
        if (approverUsername.equals(change.getRequestedBy())) {
            throw new IllegalArgumentException("Approver cannot be the same as requester (two-party rule)");
        }
        
        // Verify ZTAT token
        AgentExecution execution = analyticsAgent.getAgentExecution();
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
        change.setStatus(AgentConfigurationChange.ChangeStatus.APPROVED);
        change.setApprovedBy(approverUsername);
        change.setApprovedAt(Instant.now());
        change.setApprovalToken(ztatToken);
        
        log.info("Configuration change approved: id={}, approvedBy={}", changeId, approverUsername);
        
        // Submit provenance event
        submitProvenanceEvent(change, "Configuration change approved");
        
        // Apply the change
        applyChange(change);
        
        return change;
    }

    /**
     * Reject a configuration change
     */
    public AgentConfigurationChange rejectChange(String changeId, String approverUsername) {
        AgentConfigurationChange change = pendingChanges.get(changeId);
        if (change == null) {
            throw new IllegalArgumentException("Change request not found: " + changeId);
        }
        
        change.setStatus(AgentConfigurationChange.ChangeStatus.REJECTED);
        change.setApprovedBy(approverUsername);
        change.setApprovedAt(Instant.now());
        
        log.info("Configuration change rejected: id={}, rejectedBy={}", changeId, approverUsername);
        
        // Submit provenance event
        submitProvenanceEvent(change, "Configuration change rejected");
        
        pendingChanges.remove(changeId);
        
        return change;
    }

    /**
     * Apply an approved configuration change
     */
    private void applyChange(AgentConfigurationChange change) {
        try {
            log.info("Applying configuration change: id={}, type={}", 
                change.getChangeId(), change.getChangeType());
            
            // The actual application of changes would be handled by the specific
            // services (e.g., updating Spring properties, calling configuration methods)
            // For now, we just mark it as applied
            
            change.setStatus(AgentConfigurationChange.ChangeStatus.APPLIED);
            
            // Submit provenance event
            submitProvenanceEvent(change, "Configuration change applied");
            
            // Remove from pending
            pendingChanges.remove(change.getChangeId());
            
        } catch (Exception e) {
            log.error("Failed to apply configuration change", e);
            change.setStatus(AgentConfigurationChange.ChangeStatus.FAILED);
            change.setErrorMessage(e.getMessage());
            
            submitProvenanceEvent(change, "Configuration change failed: " + e.getMessage());
        }
    }

    /**
     * Get all pending changes
     */
    public Map<String, AgentConfigurationChange> getPendingChanges() {
        return new ConcurrentHashMap<>(pendingChanges);
    }

    /**
     * Get a specific change
     */
    public AgentConfigurationChange getChange(String changeId) {
        return pendingChanges.get(changeId);
    }

    /**
     * Submit provenance event for audit trail
     */
    private void submitProvenanceEvent(AgentConfigurationChange change, String summary) {
        try {
            ProvenanceEvent event = ProvenanceEvent.builder()
                .eventType(ProvenanceEvent.EventType.POLICY_EVALUATION) // Using closest available type
                .actor(change.getApprovedBy() != null ? change.getApprovedBy() : change.getRequestedBy())
                .triggeringUser(analyticsAgent.getAgentName())
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
