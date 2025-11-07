package io.sentrius.sso.core.dto.abac;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AttributeAssignmentDTO {
    private Long id;
    private String targetType;
    private String targetId;
    private String username; // For display
    private String attributeName;
    private String attributeValue;
    private String source;
    private LocalDateTime validFrom;
    private LocalDateTime validUntil;
    private boolean syncedFromKeycloak;
    private boolean syncToKeycloak; // For create/update requests
    private boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
