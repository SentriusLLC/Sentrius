package io.sentrius.sso.core.services.trust;

import io.sentrius.sso.core.model.trust.PolicyViolationEvent;
import io.sentrius.sso.core.model.trust.PolicyViolationEventType;
import io.sentrius.sso.core.repository.trust.PolicyViolationEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Service for recording and querying policy violation events.
 * These events are used in trust score calculations.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PolicyViolationEventService {
    
    private final PolicyViolationEventRepository repository;
    
    /**
     * Record a policy violation event.
     * 
     * @param entityId The ID of the entity (agent or user) involved
     * @param entityName The name of the entity
     * @param eventType The type of violation event
     * @param approved Whether the violation was approved
     * @param endpoint The endpoint that was accessed
     * @param policyId The policy that was violated
     * @param approverId The ID of the approver
     * @param ztatRequestId The ZTAT request ID (if applicable)
     * @param description Additional description
     * @return The saved event
     */
    @Transactional
    public PolicyViolationEvent recordViolationEvent(
            String entityId,
            String entityName,
            PolicyViolationEventType eventType,
            boolean approved,
            String endpoint,
            String policyId,
            String approverId,
            Long ztatRequestId,
            String description) {
        
        PolicyViolationEvent event = PolicyViolationEvent.builder()
            .entityId(entityId)
            .entityName(entityName)
            .eventType(eventType)
            .approved(approved)
            .endpoint(endpoint)
            .policyId(policyId)
            .approverId(approverId)
            .ztatRequestId(ztatRequestId)
            .description(description)
            .timestamp(LocalDateTime.now())
            .build();
        
        PolicyViolationEvent saved = repository.save(event);
        
        log.info("Recorded policy violation event for entity {}: type={}, approved={}, endpoint={}",
            entityId, eventType, approved, endpoint);
        
        return saved;
    }
    
    /**
     * Record a ZTAT approval event
     */
    @Transactional
    public PolicyViolationEvent recordZtatApproval(
            String entityId,
            String entityName,
            String endpoint,
            String policyId,
            String approverId,
            Long ztatRequestId,
            String description) {
        
        return recordViolationEvent(
            entityId,
            entityName,
            PolicyViolationEventType.ZTAT_REQUEST_APPROVED,
            true,
            endpoint,
            policyId,
            approverId,
            ztatRequestId,
            description
        );
    }
    
    /**
     * Record a ZTAT denial event
     */
    @Transactional
    public PolicyViolationEvent recordZtatDenial(
            String entityId,
            String entityName,
            String endpoint,
            String policyId,
            String approverId,
            Long ztatRequestId,
            String description) {
        
        return recordViolationEvent(
            entityId,
            entityName,
            PolicyViolationEventType.ZTAT_REQUEST_DENIED,
            false,
            endpoint,
            policyId,
            approverId,
            ztatRequestId,
            description
        );
    }
    
    /**
     * Record an OPS JIT approval event
     */
    @Transactional
    public PolicyViolationEvent recordOpsJitApproval(
            String entityId,
            String entityName,
            String endpoint,
            String policyId,
            String approverId,
            Long ztatRequestId,
            String description) {
        
        return recordViolationEvent(
            entityId,
            entityName,
            PolicyViolationEventType.OPS_JIT_APPROVED,
            true,
            endpoint,
            policyId,
            approverId,
            ztatRequestId,
            description
        );
    }
    
    /**
     * Record an OPS JIT denial event
     */
    @Transactional
    public PolicyViolationEvent recordOpsJitDenial(
            String entityId,
            String entityName,
            String endpoint,
            String policyId,
            String approverId,
            Long ztatRequestId,
            String description) {
        
        return recordViolationEvent(
            entityId,
            entityName,
            PolicyViolationEventType.OPS_JIT_DENIED,
            false,
            endpoint,
            policyId,
            approverId,
            ztatRequestId,
            description
        );
    }
    
    /**
     * Get the incident count (denied violations) for an entity in the last 30 days.
     * Denied policy violations are counted as incidents that lower trust scores.
     */
    public int getIncidentCount(String entityId) {
        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
        return (int) repository.countDeniedViolations(entityId, thirtyDaysAgo);
    }
    
    /**
     * Get the approved violation count for an entity in the last 30 days.
     * Approved violations can positively influence trust scores when used appropriately.
     */
    public int getApprovedViolationCount(String entityId) {
        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
        return (int) repository.countApprovedViolations(entityId, thirtyDaysAgo);
    }
    
    /**
     * Get all violations for an entity in the last 30 days.
     */
    public int getTotalViolationCount(String entityId) {
        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
        return (int) repository.countAllViolations(entityId, thirtyDaysAgo);
    }
    
    /**
     * Get violation history for an entity
     */
    public List<PolicyViolationEvent> getViolationHistory(String entityId) {
        return repository.findByEntityIdOrderByTimestampDesc(entityId);
    }
    
    /**
     * Get violation history for an entity within a time range
     */
    public List<PolicyViolationEvent> getViolationHistory(
            String entityId, LocalDateTime start, LocalDateTime end) {
        return repository.findByEntityIdAndTimestampBetweenOrderByTimestampDesc(entityId, start, end);
    }
    
    /**
     * Get recent violations across all entities
     */
    public List<PolicyViolationEvent> getRecentViolations(LocalDateTime since) {
        return repository.findRecentViolations(since);
    }
}
