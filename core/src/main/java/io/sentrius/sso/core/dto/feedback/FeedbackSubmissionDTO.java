package io.sentrius.sso.core.dto.feedback;

import io.sentrius.sso.core.feedback.FeedbackType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeedbackSubmissionDTO {
    @NotBlank(message = "Agent ID is required")
    private String agentId;
    
    @NotNull(message = "Feedback type is required")
    private FeedbackType feedbackType;
    
    @NotBlank(message = "Feedback text is required")
    private String feedbackText;
    
    private String context;
    private String actionId;
    private String behaviorCategory;
}
