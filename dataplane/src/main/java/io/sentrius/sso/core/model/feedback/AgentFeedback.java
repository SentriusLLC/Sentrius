package io.sentrius.sso.core.model.feedback;

import io.sentrius.sso.core.feedback.FeedbackType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Entity representing human feedback on agent behavior.
 * Part of the RLHF (Reinforcement Learning from Human Feedback) system.
 */
@Entity
@Table(name = "agent_feedback", indexes = {
    @Index(name = "idx_agent_feedback_agent_id", columnList = "agent_id"),
    @Index(name = "idx_agent_feedback_timestamp", columnList = "timestamp"),
    @Index(name = "idx_agent_feedback_type", columnList = "feedback_type"),
    @Index(name = "idx_agent_feedback_processed", columnList = "processed")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentFeedback {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "agent_id", nullable = false, length = 255)
    private String agentId;
    
    @Column(name = "agent_name", length = 255)
    private String agentName;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "feedback_type", nullable = false, length = 50)
    private FeedbackType feedbackType;
    
    @Column(name = "feedback_text", columnDefinition = "TEXT", nullable = false)
    private String feedbackText;
    
    @Column(name = "context", columnDefinition = "TEXT")
    private String context;
    
    @Column(name = "action_id", length = 255)
    private String actionId;
    
    @Column(name = "trust_impact")
    private Integer trustImpact;
    
    @Column(name = "provided_by", nullable = false, length = 255)
    private String providedBy;
    
    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;
    
    @Column(name = "processed", nullable = false)
    private Boolean processed = false;
    
    @Column(name = "behavior_category", length = 100)
    private String behaviorCategory;
    
    @Column(name = "reinforcement_weight")
    private Double reinforcementWeight;
    
    @PrePersist
    protected void onCreate() {
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
        }
        if (processed == null) {
            processed = false;
        }
        if (reinforcementWeight == null) {
            reinforcementWeight = calculateReinforcementWeight();
        }
    }
    
    private Double calculateReinforcementWeight() {
        return switch (feedbackType) {
            case POSITIVE -> 1.0;
            case NEGATIVE -> -1.0;
            case CORRECTIVE -> 0.5;
            case NEUTRAL -> 0.0;
        };
    }
}
