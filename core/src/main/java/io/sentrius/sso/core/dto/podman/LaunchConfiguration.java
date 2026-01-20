package io.sentrius.sso.core.dto.podman;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Wrapper class for parsing launch configuration JSON
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class LaunchConfiguration {
    private ImageIntent imageIntent;
    private ResourcesConfig resources;
    private String restartPolicy;
}