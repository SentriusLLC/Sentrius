package io.sentrius.sso.core.dto.documents;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO for knowledge graph query response.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class KnowledgeGraphQueryResponseDTO {
    
    private List<KnowledgeGraphNodeDTO> nodes;
    private List<KnowledgeGraphRelationshipDTO> relationships;
    private Integer totalCount;
    private Long executionTimeMs;
    private String metadata;
}
