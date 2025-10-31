package io.sentrius.sso.core.services.abac;

import lombok.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Context for ABAC policy evaluation containing subject, resource, action, and environment attributes.
 */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EvaluationContext {

    /**
     * Subject attributes (user, role, identity)
     */
    @Builder.Default
    private Map<String, String> subjectAttributes = new HashMap<>();

    /**
     * Resource attributes (endpoint, data entity, system resource)
     */
    @Builder.Default
    private Map<String, String> resourceAttributes = new HashMap<>();

    /**
     * Action attributes (operation, method)
     */
    @Builder.Default
    private Map<String, String> actionAttributes = new HashMap<>();

    /**
     * Environment attributes (time, location, device)
     */
    @Builder.Default
    private Map<String, String> environmentAttributes = new HashMap<>();

    /**
     * Add a subject attribute
     */
    public void addSubjectAttribute(String key, String value) {
        subjectAttributes.put(key, value);
    }

    /**
     * Add a resource attribute
     */
    public void addResourceAttribute(String key, String value) {
        resourceAttributes.put(key, value);
    }

    /**
     * Add an action attribute
     */
    public void addActionAttribute(String key, String value) {
        actionAttributes.put(key, value);
    }

    /**
     * Add an environment attribute
     */
    public void addEnvironmentAttribute(String key, String value) {
        environmentAttributes.put(key, value);
    }

    /**
     * Get attribute value by scope and name
     */
    public String getAttribute(String scope, String attributeName) {
        return switch (scope.toUpperCase()) {
            case "SUBJECT" -> subjectAttributes.get(attributeName);
            case "RESOURCE" -> resourceAttributes.get(attributeName);
            case "ACTION" -> actionAttributes.get(attributeName);
            case "ENVIRONMENT" -> environmentAttributes.get(attributeName);
            default -> null;
        };
    }

    /**
     * Get all attributes for a specific scope
     */
    public Map<String, String> getAttributesForScope(String scope) {
        return switch (scope.toUpperCase()) {
            case "SUBJECT" -> subjectAttributes;
            case "RESOURCE" -> resourceAttributes;
            case "ACTION" -> actionAttributes;
            case "ENVIRONMENT" -> environmentAttributes;
            default -> new HashMap<>();
        };
    }
}
