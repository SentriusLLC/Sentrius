package io.sentrius.sso.core.model.abac;

import jakarta.persistence.*;
import lombok.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;

/**
 * Individual rule within an access policy.
 * Rules define specific attribute conditions that must be met for policy evaluation.
 */
@Entity
@Table(name = "policy_rules", indexes = {
    @Index(name = "idx_policy_id", columnList = "policy_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PolicyRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Parent policy
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "policy_id", nullable = false)
    private AccessPolicy policy;

    /**
     * Reference to the attribute definition being evaluated
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "attribute_definition_id", nullable = false)
    private AttributeDefinition attributeDefinition;

    /**
     * Operator for comparing attribute values
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "operator", nullable = false)
    private Operator operator;

    /**
     * Expected value for comparison
     */
    @Column(name = "expected_value", nullable = false, columnDefinition = "TEXT")
    private String expectedValue;

    /**
     * Whether this rule is negated (NOT condition)
     */
    @Column(name = "is_negated")
    private Boolean isNegated = false;

    /**
     * Rule evaluation order within policy
     */
    @Column(name = "evaluation_order")
    private Integer evaluationOrder = 0;

    /**
     * Optional description
     */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /**
     * Whether this rule is active
     */
    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    /**
     * Evaluate this rule against an actual attribute value
     */
    public boolean evaluate(String actualValue) {
        if (actualValue == null && operator != Operator.IS_NULL) {
            return applyNegation(false);
        }

        boolean result = switch (operator) {
            case EQUALS -> expectedValue.equals(actualValue);
            case NOT_EQUALS -> !expectedValue.equals(actualValue);
            case CONTAINS -> actualValue != null && actualValue.contains(expectedValue);
            case STARTS_WITH -> actualValue != null && actualValue.startsWith(expectedValue);
            case ENDS_WITH -> actualValue != null && actualValue.endsWith(expectedValue);
            case REGEX_MATCH -> actualValue != null && actualValue.matches(expectedValue);
            case GREATER_THAN -> compareNumeric(actualValue, expectedValue) > 0;
            case LESS_THAN -> compareNumeric(actualValue, expectedValue) < 0;
            case GREATER_OR_EQUAL -> compareNumeric(actualValue, expectedValue) >= 0;
            case LESS_OR_EQUAL -> compareNumeric(actualValue, expectedValue) <= 0;
            case IN_LIST -> isInList(actualValue, expectedValue);
            case NOT_IN_LIST -> !isInList(actualValue, expectedValue);
            case IS_NULL -> actualValue == null;
            case IS_NOT_NULL -> actualValue != null;
        };

        return applyNegation(result);
    }

    private boolean applyNegation(boolean result) {
        return Boolean.TRUE.equals(isNegated) ? !result : result;
    }

    private int compareNumeric(String actual, String expected) {
        try {
            double actualNum = Double.parseDouble(actual);
            double expectedNum = Double.parseDouble(expected);
            return Double.compare(actualNum, expectedNum);
        } catch (NumberFormatException e) {
            return actual.compareTo(expected);
        }
    }

    private boolean isInList(String actualValue, String listString) {
        if (actualValue == null || listString == null) {
            return false;
        }
        String[] values = listString.split(",");
        for (String value : values) {
            if (actualValue.equals(value.trim())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Comparison operators for rule evaluation
     */
    public enum Operator {
        EQUALS,
        NOT_EQUALS,
        CONTAINS,
        STARTS_WITH,
        ENDS_WITH,
        REGEX_MATCH,
        GREATER_THAN,
        LESS_THAN,
        GREATER_OR_EQUAL,
        LESS_OR_EQUAL,
        IN_LIST,
        NOT_IN_LIST,
        IS_NULL,
        IS_NOT_NULL
    }
}
