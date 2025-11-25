package io.sentrius.sso.core.model.trust;

import java.time.LocalDateTime;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "agent_trust_score_history", indexes = {
    @Index(name = "idx_agent_id_timestamp", columnList = "agent_id,timestamp"),
    @Index(name = "idx_timestamp", columnList = "timestamp")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentTrustScoreHistory {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "agent_id", nullable = false)
    private String agentId;
    
    @Column(name = "agent_name")
    private String agentName;
    
    @Column(name = "trust_score", nullable = false)
    private Integer trustScore;
    
    @Column(name = "identity_score")
    private Double identityScore;
    
    @Column(name = "provenance_score")
    private Double provenanceScore;
    
    @Column(name = "runtime_score")
    private Double runtimeScore;
    
    @Column(name = "behavior_score")
    private Double behaviorScore;
    
    @Column(name = "feedback_score")
    private Double feedbackScore;
    
    @Column(name = "evaluation_result", length = 50)
    private String evaluationResult; // SUCCESS, MARGINAL, FAILURE
    
    @Column(name = "policy_id")
    private String policyId;
    
    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;
    
    @Column(name = "prior_runs")
    private Integer priorRuns;
    
    @Column(name = "incident_count")
    private Integer incidentCount;
    
    @Column(name = "enclave_verified")
    private Boolean enclaveVerified;
    
    @Column(name = "evaluation_notes", columnDefinition = "TEXT")
    private String evaluationNotes;
}
