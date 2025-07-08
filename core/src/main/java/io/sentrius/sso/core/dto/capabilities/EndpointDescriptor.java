package io.sentrius.sso.core.dto.capabilities;

import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Represents a unified descriptor for both REST API endpoints and Verb methods.
 * This allows for a consistent way to describe all capabilities across the system.
 */
@Builder
@Data
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class EndpointDescriptor {
    private String name;
    private String description;
    private String type; // "REST" or "VERB"
    private String httpMethod; // GET, POST, etc. (null for verbs)
    private String path; // REST path (null for verbs)
    private String className; // Class containing the method
    private String methodName; // Method name
    private List<ParameterDescriptor> parameters;
    private AccessLimitations accessLimitations;
    private Map<String, Object> metadata; // Additional metadata
    
    @Builder.Default
    private boolean requiresAuthentication = true;
    
    @Builder.Default
    private boolean requiresTokenManagement = false;
    
    private Class<?> returnType;
}