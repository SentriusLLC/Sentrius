package io.sentrius.sso.core.model.documents;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Represents a node in the knowledge graph stored in SurrealDB.
 * Nodes are entities (documents, concepts, users, systems) with properties and relationships.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class KnowledgeGraphNode {
    
    /**
     * Unique identifier in SurrealDB (e.g., "document:123" or "concept:ai")
     */
    private String id;
    
    /**
     * Node type (document, concept, user, system, command, etc.)
     */
    private String nodeType;
    
    /**
     * Display name for the node
     */
    private String name;
    
    /**
     * Optional description or content
     */
    private String description;
    
    /**
     * Reference to original entity ID if applicable (e.g., document ID in PostgreSQL)
     */
    private Long entityId;
    
    /**
     * Custom properties for the node
     */
    private Map<String, Object> properties;
    
    /**
     * ABAC markings that drive access control.
     * Uses visibility expression syntax compatible with Apache Accumulo AccessEvaluator.
     * See Document.markings for detailed documentation on marking syntax.
     *
     * - null or empty: Node is PUBLIC (accessible to all authenticated users)
     * - "USER:username": Node is private to specific user
     * - Other markings: Evaluated against user's authorizations
     */
    private String markings;
    
    /**
     * @deprecated Use markings instead. Classification is now derived from markings.
     * This field is retained for backward compatibility only.
     */
    @Deprecated
    private String classification;

    /**
     * Created by user
     */
    private String createdBy;
    
    /**
     * Creation timestamp
     */
    private LocalDateTime createdAt;
    
    /**
     * Last update timestamp
     */
    private LocalDateTime updatedAt;
    
    /**
     * Metadata tags for categorization
     */
    private List<String> tags;
}
