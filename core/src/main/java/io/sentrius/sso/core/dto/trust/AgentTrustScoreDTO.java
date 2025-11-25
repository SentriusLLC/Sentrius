package io.sentrius.sso.core.dto.trust;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentTrustScoreDTO {
    private Long id;
    private String agentId;
    private String agentName;
    private Integer trustScore;
    private Double identityScore;
    private Double provenanceScore;
    private Double runtimeScore;
    private Double behaviorScore;
    private Double feedbackScore;
    private String evaluationResult;
    private String policyId;
    private LocalDateTime timestamp;
    private Integer priorRuns;
    private Integer incidentCount;
    private Boolean enclaveVerified;
    private String evaluationNotes;
}
