package io.sentrius.sso.core.model.documents;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response from knowledge graph query operations.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class KnowledgeGraphQueryResponse {
    
    /**
     * Nodes found in the query
     */
    private List<KnowledgeGraphNode> nodes;
    
    /**
     * Relationships found in the query
     */
    private List<KnowledgeGraphRelationship> relationships;
    
    /**
     * Total count of results (may be > nodes.size if paginated)
     */
    private Integer totalCount;
    
    /**
     * Query execution time in milliseconds
     */
    private Long executionTimeMs;
    
    /**
     * Additional metadata about the query results
     */
    private String metadata;
}
