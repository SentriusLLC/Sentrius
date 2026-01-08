package io.sentrius.sso.core.dto.podman;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Resource requirements for agent pods
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ResourcesConfig {
    
    /**
     * CPU limit (e.g., "500m", "1", "2000m")
     */
    private String cpu;
    
    /**
     * Memory limit (e.g., "512Mi", "1Gi", "2Gi")
     */
    private String memory;
    
    /**
     * CPU request (optional, defaults to limit)
     */
    private String cpuRequest;
    
    /**
     * Memory request (optional, defaults to limit)
     */
    private String memoryRequest;
}
