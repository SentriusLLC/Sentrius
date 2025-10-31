package io.sentrius.sso.core.dto.abac;

import lombok.*;

/**
 * DTO for PolicyRule management via REST API
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PolicyRuleDTO {
    private Long id;
    private Long policyId;
    private String attributeName;
    private String attributeScope;
    private String operator;
    private String expectedValue;
    private Boolean isNegated;
    private Integer evaluationOrder;
    private String description;
    private Boolean isActive;
}
