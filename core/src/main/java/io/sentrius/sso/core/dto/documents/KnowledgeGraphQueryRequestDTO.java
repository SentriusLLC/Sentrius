package io.sentrius.sso.core.dto.documents;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO for knowledge graph query request.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class KnowledgeGraphQueryRequestDTO {
    
    private String queryType;
    private String startNodeId;
    private String targetNodeId;
    private String searchText;
    private List<String> nodeTypes;
    private List<String> relationshipTypes;
    private Integer maxDepth;
    private Integer limit;
    private String customQuery;
}
