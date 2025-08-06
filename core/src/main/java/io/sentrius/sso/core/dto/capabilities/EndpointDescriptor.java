package io.sentrius.sso.core.dto.capabilities;

import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.sentrius.sso.core.utils.JsonUtil;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
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
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class EndpointDescriptor {
    @Builder.Default
    private String serviceUrl = ""; // Base URL of the service providing this endpoint
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

    public static String toEmbeddableJson(EndpointDescriptor ed) {
        ObjectNode node = JsonUtil.MAPPER.createObjectNode();
        node.put("name", ed.getName());
        node.put("description", ed.getDescription());
        node.put("type", ed.getType());
        node.put("httpMethod", ed.getHttpMethod());
        node.put("path", ed.getPath());
        node.put("className", ed.getClassName());
        node.put("methodName", ed.getMethodName());
        node.put("requiresAuthentication", ed.isRequiresAuthentication());
        node.put("requiresTokenManagement", ed.isRequiresTokenManagement());

        return node.toString();
    }
}