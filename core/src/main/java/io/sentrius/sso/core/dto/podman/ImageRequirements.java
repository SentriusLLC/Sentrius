package io.sentrius.sso.core.dto.podman;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Requirements/verification criteria for agent images
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ImageRequirements {
    
    /**
     * Require image to be signed
     */
    @Builder.Default
    private boolean signed = false;
    
    /**
     * Require agent name to match in image metadata
     */
    @Builder.Default
    private boolean agentNameMatch = false;
    
    /**
     * Minimum image version requirement
     */
    private String minVersion;
}
