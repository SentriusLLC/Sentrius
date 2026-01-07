package io.sentrius.sso.core.dto.tooltip;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for tooltip/chat endpoint.
 * Contains the user's chat message and optional element context.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TooltipChatRequest {
    /**
     * The user's chat message/question
     */
    private String message;
    
    /**
     * Optional element context if the chat is related to a specific UI element
     */
    private TooltipDescribeRequest.ElementContext context;
    
    /**
     * Optional timestamp for tracking/logging purposes
     */
    private Long timestamp;
}
