package io.sentrius.sso.core.model.abac;

import jakarta.persistence.*;
import lombok.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;

/**
 * Defines an access control policy that evaluates attributes to make authorization decisions.
 * Policies can apply to endpoints, data operations, or any protected resource.
 */
@Entity
@Table(name = "access_policies")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AccessPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Unique policy name
     */
    @Column(name = "policy_name", nullable = false, unique = true, length = 255)
    private String policyName;

    /**
     * Human-readable description
     */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /**
     * Type of resource this policy protects
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", nullable = false)
    private ResourceType resourceType;

    /**
     * Specific resource identifier (endpoint path, entity class, etc.)
     * Can use wildcards: /api/v1/data/**
     */
    @Column(name = "resource_pattern", nullable = false, length = 500)
    private String resourcePattern;

    /**
     * Actions this policy applies to (e.g., READ, WRITE, DELETE, EXECUTE)
     * Comma-separated list for multiple actions
     */
    @Column(name = "actions", length = 500)
    private String actions;

    /**
     * Policy effect when rules are satisfied
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "effect", nullable = false)
    private PolicyEffect effect = PolicyEffect.ALLOW;

    /**
     * Priority for policy conflict resolution (higher number = higher priority)
     */
    @Column(name = "priority")
    private Integer priority = 0;

    /**
     * Whether all rules must match (AND) or any rule can match (OR)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "rule_combination")
    private RuleCombination ruleCombination = RuleCombination.AND;

    /**
     * JSON representation of policy rules for complex evaluation
     */
    @Column(name = "rules_json", columnDefinition = "TEXT")
    private String rulesJson;

    /**
     * Whether this policy is active
     */
    @Column(name = "is_active")
    private Boolean isActive = true;

    /**
     * Policy evaluation mode
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "evaluation_mode")
    private EvaluationMode evaluationMode = EvaluationMode.STRICT;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "created_by", length = 255)
    private String createdBy;

    @PrePersist
    protected void onCreate() {
        createdAt = updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    /**
     * Check if this policy applies to the given resource
     */
    public boolean appliesToResource(String resourceId) {
        if (resourcePattern == null) {
            return false;
        }
        
        // Convert wildcard pattern to regex
        String regex = resourcePattern
            .replace(".", "\\.")
            .replace("**", ".*")
            .replace("*", "[^/]*");
        
        return resourceId != null && resourceId.matches(regex);
    }

    /**
     * Type of resource the policy protects
     */
    public enum ResourceType {
        ENDPOINT,       // HTTP endpoints
        DATA_ENTITY,    // Database entities
        OPERATION,      // Specific operations
        SYSTEM_RESOURCE // System resources
    }

    /**
     * Policy effect
     */
    public enum PolicyEffect {
        ALLOW,          // Grant access when rules match
        DENY            // Deny access when rules match
    }

    /**
     * How to combine multiple rules
     */
    public enum RuleCombination {
        AND,            // All rules must match
        OR              // Any rule can match
    }

    /**
     * Policy evaluation strictness
     */
    public enum EvaluationMode {
        STRICT,         // Fail on any error
        PERMISSIVE,     // Allow on evaluation errors
        AUDIT_ONLY      // Log but don't enforce
    }
}
