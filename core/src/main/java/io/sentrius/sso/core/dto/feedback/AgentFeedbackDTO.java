package io.sentrius.sso.core.dto.feedback;

import io.sentrius.sso.core.feedback.FeedbackType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentFeedbackDTO {
    private Long id;
    private String agentId;
    private String agentName;
    private FeedbackType feedbackType;
    private String feedbackText;
    private String context;
    private String actionId;
    private Integer trustImpact;
    private String providedBy;
    private LocalDateTime timestamp;
    private Boolean processed;
    private String behaviorCategory;
    private Double reinforcementWeight;
}
