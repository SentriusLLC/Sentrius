package io.sentrius.sso.core.dto.tooltip;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for tooltip/chat endpoint.
 * Contains the AI-generated chat response.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TooltipChatResponse {
    /**
     * The AI-generated chat response
     */
    private String response;
    
    /**
     * Optional alternative message field for compatibility
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
