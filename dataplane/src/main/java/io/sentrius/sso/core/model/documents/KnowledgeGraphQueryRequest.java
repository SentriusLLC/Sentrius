package io.sentrius.sso.core.model.documents;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Query request for knowledge graph operations.
 * Supports various query types including traversal, search, and path finding.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class KnowledgeGraphQueryRequest {
    
    /**
     * Query type: TRAVERSE, SEARCH, PATH, NEIGHBORS, SUBGRAPH
     */
    private QueryType queryType;
    
    /**
     * Starting node ID for traversal queries
     */
    private String startNodeId;
    
    /**
     * Target node ID for path queries
     */
    private String targetNodeId;
    
    /**
     * Search text for semantic search
     */
    private String searchText;
    
    /**
     * Node types to filter (optional)
     */
    private List<String> nodeTypes;
    
    /**
     * Relationship types to traverse (optional)
     */
    private List<String> relationshipTypes;
    
    /**
     * Maximum depth for traversal (default: 2)
     */
    private Integer maxDepth;
    
    /**
     * Maximum results to return (default: 50)
     */
    private Integer limit;
    
    /**
     * Custom SurrealQL query (for advanced users)
     */
    private String customQuery;
    
    public enum QueryType {
        TRAVERSE,      // Traverse from a starting node
        SEARCH,        // Search nodes by text or properties
        PATH,          // Find path between two nodes
        NEIGHBORS,     // Get immediate neighbors of a node
        SUBGRAPH       // Get subgraph around a node
    }
}
