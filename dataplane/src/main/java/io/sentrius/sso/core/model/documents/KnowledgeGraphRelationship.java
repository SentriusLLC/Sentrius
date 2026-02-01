package io.sentrius.sso.core.model.documents;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Represents a relationship (edge) in the knowledge graph stored in SurrealDB.
 * Relationships connect nodes with typed edges and optional properties.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class KnowledgeGraphRelationship {
    
    /**
     * Unique identifier in SurrealDB
     */
    private String id;
    
    /**
     * Relationship type (references, derived_from, similar_to, contains, etc.)
     */
    private String relationshipType;
    
    /**
     * Source node ID
     */
    private String fromNode;
    
    /**
     * Target node ID
     */
    private String toNode;
    
    /**
     * Optional relationship properties (weight, confidence, etc.)
     */
    private Map<String, Object> properties;
    
    /**
     * Created by user
     */
    private String createdBy;
    
    /**
     * Creation timestamp
     */
    private LocalDateTime createdAt;
    
    /**
     * Relationship strength or weight (0.0 to 1.0)
     */
    private Double weight;
    
    /**
     * Optional description of the relationship
     */
    private String description;
}
