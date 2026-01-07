package io.sentrius.agent.launcher.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Configuration for image selection strategy
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SelectionConfig {
    
    /**
     * Selection strategy: "generation", "latest", "tag"
     */
    private String strategy;
    
    /**
     * Maximum generation to select (for generation strategy)
     */
    private Integer maxGeneration;
    
    /**
     * Minimum generation to select (for generation strategy)
     */
    private Integer minGeneration;
    
    /**
     * Specific tag to select (for tag strategy)
     */
    private String specificTag;
}
