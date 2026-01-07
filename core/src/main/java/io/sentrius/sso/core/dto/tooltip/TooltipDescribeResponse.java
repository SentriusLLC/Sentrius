package io.sentrius.sso.core.dto.tooltip;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for tooltip/describe endpoint.
 * Contains the AI-generated description/tooltip for a UI element.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TooltipDescribeResponse {
    /**
     * The AI-generated description/tooltip text
     */
    private String description;
    
    /**
     * Optional detailed message with additional context
     */
    private String message;
    
    /**
     * Indicates if the response was generated successfully
     */
    private boolean success;
    
    /**
     * Optional error message if generation failed
     */
    private String error;
}
