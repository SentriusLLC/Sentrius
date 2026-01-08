package io.sentrius.sso.core.dto.podman;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.sentrius.sso.core.dto.AgentRegistrationDTO;
import io.sentrius.sso.core.utils.JsonUtil;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Represents the intent for selecting an agent container image.
 * This can be parsed from AgentRegistrationDTO's templateLaunchConfiguration.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@Slf4j
public class ImageIntent {

    
    /**
     * Container registry repository (e.g., "ghcr.io/sentrius/agents/payments")
     * If not specified, falls back to configured registry + agent type
     */
    private String repo;
    
    /**
     * Selection strategy configuration
     */
    private SelectionConfig selection;
    
    /**
     * Image requirements/verification criteria
     */
    private ImageRequirements requirements;
    
    /**
     * Explicitly specified image tag (overrides selection strategy)
     */
    private String tag;
    
    /**
     * Parse ImageIntent from AgentRegistrationDTO
     * Looks for imageIntent in templateLaunchConfiguration JSON
     */
    public static ImageIntent from(AgentRegistrationDTO agent) {
        if (agent == null) {
            return ImageIntent.builder().build();
        }
        
        String launchConfig = agent.getTemplateLaunchConfiguration();
        if (launchConfig == null || launchConfig.trim().isEmpty()) {
            log.debug("No templateLaunchConfiguration found for agent: {}", agent.getAgentName());
            return ImageIntent.builder().build();
        }
        
        try {
            LaunchConfiguration config = JsonUtil.MAPPER.readValue(launchConfig, LaunchConfiguration.class);
            
            if (config.getImageIntent() != null) {
                log.info("Found imageIntent configuration for agent: {}", agent.getAgentName());
                return config.getImageIntent();
            }
            
            log.debug("No imageIntent found in launchConfiguration for agent: {}", agent.getAgentName());
            return ImageIntent.builder().build();
            
        } catch (Exception e) {
            log.warn("Failed to parse templateLaunchConfiguration for agent {}: {}", 
                agent.getAgentName(), e.getMessage());
            return ImageIntent.builder().build();
        }
    }
    
    /**
     * Check if this intent has explicit configuration
     */
    public boolean hasExplicitConfig() {
        return repo != null || tag != null || selection != null;
    }
    
    /**
     * Wrapper class for parsing launch configuration JSON
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class LaunchConfiguration {
        private ImageIntent imageIntent;
        private ResourcesConfig resources;
        private String restartPolicy;
    }
}
