package io.sentrius.sso.core.dto.documents;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * DTO for knowledge graph relationship data transfer.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class KnowledgeGraphRelationshipDTO {
    
    private String id;
    private String relationshipType;
    private String fromNode;
    private String toNode;
    private Map<String, Object> properties;
    private String createdBy;
    private LocalDateTime createdAt;
    private Double weight;
    private String description;
}
