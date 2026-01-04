package io.sentrius.sso.core.dto.tooltip;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Request DTO for tooltip/describe endpoint.
 * Contains context information about the UI element that needs a tooltip description.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TooltipDescribeRequest {
    /**
     * Element context information extracted from the frontend
     */
    private ElementContext context;
    
    /**
     * Optional timestamp for tracking/logging purposes
     */
    private Long timestamp;
    
    /**
     * Context information about a UI element
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ElementContext {
        /**
         * HTML tag name (e.g., "BUTTON", "INPUT")
         */
        private String tagName;
        
        /**
         * Element ID attribute
         */
        private String id;
        
        /**
         * Element class names
         */
        private String className;
        
        /**
         * Text content of the element (truncated)
         */
        private String textContent;
        
        /**
         * Relevant HTML attributes (type, name, value, etc.)
         */
        private Map<String, String> attributes;
        
        /**
         * Inner HTML (truncated)
         */
        private String innerHTML;
        
        /**
         * CSS path to the element
         */
        private String path;
    }
}
