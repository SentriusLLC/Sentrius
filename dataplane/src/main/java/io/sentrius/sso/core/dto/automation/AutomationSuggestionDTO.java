package io.sentrius.sso.core.dto.automation;

import lombok.Data;
import java.sql.Timestamp;

/**
 * DTO for AutomationSuggestion entity
 */
@Data
public class AutomationSuggestionDTO {
    private Long id;
    private String sessionIds;
    private String suggestedScript;
    private String description;
    private String scriptType;
    private String status;
    private Double confidenceScore;
    private Integer patternFrequency;
    private String targetSystem;
    private Timestamp createdAt;
    private Timestamp updatedAt;
    private String suggestedForUsername;
    private Long automationId;
}
