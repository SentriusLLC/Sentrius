package io.sentrius.sso.core.model.trust;

import java.time.LocalDateTime;
import jakarta.persistence.*;
import lombok.*;

/**
 * Records policy violation events when an agent/user tries to access an endpoint
 * outside their policy, and the resulting approval/denial decision.
 * These events are used to calculate behavior scores in trust evaluations.
 */
@Entity
@Table(name = "policy_violation_events", indexes = {
    @Index(name = "idx_pv_entity_id_timestamp", columnList = "entity_id,timestamp"),
    @Index(name = "idx_pv_timestamp", columnList = "timestamp")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PolicyViolationEvent {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /**
     * The ID of the entity (agent or user) that attempted the policy violation
     */
    @Column(name = "entity_id", nullable = false)
    private String entityId;
    
    /**
     * The name of the entity
     */
    @Column(name = "entity_name")
    private String entityName;
    
    /**
     * The type of violation event
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 50)
    private PolicyViolationEventType eventType;
    
    /**
     * Whether the violation was approved by a supervisor
     */
    @Column(name = "approved", nullable = false)
    private Boolean approved;
    
    /**
     * The endpoint that was accessed outside the policy
     */
    @Column(name = "endpoint")
    private String endpoint;
    
    /**
     * The policy ID that was violated
     */
    @Column(name = "policy_id")
    private String policyId;
    
    /**
     * The ID of the user who approved/denied the violation
     */
    @Column(name = "approver_id")
    private String approverId;
    
    /**
     * The ZTAT request ID associated with this event
     */
    @Column(name = "ztat_request_id")
    private Long ztatRequestId;
    
    /**
     * Description or notes about the violation
     */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
    
    /**
     * When this event occurred
     */
    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;
    
    @PrePersist
    protected void onCreate() {
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
        }
    }
}
