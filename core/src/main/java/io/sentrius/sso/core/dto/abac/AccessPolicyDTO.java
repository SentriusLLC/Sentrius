package io.sentrius.sso.core.dto.abac;

import lombok.*;

/**
 * DTO for AccessPolicy management via REST API
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccessPolicyDTO {
    private Long id;
    private String policyName;
    private String description;
    private String resourceType;
    private String resourcePattern;
    private String actions;
    private String effect;
    private Integer priority;
    private String ruleCombination;
    private Boolean isActive;
    private String evaluationMode;
    private String createdAt;
    private String updatedAt;
    private Integer ruleCount;
}
