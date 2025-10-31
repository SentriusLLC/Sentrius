package io.sentrius.sso.core.dto.abac;

import lombok.*;

/**
 * DTO for AttributeDefinition management via REST API
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttributeDefinitionDTO {
    private Long id;
    private String attributeName;
    private String attributeScope;
    private String attributeType;
    private String description;
    private String allowedValues;
    private Boolean syncedWithKeycloak;
    private String keycloakAttributeName;
    private Boolean isRequired;
    private Boolean isActive;
}
