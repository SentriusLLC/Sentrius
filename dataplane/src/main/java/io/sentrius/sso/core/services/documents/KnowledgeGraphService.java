package io.sentrius.sso.core.services.documents;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.surrealdb.Surreal;
import com.surrealdb.SurrealException;
import com.surrealdb.Response;
import com.surrealdb.Value;
import io.sentrius.sso.core.config.SystemOptions;
import io.sentrius.sso.core.dto.ztat.TokenDTO;
import io.sentrius.sso.core.exceptions.ZtatException;
import io.sentrius.sso.core.model.documents.*;
import io.sentrius.sso.core.model.documents.*;
import io.sentrius.sso.provenance.ProvenanceEvent;
import io.sentrius.sso.provenance.ProvenanceLogger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for managing knowledge graph operations using SurrealDB.
 * Provides functionality for storing documents as graph nodes, creating relationships,
 * and querying the knowledge graph for document discovery and investigation.
 *
 * Note: This service uses SurrealDBConnectionFactory to get fresh connections for each operation,
 * allowing dynamic configuration changes to take effect without restart.
 * ProvenanceLogger is optional. When not available, provenance events are skipped.
 */
@Slf4j
@Service
@ConditionalOnClass(name = "com.surrealdb.Surreal")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class KnowledgeGraphService {
    
    /** Maximum content length to include in full before using excerpts only */
    private static final int MAX_FULL_CONTENT_LENGTH = 3000;
    
    /** Length of excerpt to extract around search terms */
    private static final int EXCERPT_LENGTH = 500;
    
    private final SurrealDBConnectionProvider connectionProvider;
    private final ProvenanceLogger provenanceLogger;
    private final DocumentAccessControlService accessControlService;
    private final ObjectMapper objectMapper;
    private final io.sentrius.sso.core.services.agents.LLMService llmService;
    private final SystemOptions systemOptions;
    private final io.sentrius.sso.core.repository.documents.DocumentRepository documentRepository;

    /**
     * Interface for providing SurrealDB connections.
     * This allows the service to work with different connection strategies.
     */
    public interface SurrealDBConnectionProvider {
        boolean isEnabled();
        Surreal getConnection();
    }

    @Autowired
    public KnowledgeGraphService(
        @Autowired(required = false) SurrealDBConnectionProvider connectionProvider,
        @Autowired(required = false) ProvenanceLogger provenanceLogger,
        DocumentAccessControlService accessControlService,
        ObjectMapper objectMapper,
        @Autowired(required = false) io.sentrius.sso.core.services.agents.LLMService llmService,
        SystemOptions systemOptions,
        @Autowired(required = false) io.sentrius.sso.core.repository.documents.DocumentRepository documentRepository
    ) {
        this.connectionProvider = connectionProvider;
        this.provenanceLogger = provenanceLogger;
        this.accessControlService = accessControlService;
        this.objectMapper = objectMapper;
        this.llmService = llmService;
        this.systemOptions = systemOptions;
        this.documentRepository = documentRepository;

        if (connectionProvider == null) {
            log.info("SurrealDB connection provider not configured. Knowledge graph features will be disabled.");
        } else if (!connectionProvider.isEnabled()) {
            log.info("SurrealDB is disabled. Knowledge graph features will be disabled.");
        } else {
            log.info("KnowledgeGraphService initialized with SurrealDB connection provider.");
        }
        if (provenanceLogger == null) {
            log.info("ProvenanceLogger not configured. Provenance logging will be disabled for knowledge graph operations.");
        }
        if (llmService == null) {
            log.info("LLMService not configured. Using fallback keyword extraction for search queries.");
        } else {
            log.info("LLMService available. Will use AI-powered keyword extraction for search queries.");
        }

        if (null != llmService){
            log.info("LLM Service detected in KnowledgeGraphService");
            llmService.setLLMEndpoint( systemOptions.getIntegrationProxyUrl());
        }
        if (documentRepository == null) {
            log.info("DocumentRepository not configured. Knowledge graph answers will use only node metadata.");
        } else {
            log.info("DocumentRepository available. Knowledge graph will use full document content.");
        }
    }
    
    /**
     * Check if SurrealDB is available and enabled.
     */
    private boolean isSurrealDBAvailable() {
        return connectionProvider != null && connectionProvider.isEnabled();
    }

    /**
     * Creates or updates a node in the knowledge graph from a Document entity.
     * Also creates automatic relationships with existing documents based on:
     * - Shared tags
     * - Same author/creator
     */
    public KnowledgeGraphNode storeDocumentAsNode(Document document, String username) {
        if (!isSurrealDBAvailable()) {
            log.warn("SurrealDB not configured, skipping knowledge graph storage");
            return null;
        }
        
        try {
            // Create node representation
            KnowledgeGraphNode node = KnowledgeGraphNode.builder()
                    .id("document:" + document.getId())
                    .nodeType("document")
                    .name(document.getDocumentName())
                    .description(document.getSummary())
                    .entityId(document.getId())
                    .properties(buildDocumentProperties(document))
                    .markings(document.getMarkings())
                    .createdBy(username)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .tags(parseTags(document.getTags()))
                    .build();
            
            // Store in SurrealDB using UPSERT to handle existing records
            // UPSERT will create if not exists, or update if exists
            String escapedId = escapeRecordIdForRelate(node.getId());
            String query = "UPSERT " + escapedId + " CONTENT " + objectMapper.writeValueAsString(nodeToMap(node));
            invokeSurrealQuery(query);
            
            // Log provenance
            logProvenance("KNOWLEDGE_GRAPH_NODE_CREATED", username, 
                    "Created/updated knowledge graph node for document: " + document.getDocumentName());

            log.info("Stored document {} as knowledge graph node", document.getId());

            // Create automatic relationships with existing documents
            createAutomaticRelationships(node, document, username);

            return node;
        } catch (Exception e) {
            log.error("Failed to store document as knowledge graph node: {}", e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Creates a node in the knowledge graph.
     * If the node is a concept/procedure derived from a document, it inherits the document's markings.
     *
     * @param node The node to create
     * @param username The user creating the node
     * @return The created node, or null if creation failed
     */
    public KnowledgeGraphNode createNode(KnowledgeGraphNode node, String username) {
        if (!isSurrealDBAvailable()) {
            log.warn("SurrealDB not configured, skipping node creation");
            return null;
        }

        try {
            // Set created metadata if not already set
            if (node.getCreatedBy() == null) {
                node.setCreatedBy(username);
            }
            if (node.getCreatedAt() == null) {
                node.setCreatedAt(LocalDateTime.now());
            }
            node.setUpdatedAt(LocalDateTime.now());

            // If node has a sourceDocumentId, inherit markings from the source document
            if (node.getMarkings() == null && node.getProperties() != null) {
                Object sourceDocId = node.getProperties().get("sourceDocumentId");
                if (sourceDocId != null) {
                    String inheritedMarkings = getMarkingsFromSourceDocument(sourceDocId);
                    if (inheritedMarkings != null) {
                        node.setMarkings(inheritedMarkings);
                        log.debug("Inherited markings '{}' from source document {} for node {}",
                            inheritedMarkings, sourceDocId, node.getId());
                    }
                }
            }

            // Default to PUBLIC if no markings specified
            if (node.getMarkings() == null || node.getMarkings().trim().isEmpty()) {
                node.setMarkings("PUBLIC");
            }

            // Store in SurrealDB
            String escapedId = escapeRecordIdForRelate(node.getId());
            String query = "CREATE " + escapedId + " CONTENT " + objectMapper.writeValueAsString(nodeToMap(node));
            invokeSurrealQuery(query);

            // Log provenance
            logProvenance("KNOWLEDGE_GRAPH_NODE_CREATED", username,
                    "Created knowledge graph node: " + node.getName() + " (type: " + node.getNodeType() + ")");

            log.info("Created knowledge graph node: {} (type: {}, markings: {})",
                node.getId(), node.getNodeType(), node.getMarkings());

            return node;
        } catch (Exception e) {
            log.error("Failed to create knowledge graph node: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Get the markings from a source document node by its ID.
     * Used to inherit markings for concept/procedure nodes derived from documents.
     */
    private String getMarkingsFromSourceDocument(Object sourceDocId) {
        try {
            String docNodeId = "document:" + sourceDocId;
            String query = "SELECT markings FROM " + escapeRecordIdForRelate(docNodeId);
            List<Object> results = invokeSurrealQuery(query);

            if (results != null && !results.isEmpty()) {
                Object result = results.get(0);
                if (result instanceof Map) {
                    Map<String, Object> map = (Map<String, Object>) result;
                    Object markings = map.get("markings");
                    if (markings != null) {
                        return markings.toString();
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Could not get markings from source document {}: {}", sourceDocId, e.getMessage());
        }
        return null;
    }

    /**
     * Creates automatic relationships between a new document node and existing documents.
     * Relationships are created based on:
     * - SHARES_TAG: Documents that share one or more tags
     * - SAME_AUTHOR: Documents created by the same user
     * - SAME_MARKINGS: Documents with the same marking level
     * - SAME_TYPE: Documents of the same type (e.g., both are TSG, README, etc.)
     */
    private void createAutomaticRelationships(KnowledgeGraphNode newNode, Document document, String username) {
        try {
            String nodeId = newNode.getId();
            List<String> tags = newNode.getTags();
            String markings = document.getMarkings();
            String documentType = document.getDocumentType();
            String createdBy = document.getCreatedBy();

            log.info("Creating automatic relationships for document node: {}", nodeId);

            // Find and link documents with shared tags
            if (tags != null && !tags.isEmpty()) {
                for (String tag : tags) {
                    String tagQuery = String.format(
                        "SELECT * FROM document WHERE '%s' IN tags AND id != '%s' LIMIT 10",
                        escapeString(tag), nodeId
                    );
                    List<Object> relatedByTag = invokeSurrealQuery(tagQuery);
                    List<KnowledgeGraphNode> tagNodes = parseNodesToList(relatedByTag);

                    for (KnowledgeGraphNode relatedNode : tagNodes) {
                        createRelationship(nodeId, relatedNode.getId(), "SHARES_TAG", 0.7, username);
                        log.debug("Created SHARES_TAG relationship: {} -> {} (tag: {})",
                            nodeId, relatedNode.getId(), tag);
                    }
                }
            }

            // Find and link documents by same author
            if (createdBy != null && !createdBy.isEmpty()) {
                String authorQuery = String.format(
                    "SELECT * FROM document WHERE createdBy = '%s' AND id != '%s' LIMIT 20",
                    escapeString(createdBy), nodeId
                );
                List<Object> relatedByAuthor = invokeSurrealQuery(authorQuery);
                List<KnowledgeGraphNode> authorNodes = parseNodesToList(relatedByAuthor);

                for (KnowledgeGraphNode relatedNode : authorNodes) {
                    createRelationship(nodeId, relatedNode.getId(), "SAME_AUTHOR", 0.5, username);
                    log.debug("Created SAME_AUTHOR relationship: {} -> {}", nodeId, relatedNode.getId());
                }
            }

            // Find and link documents with same markings
            if (markings != null && !markings.isEmpty()) {
                String classQuery = String.format(
                    "SELECT * FROM document WHERE markings = '%s' AND id != '%s' LIMIT 20",
                    escapeString(markings), nodeId
                );
                List<Object> relatedByClass = invokeSurrealQuery(classQuery);
                List<KnowledgeGraphNode> classNodes = parseNodesToList(relatedByClass);

                for (KnowledgeGraphNode relatedNode : classNodes) {
                    createRelationship(nodeId, relatedNode.getId(), "SAME_MARKINGS", 0.3, username);
                    log.debug("Created SAME_MARKINGSSAME_MARKINGS relationship: {} -> {}", nodeId, relatedNode.getId());
                }
            }

            // Find and link documents of same type
            if (documentType != null && !documentType.isEmpty()) {
                String typeQuery = String.format(
                    "SELECT * FROM document WHERE properties.document_type = '%s' AND id != '%s' LIMIT 20",
                    escapeString(documentType), nodeId
                );
                List<Object> relatedByType = invokeSurrealQuery(typeQuery);
                List<KnowledgeGraphNode> typeNodes = parseNodesToList(relatedByType);

                for (KnowledgeGraphNode relatedNode : typeNodes) {
                    createRelationship(nodeId, relatedNode.getId(), "SAME_TYPE", 0.4, username);
                    log.debug("Created SAME_TYPE relationship: {} -> {}", nodeId, relatedNode.getId());
                }
            }

            log.info("Completed automatic relationship creation for document: {}", nodeId);

        } catch (Exception e) {
            log.error("Failed to create automatic relationships for document: {}", e.getMessage(), e);
        }
    }

    /**
     * Escape string for SurrealDB query to prevent injection
     */
    private String escapeString(String input) {
        if (input == null) return "";
        return input.replace("'", "\\'").replace("\"", "\\\"");
    }

    /**
     * Escape a record ID for use in SurrealDB RELATE statements.
     * Format: table:id or table:⟨complex-id⟩ for IDs with special characters.
     *
     * @param recordId The record ID like "document:5" or "concept:my-concept-name"
     * @return Properly escaped record ID for RELATE statement
     */
    private String escapeRecordIdForRelate(String recordId) {
        if (recordId == null) return "";

        int colonIndex = recordId.indexOf(':');
        if (colonIndex == -1) {
            // No colon, return as-is
            return recordId;
        }

        String table = recordId.substring(0, colonIndex);
        String id = recordId.substring(colonIndex + 1);

        // Check if already escaped with angle brackets - don't double-escape
        if (id.startsWith("⟨") && id.endsWith("⟩")) {
            return recordId;
        }

        // Check if ID contains special characters that need escaping
        // Special chars: hyphens, spaces, dots, etc.
        boolean needsEscaping = id.contains("-") || id.contains(" ") ||
                                id.contains(".") || id.contains("/") ||
                                !id.matches("[a-zA-Z0-9_]+");

        if (needsEscaping) {
            // Use SurrealDB's angle bracket syntax for complex IDs
            return table + ":⟨" + id + "⟩";
        }

        return recordId;
    }

    /**
     * Creates a relationship between two nodes in the knowledge graph.
     */
    public KnowledgeGraphRelationship createRelationship(
            String fromNodeId, 
            String toNodeId, 
            String relationshipType,
            Double weight,
            String username) {
        
        if (!isSurrealDBAvailable()) {
            log.warn("SurrealDB not configured, skipping relationship creation");
            return null;
        }
        
        try {
            KnowledgeGraphRelationship relationship = KnowledgeGraphRelationship.builder()
                    .id(fromNodeId + "->" + relationshipType + "->" + toNodeId)
                    .relationshipType(relationshipType)
                    .fromNode(fromNodeId)
                    .toNode(toNodeId)
                    .weight(weight != null ? weight : 1.0)
                    .createdBy(username)
                    .createdAt(LocalDateTime.now())
                    .build();
            
            // Use RELATE statement in SurrealDB
            // For RELATE, record IDs should not be wrapped in backticks
            // Format: RELATE table:id->RELATION_TYPE->table:id
            // For IDs with special chars, use the ⟨id⟩ syntax: table:⟨my-special-id⟩
            String escapedFromId = escapeRecordIdForRelate(fromNodeId);
            String escapedToId = escapeRecordIdForRelate(toNodeId);

            String query = String.format(
                    "RELATE %s->%s->%s CONTENT %s",
                    escapedFromId,
                    relationshipType,
                    escapedToId,
                    objectMapper.writeValueAsString(relationshipToMap(relationship))
            );
            invokeSurrealQuery(query);
            
            // Log provenance
            logProvenance("KNOWLEDGE_GRAPH_RELATIONSHIP_CREATED", username,
                    String.format("Created %s relationship from %s to %s", relationshipType, fromNodeId, toNodeId));
            
            log.debug("Created relationship: {} -> {} -> {}", fromNodeId, relationshipType, toNodeId);
            return relationship;
        } catch (Exception e) {
            log.error("Failed to create relationship: {}", e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Finds similar documents in the knowledge graph based on relationships.
     */
    public KnowledgeGraphQueryResponse findSimilarDocuments(Long documentId, String username, int limit) {
        if (!isSurrealDBAvailable()) {
            return KnowledgeGraphQueryResponse.builder()
                    .nodes(Collections.emptyList())
                    .relationships(Collections.emptyList())
                    .totalCount(0)
                    .build();
        }
        
        try {
            long startTime = System.currentTimeMillis();
            
            String nodeId = "document:" + documentId;
            
            // Query for similar documents using relationships
            String query = String.format(
                    "SELECT * FROM %s<-similar_to | similar_to->* LIMIT %d",
                    nodeId, limit
            );
            
            // Execute query
            List<Object> results = invokeSurrealQuery(query);
            
            // Parse results (simplified - actual parsing depends on SDK)
            List<KnowledgeGraphNode> nodes = parseNodesToList(results);
            
            // Filter by access control
            nodes = filterNodesByAccess(nodes, username);
            
            long executionTime = System.currentTimeMillis() - startTime;
            
            // Log provenance
            logProvenance("KNOWLEDGE_GRAPH_QUERY", username,
                    "Queried similar documents for document ID: " + documentId);
            
            return KnowledgeGraphQueryResponse.builder()
                    .nodes(nodes)
                    .relationships(Collections.emptyList())
                    .totalCount(nodes.size())
                    .executionTimeMs(executionTime)
                    .build();
        } catch (Exception e) {
            log.error("Failed to find similar documents: {}", e.getMessage(), e);
            return KnowledgeGraphQueryResponse.builder()
                    .nodes(Collections.emptyList())
                    .relationships(Collections.emptyList())
                    .totalCount(0)
                    .build();
        }
    }
    
    /**
     * Executes a custom knowledge graph query.
     * By default, does NOT fetch relationships to keep the query fast.
     * Use executeQueryWithRelationships() or the includeRelationships parameter for full graph data.
     */
    public KnowledgeGraphQueryResponse executeQuery(KnowledgeGraphQueryRequest request, String username) {
        return executeQuery(request, username, false);
    }

    /**
     * Executes a custom knowledge graph query with optional relationship fetching.
     * @param includeRelationships If true, also fetches relationships and connected nodes (slower)
     */
    public KnowledgeGraphQueryResponse executeQuery(KnowledgeGraphQueryRequest request, String username, boolean includeRelationships) {
        if (!isSurrealDBAvailable()) {
            return KnowledgeGraphQueryResponse.builder()
                    .nodes(Collections.emptyList())
                    .relationships(Collections.emptyList())
                    .totalCount(0)
                    .build();
        }
        
        try {
            long startTime = System.currentTimeMillis();
            
            String query = buildQuery(request);
            log.debug("Executing knowledge graph query: {}", query);
            
            List<Object> results = invokeSurrealQuery(query);
            
            List<KnowledgeGraphNode> nodes = parseNodesToList(results);
            nodes = filterNodesByAccess(nodes, username);
            
            List<KnowledgeGraphRelationship> relationships = Collections.emptyList();
            List<KnowledgeGraphNode> allNodes = new ArrayList<>(nodes);

            // Only fetch relationships if explicitly requested (to keep queries fast)
            if (includeRelationships) {
                relationships = fetchRelationshipsForNodes(nodes);

                // Also fetch the connected nodes (concepts, procedures) so the UI can render the full graph
                if (!relationships.isEmpty()) {
                    List<KnowledgeGraphNode> connectedNodes = fetchConnectedNodes(relationships, nodes);
                    allNodes.addAll(connectedNodes);
                    log.debug("Added {} connected nodes to response", connectedNodes.size());
                }
            }

            long executionTime = System.currentTimeMillis() - startTime;
            
            // Log provenance
            logProvenance("KNOWLEDGE_GRAPH_QUERY", username,
                    "Executed knowledge graph query: " + request.getQueryType());
            
            return KnowledgeGraphQueryResponse.builder()
                    .nodes(allNodes)
                    .relationships(relationships)
                    .totalCount(allNodes.size())
                    .executionTimeMs(executionTime)
                    .metadata("Query type: " + request.getQueryType())
                    .build();
        } catch (Exception e) {
            log.error("Failed to execute knowledge graph query: {}", e.getMessage(), e);
            return KnowledgeGraphQueryResponse.builder()
                    .nodes(Collections.emptyList())
                    .relationships(Collections.emptyList())
                    .totalCount(0)
                    .build();
        }
    }
    
    /**
     * Fetches relationships for a list of nodes.
     * Queries SurrealDB for all relationships where any of the nodes is the source (out) or target (in).
     */
    private List<KnowledgeGraphRelationship> fetchRelationshipsForNodes(List<KnowledgeGraphNode> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return Collections.emptyList();
        }

        try {
            // Build a list of node IDs
            List<String> nodeIds = nodes.stream()
                    .map(KnowledgeGraphNode::getId)
                    .filter(id -> id != null && !id.isEmpty())
                    .collect(Collectors.toList());

            if (nodeIds.isEmpty()) {
                return Collections.emptyList();
            }

            // Query for relationships where any node is source or target
            // In SurrealDB, relationships are stored in tables named after the relationship type
            // We query DISCUSSES, CONTAINS_PROCEDURE, RELATED_TO, etc.
            List<KnowledgeGraphRelationship> allRelationships = new ArrayList<>();

            String[] relationshipTypes = {"DISCUSSES", "CONTAINS_PROCEDURE", "RELATED_TO", "REFERENCES", "SUPERSEDES", "DEPENDS_ON"};

            for (String relType : relationshipTypes) {
                try {
                    String query = "SELECT * FROM " + relType + " LIMIT 500";
                    log.debug("Querying relationships of type {}: {}", relType, query);
                    List<Object> results = invokeSurrealQuery(query);

                    if (results != null && !results.isEmpty()) {
                        log.debug("Found {} {} relationships", results.size(), relType);
                        List<KnowledgeGraphRelationship> relationships = parseRelationshipsFromResults(results, relType);

                        // Filter to only include relationships connected to our nodes
                        for (KnowledgeGraphRelationship rel : relationships) {
                            String fromNode = rel.getFromNode();
                            String toNode = rel.getToNode();

                            // Check if either endpoint is in our node list
                            if ((fromNode != null && nodeIds.stream().anyMatch(id -> fromNode.contains(id) || id.contains(fromNode))) ||
                                (toNode != null && nodeIds.stream().anyMatch(id -> toNode.contains(id) || id.contains(toNode)))) {
                                allRelationships.add(rel);
                                log.trace("Including relationship: {} -> {} -> {}", fromNode, relType, toNode);
                            }
                        }
                    } else {
                        log.trace("No {} relationships found", relType);
                    }
                } catch (Exception e) {
                    // Relationship type table might not exist, skip it
                    log.trace("Could not query relationship type {}: {}", relType, e.getMessage());
                }
            }

            log.debug("Fetched {} relationships for {} nodes", allRelationships.size(), nodes.size());
            return allRelationships;

        } catch (Exception e) {
            log.error("Failed to fetch relationships for nodes: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * Parse relationship results from SurrealDB query response.
     */
    private List<KnowledgeGraphRelationship> parseRelationshipsFromResults(List<Object> results, String relType) {
        List<KnowledgeGraphRelationship> relationships = new ArrayList<>();

        try {
            for (Object result : results) {
                if (result instanceof Map) {
                    Map<String, Object> map = (Map<String, Object>) result;

                    // SurrealDB RELATE syntax: RELATE source->TYPE->target
                    // Stored as: 'out' = source node, 'in' = target node
                    String fromNode = map.get("out") != null ? map.get("out").toString() : null;
                    String toNode = map.get("in") != null ? map.get("in").toString() : null;
                    String id = map.get("id") != null ? map.get("id").toString() : null;

                    Double weight = null;
                    if (map.get("weight") != null) {
                        try {
                            weight = Double.valueOf(map.get("weight").toString());
                        } catch (NumberFormatException e) {
                            weight = 1.0;
                        }
                    }

                    if (fromNode != null && toNode != null) {
                        relationships.add(KnowledgeGraphRelationship.builder()
                                .id(id)
                                .fromNode(fromNode)
                                .toNode(toNode)
                                .relationshipType(relType)
                                .weight(weight != null ? weight : 1.0)
                                .build());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Error parsing relationship results: {}", e.getMessage());
        }

        return relationships;
    }

    /**
     * Fetches nodes that are connected via relationships but not in the original node list.
     * This allows the UI to render the full graph including concept and procedure nodes.
     */
    private List<KnowledgeGraphNode> fetchConnectedNodes(List<KnowledgeGraphRelationship> relationships, List<KnowledgeGraphNode> existingNodes) {
        if (relationships == null || relationships.isEmpty()) {
            return Collections.emptyList();
        }

        try {
            // Get existing node IDs
            Set<String> existingNodeIds = existingNodes.stream()
                    .map(KnowledgeGraphNode::getId)
                    .filter(id -> id != null)
                    .collect(Collectors.toSet());

            // Find node IDs from relationships that we don't already have
            Set<String> missingNodeIds = new HashSet<>();
            for (KnowledgeGraphRelationship rel : relationships) {
                if (rel.getFromNode() != null && !existingNodeIds.contains(rel.getFromNode())) {
                    missingNodeIds.add(rel.getFromNode());
                }
                if (rel.getToNode() != null && !existingNodeIds.contains(rel.getToNode())) {
                    missingNodeIds.add(rel.getToNode());
                }
            }

            if (missingNodeIds.isEmpty()) {
                return Collections.emptyList();
            }

            log.debug("Fetching {} connected nodes: {}", missingNodeIds.size(), missingNodeIds);

            // Query for each missing node
            List<KnowledgeGraphNode> connectedNodes = new ArrayList<>();
            for (String nodeId : missingNodeIds) {
                try {
                    String escapedNodeId = escapeRecordIdForRelate(nodeId);
                    String query = "SELECT * FROM " + escapedNodeId;
                    List<Object> results = invokeSurrealQuery(query);

                    if (results != null && !results.isEmpty()) {
                        List<KnowledgeGraphNode> nodes = parseNodesToList(results);
                        connectedNodes.addAll(nodes);
                    }
                } catch (Exception e) {
                    log.trace("Could not fetch connected node {}: {}", nodeId, e.getMessage());
                }
            }

            log.debug("Fetched {} connected nodes", connectedNodes.size());
            return connectedNodes;

        } catch (Exception e) {
            log.error("Failed to fetch connected nodes: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * Public method to fetch relationships for a list of node IDs.
     * Designed to be called async from the UI after the main query returns.
     * Returns relationships and connected nodes with resolved names.
     */
    public Map<String, Object> getRelationshipsForNodeIds(List<String> nodeIds, String username) {
        Map<String, Object> result = new HashMap<>();

        if (!isSurrealDBAvailable() || nodeIds == null || nodeIds.isEmpty()) {
            result.put("relationships", Collections.emptyList());
            result.put("connectedNodes", Collections.emptyList());
            result.put("relationshipCount", 0);
            return result;
        }

        try {
            // Create minimal node objects for the fetch method
            List<KnowledgeGraphNode> nodes = nodeIds.stream()
                .map(id -> KnowledgeGraphNode.builder().id(id).build())
                .collect(Collectors.toList());

            // Fetch relationships
            List<KnowledgeGraphRelationship> relationships = fetchRelationshipsForNodes(nodes);

            // Fetch connected nodes
            List<KnowledgeGraphNode> connectedNodes = Collections.emptyList();
            if (!relationships.isEmpty()) {
                connectedNodes = fetchConnectedNodes(relationships, nodes);
            }

            // Build a map of node ID -> name for the UI to resolve names
            Map<String, String> nodeNames = new HashMap<>();
            for (KnowledgeGraphNode node : connectedNodes) {
                if (node.getId() != null && node.getName() != null) {
                    nodeNames.put(node.getId(), node.getName());
                }
            }

            // Enrich relationships with node names for easier UI display
            List<Map<String, Object>> enrichedRelationships = new ArrayList<>();
            for (KnowledgeGraphRelationship rel : relationships) {
                Map<String, Object> enrichedRel = new HashMap<>();
                enrichedRel.put("id", rel.getId());
                enrichedRel.put("fromNode", rel.getFromNode());
                enrichedRel.put("toNode", rel.getToNode());
                enrichedRel.put("relationshipType", rel.getRelationshipType());
                enrichedRel.put("weight", rel.getWeight());

                // Add resolved names
                String fromName = nodeNames.getOrDefault(rel.getFromNode(), extractNameFromId(rel.getFromNode()));
                String toName = nodeNames.getOrDefault(rel.getToNode(), extractNameFromId(rel.getToNode()));
                enrichedRel.put("fromNodeName", fromName);
                enrichedRel.put("toNodeName", toName);

                enrichedRelationships.add(enrichedRel);
            }

            result.put("relationships", enrichedRelationships);
            result.put("connectedNodes", connectedNodes);
            result.put("relationshipCount", relationships.size());
            result.put("nodeNames", nodeNames);

            log.info("Fetched {} relationships and {} connected nodes for {} input nodes",
                relationships.size(), connectedNodes.size(), nodeIds.size());

            return result;

        } catch (Exception e) {
            log.error("Failed to get relationships for node IDs: {}", e.getMessage(), e);
            result.put("relationships", Collections.emptyList());
            result.put("connectedNodes", Collections.emptyList());
            result.put("relationshipCount", 0);
            result.put("error", e.getMessage());
            return result;
        }
    }

    /**
     * Extract a human-readable name from a node ID.
     * e.g., "concept:⟨unit-testing⟩" -> "unit-testing"
     */
    private String extractNameFromId(String nodeId) {
        if (nodeId == null) return "";

        int colonIndex = nodeId.indexOf(':');
        if (colonIndex == -1) return nodeId;

        String id = nodeId.substring(colonIndex + 1);

        // Remove angle brackets if present
        if (id.startsWith("⟨") && id.endsWith("⟩")) {
            id = id.substring(1, id.length() - 1);
        }

        // Convert hyphens to spaces and capitalize words
        return id.replace("-", " ");
    }

    /**
     * Deletes a node from the knowledge graph.
     */
    public boolean deleteNode(String nodeId, String username) {
        if (!isSurrealDBAvailable()) {
            return false;
        }
        
        try {
            String query = "DELETE " + nodeId;
            invokeSurrealQuery(query);
            
            // Log provenance
            logProvenance("KNOWLEDGE_GRAPH_NODE_DELETED", username,
                    "Deleted knowledge graph node: " + nodeId);
            
            log.debug("Deleted knowledge graph node: {}", nodeId);
            return true;
        } catch (Exception e) {
            log.error("Failed to delete node: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Updates properties of an existing node in the knowledge graph.
     * Merges the provided properties with the existing node properties.
     */
    public boolean updateNodeProperties(String nodeId, Map<String, Object> properties, String username) {
        if (!isSurrealDBAvailable()) {
            return false;
        }

        try {
            // Build the MERGE query to update properties
            // This will merge the new properties with existing ones
            // Use proper record ID escaping for special characters
            String escapedNodeId = escapeRecordIdForRelate(nodeId);
            String propertiesJson = objectMapper.writeValueAsString(properties);
            String query = "UPDATE " + escapedNodeId + " MERGE " + propertiesJson;

            log.info("Executing node properties update for {}: {}", nodeId, query);
            log.debug("Properties JSON: {}", propertiesJson);

            List<Object> updateResult = invokeSurrealQuery(query);
            log.info("Update query returned {} results", updateResult.size());

            // Verify the update by querying the node
            String verifyQuery = "SELECT * FROM " + escapedNodeId;
            List<Object> verifyResult = invokeSurrealQuery(verifyQuery);
            log.info("Verification query returned {} results", verifyResult.size());

            if (verifyResult.isEmpty()) {
                log.error("Node {} not found after update! Update may have failed or corrupted the node", nodeId);
                return false;
            }

            // Log provenance
            logProvenance("KNOWLEDGE_GRAPH_NODE_UPDATED", username,
                    "Updated knowledge graph node properties: " + nodeId + " with " + properties.size() + " properties");

            log.info("Successfully updated knowledge graph node: {} with {} properties", nodeId, properties.size());
            return true;
        } catch (Exception e) {
            log.error("Failed to update node properties for {}: {}", nodeId, e.getMessage(), e);
            return false;
        }
    }

    // Helper methods
    
    private Map<String, Object> buildDocumentProperties(Document document) {
        Map<String, Object> properties = new HashMap<>();
        properties.put("document_type", document.getDocumentType());
        properties.put("file_size", document.getFileSize());
        properties.put("checksum", document.getChecksum());
        properties.put("created_at", document.getCreatedAt());
        if (document.getMetadata() != null && !document.getMetadata().isNull()) {
            try {
                // Convert JsonNode to Map
                Map<String, Object> metadataMap = objectMapper.convertValue(
                        document.getMetadata(), 
                        objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class));
                properties.putAll(metadataMap);
            } catch (Exception e) {
                log.warn("Failed to convert metadata to map: {}", e.getMessage());
            }
        }
        return properties;
    }
    
    /**
     * Get all nodes in the knowledge graph (for debugging/admin purposes).
     */
    public List<KnowledgeGraphNode> getAllNodes(int limit) {
        if (!isSurrealDBAvailable()) {
            log.warn("SurrealDB not configured");
            return Collections.emptyList();
        }

        try {
            String query = "SELECT * FROM document LIMIT " + limit;
            log.info("Executing getAllNodes query: {}", query);
            List<Object> results = invokeSurrealQuery(query);
            List<KnowledgeGraphNode> nodes = parseNodesToList(results);
            log.info("getAllNodes returned {} nodes", nodes.size());
            return nodes;
        } catch (Exception e) {
            log.error("Failed to get all nodes: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * Get knowledge graph statistics.
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("enabled", isSurrealDBAvailable());
        stats.put("database", "surrealdb");

        if (!isSurrealDBAvailable()) {
            stats.put("nodeCount", 0);
            stats.put("status", "disconnected");
            return stats;
        }

        try {
            // Count documents
            String countQuery = "SELECT count() FROM document GROUP ALL";
            log.debug("Executing count query: {}", countQuery);
            List<Object> countResult = invokeSurrealQuery(countQuery);

            int nodeCount = 0;
            if (!countResult.isEmpty()) {
                Object first = countResult.get(0);
                if (first instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> countMap = (Map<String, Object>) first;
                    Object count = countMap.get("count");
                    if (count instanceof Number) {
                        nodeCount = ((Number) count).intValue();
                    }
                }
            }
            stats.put("nodeCount", nodeCount);
            stats.put("status", "connected");

            // Also try a simple info query
            String infoQuery = "INFO FOR DB";
            log.debug("Executing info query: {}", infoQuery);
            List<Object> infoResult = invokeSurrealQuery(infoQuery);
            if (!infoResult.isEmpty()) {
                stats.put("dbInfo", infoResult.get(0));
            }

        } catch (Exception e) {
            log.error("Failed to get statistics: {}", e.getMessage(), e);
            stats.put("status", "error");
            stats.put("error", e.getMessage());
        }

        return stats;
    }

    private List<String> parseTags(String tags) {
        if (tags == null || tags.isEmpty()) {
            return Collections.emptyList();
        }
        return Arrays.stream(tags.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }
    
    private Map<String, Object> nodeToMap(KnowledgeGraphNode node) {
        Map<String, Object> map = new HashMap<>();
        map.put("nodeType", node.getNodeType());
        map.put("name", node.getName());
        map.put("description", node.getDescription());
        map.put("entityId", node.getEntityId());
        map.put("properties", node.getProperties());
        map.put("markings", node.getMarkings());
        map.put("createdBy", node.getCreatedBy());
        map.put("createdAt", node.getCreatedAt());
        map.put("updatedAt", node.getUpdatedAt());
        map.put("tags", node.getTags());
        return map;
    }
    
    private Map<String, Object> relationshipToMap(KnowledgeGraphRelationship relationship) {
        Map<String, Object> map = new HashMap<>();
        map.put("relationshipType", relationship.getRelationshipType());
        map.put("weight", relationship.getWeight());
        map.put("createdBy", relationship.getCreatedBy());
        map.put("createdAt", relationship.getCreatedAt());
        map.put("description", relationship.getDescription());
        if (relationship.getProperties() != null) {
            map.putAll(relationship.getProperties());
        }
        return map;
    }
    
    private String buildQuery(KnowledgeGraphQueryRequest request) {
        int limit = request.getLimit() != null ? request.getLimit() : 50;
        
        switch (request.getQueryType()) {
            case SEARCH:
                return buildSearchQuery(request.getSearchText(), request.getNodeTypes(), limit);
            case NEIGHBORS:
                return buildNeighborsQuery(request.getStartNodeId(), limit);
            case TRAVERSE:
                int maxDepth = request.getMaxDepth() != null ? request.getMaxDepth() : 2;
                return buildTraversalQuery(request.getStartNodeId(), maxDepth, limit);
            case PATH:
                return buildPathQuery(request.getStartNodeId(), request.getTargetNodeId());
            case SUBGRAPH:
                maxDepth = request.getMaxDepth() != null ? request.getMaxDepth() : 2;
                return buildSubgraphQuery(request.getStartNodeId(), maxDepth, limit);
            default:
                return "SELECT * FROM document LIMIT " + limit;
        }
    }
    
    private String buildSearchQuery(String searchText, List<String> nodeTypes, int limit) {
        // Handle null or empty search text - return all documents
        if (searchText == null || searchText.trim().isEmpty()) {
            log.debug("Empty search text, returning all documents");
            StringBuilder query = new StringBuilder("SELECT * FROM document");

            if (nodeTypes != null && !nodeTypes.isEmpty()) {
                query.append(" WHERE nodeType IN [");
                query.append(nodeTypes.stream()
                        .map(t -> "'" + t + "'")
                        .collect(Collectors.joining(", ")));
                query.append("]");
            }

            query.append(" LIMIT ").append(limit);
            return query.toString();
        }

        // Extract keywords from search text using LLM if available, otherwise use fallback
        List<String> keywords = extractKeywordsWithLLM(searchText);

        if (keywords.isEmpty()) {
            // If still no keywords, use the original text as a single keyword (but we know it's not empty)
            keywords = List.of(searchText.trim());
        }

        log.debug("Extracted {} keywords from search text: {}", keywords.size(), keywords);

        // Build OR query for each keyword
        StringBuilder query = new StringBuilder("SELECT * FROM document WHERE (");

        List<String> conditions = new ArrayList<>();
        for (String keyword : keywords) {
            if (keyword != null && !keyword.trim().isEmpty()) {
                String escapedKeyword = escapeString(keyword).toLowerCase();
                conditions.add("string::lowercase(name) CONTAINS '" + escapedKeyword + "'");
                conditions.add("string::lowercase(description) CONTAINS '" + escapedKeyword + "'");
            }
        }

        // If no valid conditions, return all documents
        if (conditions.isEmpty()) {
            log.debug("No valid keywords found, returning all documents");
            query = new StringBuilder("SELECT * FROM document");

            if (nodeTypes != null && !nodeTypes.isEmpty()) {
                query.append(" WHERE nodeType IN [");
                query.append(nodeTypes.stream()
                        .map(t -> "'" + t + "'")
                        .collect(Collectors.joining(", ")));
                query.append("]");
            }

            query.append(" LIMIT ").append(limit);
            return query.toString();
        }

        query.append(String.join(" OR ", conditions));
        query.append(")");

        if (nodeTypes != null && !nodeTypes.isEmpty()) {
            query.append(" AND nodeType IN [");
            query.append(nodeTypes.stream()
                    .map(t -> "'" + t + "'")
                    .collect(Collectors.joining(", ")));
            query.append("]");
        }
        
        query.append(" LIMIT ").append(limit);
        log.debug("Built search query: {}", query);
        return query.toString();
    }
    
    private String buildNeighborsQuery(String startNodeId, int limit) {
        if (startNodeId == null || startNodeId.trim().isEmpty()) {
            log.warn("NEIGHBORS query requires startNodeId but it was null or empty");
            return "SELECT * FROM document LIMIT " + limit;
        }
        return String.format(
                "SELECT * FROM %s<-* | *->* LIMIT %d",
                startNodeId, limit
        );
    }
    
    private String buildTraversalQuery(String startNodeId, int maxDepth, int limit) {
        if (startNodeId == null || startNodeId.trim().isEmpty()) {
            log.warn("TRAVERSE query requires startNodeId but it was null or empty");
            return "SELECT * FROM document LIMIT " + limit;
        }
        return String.format(
                "SELECT * FROM %s->*..%d LIMIT %d",
                startNodeId, maxDepth, limit
        );
    }
    
    private String buildPathQuery(String startNodeId, String targetNodeId) {
        if (startNodeId == null || startNodeId.trim().isEmpty() ||
            targetNodeId == null || targetNodeId.trim().isEmpty()) {
            log.warn("PATH query requires both startNodeId and targetNodeId but one or both were null or empty");
            return "SELECT * FROM document LIMIT 50";
        }
        return String.format(
                "SELECT * FROM %s<->*<->%s",
                startNodeId, targetNodeId
        );
    }
    
    private String buildSubgraphQuery(String startNodeId, int maxDepth, int limit) {
        if (startNodeId == null || startNodeId.trim().isEmpty()) {
            log.warn("SUBGRAPH query requires startNodeId but it was null or empty");
            return "SELECT * FROM document LIMIT " + limit;
        }
        return String.format(
                "SELECT * FROM %s<->*..%d LIMIT %d",
                startNodeId, maxDepth, limit
        );
    }
    
    /**
     * Extract keywords using LLM if available, otherwise fall back to simple extraction.
     * The LLM can understand the intent of natural language questions and extract relevant search terms.
     * Only uses LLM for complex queries (questions, multiple words, etc.)
     */
    private List<String> extractKeywordsWithLLM(String searchText) {
        if (llmService == null) {
            log.debug("LLM service not available, using simple keyword extraction");
            return extractKeywords(searchText);
        }

        if (!shouldUseLLMForKeywordExtraction(searchText)) {
            log.debug("Query is simple, using basic keyword extraction without LLM");
            return extractKeywords(searchText);
        }

        try {
            log.debug("Using LLM to extract keywords from: {}", searchText);

            String prompt = String.format(
                "Extract 3-5 relevant search keywords from this question for searching a document database. " +
                "Return ONLY the keywords as a comma-separated list, nothing else.\n\n" +
                "Question: %s\n\n" +
                "Keywords:",
                searchText
            );

            TokenDTO tokenDTO = TokenDTO.builder().communicationId(UUID.randomUUID().toString()).build();
            Map<String, Object> llmRequest = Map.of(
                "model", "gpt-4.1",
                "messages", List.of(Map.of("role", "user", "content", prompt)),
                "temperature", 0.3,
                "max_tokens", 500
            );

            String response = llmService.askQuestion(tokenDTO, llmRequest);

            if (response != null && !response.trim().isEmpty()) {
                try {
                    com.fasterxml.jackson.databind.JsonNode jsonResponse = objectMapper.readTree(response);
                    String extractedText = null;

                    if (jsonResponse.isArray() && jsonResponse.size() > 0) {
                        com.fasterxml.jackson.databind.JsonNode firstItem = jsonResponse.get(0);
                        if (firstItem.has("output") && firstItem.get("output").isArray()) {
                            com.fasterxml.jackson.databind.JsonNode output = firstItem.get("output").get(0);
                            if (output.has("content") && output.get("content").isArray()) {
                                com.fasterxml.jackson.databind.JsonNode content = output.get("content").get(0);
                                if (content.has("text")) {
                                    extractedText = content.get("text").asText();
                                }
                            }
                        }
                    } else if (jsonResponse.has("choices") && jsonResponse.get("choices").isArray()) {
                        com.fasterxml.jackson.databind.JsonNode choices = jsonResponse.get("choices").get(0);
                        if (choices.has("message") && choices.get("message").has("content")) {
                            extractedText = choices.get("message").get("content").asText();
                        }
                    }

                    if (extractedText != null && !extractedText.trim().isEmpty()) {
                        String[] keywordsArray = extractedText.split(",");
                        List<String> keywords = Arrays.stream(keywordsArray)
                                .map(String::trim)
                                .filter(s -> !s.isEmpty())
                                .collect(Collectors.toList());
                        log.debug("LLM extracted keywords: {}", keywords);
                        return keywords;
                    }
                } catch (Exception parseEx) {
                    log.warn("Failed to parse LLM response JSON: {}", parseEx.getMessage());
                }

                log.warn("Could not extract keywords from LLM response, falling back to simple extraction");
                return extractKeywords(searchText);
            } else {
                log.warn("LLM returned empty response, falling back to simple extraction");
                return extractKeywords(searchText);
            }

        } catch (Exception | ZtatException e) {
            log.warn("Failed to extract keywords using LLM: {}, falling back to simple extraction", e.getMessage());
            return extractKeywords(searchText);
        }
    }

    /**
     * Determine if we should use LLM for keyword extraction.
     * Only use LLM for complex queries that would benefit from natural language understanding.
     */
    private boolean shouldUseLLMForKeywordExtraction(String searchText) {
        if (searchText == null || searchText.trim().isEmpty()) {
            return false;
        }

        String trimmed = searchText.trim();

        // Don't use LLM for very short queries (1-3 characters)
        if (trimmed.length() <= 3) {
            return false;
        }

        // Don't use LLM for single word queries
        if (!trimmed.contains(" ")) {
            return false;
        }

        // Use LLM for questions (contains question words or ends with ?)
        String lower = trimmed.toLowerCase();
        if (lower.endsWith("?") ||
            lower.startsWith("what ") || lower.startsWith("where ") ||
            lower.startsWith("when ") || lower.startsWith("who ") ||
            lower.startsWith("why ") || lower.startsWith("how ") ||
            lower.startsWith("find ") || lower.startsWith("show ") ||
            lower.startsWith("search ") || lower.startsWith("list ")) {
            return true;
        }

        // Use LLM for longer queries (more than 5 words)
        if (trimmed.split("\\s+").length > 5) {
            return true;
        }

        // Default to simple extraction for everything else
        return false;
    }

    /**
     * Extract meaningful keywords from search text by removing common stop words.
     * This allows natural language questions to be converted to effective search terms.
     */
    private List<String> extractKeywords(String searchText) {
        if (searchText == null || searchText.trim().isEmpty()) {
            return Collections.emptyList();
        }

        // Common stop words to exclude from search
        Set<String> stopWords = Set.of(
            "a", "an", "and", "are", "as", "at", "be", "by", "for", "from", "has", "he",
            "in", "is", "it", "its", "of", "on", "that", "the", "to", "was", "will", "with",
            "what", "when", "where", "who", "why", "how", "do", "does", "did", "we", "you",
            "i", "me", "my", "have", "can", "could", "would", "should", "about", "kind", "type"
        );

        // Tokenize and filter
        return Arrays.stream(searchText.toLowerCase().split("\\s+"))
            .map(word -> word.replaceAll("[^a-z0-9-]", "")) // Remove punctuation but keep hyphens
            .filter(word -> word.length() > 2) // Min 3 characters
            .filter(word -> !stopWords.contains(word))
            .distinct()
            .limit(10) // Limit to 10 keywords to avoid overly complex queries
            .collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    private List<KnowledgeGraphNode> parseNodesToList(List<Object> results) {
        List<KnowledgeGraphNode> nodes = new ArrayList<>();
        if (results == null || results.isEmpty()) {
            log.debug("No results to parse");
            return nodes;
        }

        log.debug("Parsing {} results to KnowledgeGraphNode list", results.size());

        for (Object result : results) {
            try {
                KnowledgeGraphNode node;

                if (result instanceof Map) {
                    // Handle Map response from SurrealDB
                    Map<String, Object> map = (Map<String, Object>) result;
                    log.debug("Parsing Map result with keys: {}", map.keySet());

                    node = KnowledgeGraphNode.builder()
                            .id(getStringValue(map, "id"))
                            .nodeType(getStringValue(map, "nodeType"))
                            .name(getStringValue(map, "name"))
                            .description(getStringValue(map, "description"))
                            .entityId(getLongValue(map, "entityId"))
                            .markings(getStringValue(map, "markings"))
                            .createdBy(getStringValue(map, "createdBy"))
                            .properties((Map<String, Object>) map.get("properties"))
                            .tags(getStringList(map, "tags"))
                            .build();
                } else if (result instanceof KnowledgeGraphNode) {
                    // Already the correct type
                    node = (KnowledgeGraphNode) result;
                } else {
                    // Try Jackson conversion as fallback
                    log.debug("Attempting Jackson conversion for type: {}", result.getClass().getName());
                    node = objectMapper.convertValue(result, KnowledgeGraphNode.class);
                }

                if (node != null && node.getId() != null) {
                    nodes.add(node);
                    log.debug("Successfully parsed node: id={}, name={}", node.getId(), node.getName());
                }
            } catch (Exception e) {
                log.warn("Failed to parse node from result (type: {}): {}",
                    result != null ? result.getClass().getName() : "null", e.getMessage());
            }
        }

        log.info("Parsed {} nodes from {} results", nodes.size(), results.size());
        return nodes;
    }
    
    private String getStringValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }

    private Long getLongValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof Number) return ((Number) value).longValue();
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> getStringList(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return new ArrayList<>();
        if (value instanceof List) {
            return ((List<?>) value).stream()
                    .map(Object::toString)
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }

    private List<KnowledgeGraphNode> filterNodesByAccess(List<KnowledgeGraphNode> nodes, String username) {
        return nodes.stream()
                .filter(node -> accessControlService.canAccessNode(node, username))
                .collect(Collectors.toList());
    }
    
    private void logProvenance(String eventType, String username, String description) {
        if (provenanceLogger == null) {
            log.debug("ProvenanceLogger not available, skipping knowledge graph event logging");
            return;
        }
        try {
            // Use UNKNOWN for custom event types not in the enum
            ProvenanceEvent event = ProvenanceEvent.builder()
                    .eventType(ProvenanceEvent.EventType.UNKNOWN)
                    .actor(username)
                    .timestamp(java.time.Instant.now())
                    .outputSummary(eventType + ": " + description)
                    .build();
            provenanceLogger.log(event);
        } catch (Exception e) {
            log.error("Failed to log provenance event: {}", e.getMessage());
        }
    }
    
    /**
     * Invoke a SurrealDB query using the SDK.
     * Creates a fresh connection for each query to respect dynamic configuration.
     * Returns results as a list for processing.
     */
    @SuppressWarnings("unchecked")
    private List<Object> invokeSurrealQuery(String query) {
        if (!isSurrealDBAvailable()) {
            log.info("SurrealDB not configured, cannot execute query");
            return Collections.emptyList();
        }
        
        Surreal surrealDB = null;
        try {
            log.debug("Executing SurrealDB query: {}", query);

            // Get a fresh connection for this query
            surrealDB = connectionProvider.getConnection();
            if (surrealDB == null) {
                log.warn("Failed to get SurrealDB connection");
                return Collections.emptyList();
            }

            // Use the SurrealDB SDK query method which returns a Response object
            Response response = surrealDB.query(query);

            log.debug("SurrealDB response size: {}", response.size());

            // Parse the response - Response.take(index) gets result set at index
            if (response.size() > 0) {
                try {
                    // Get the raw Value object first to see what we have
                    Value value = response.take(0);
                    String valueStr = value.toString();
                    log.debug("SurrealDB raw value: {}", valueStr);

                    // The Value.toString() returns the SurrealDB format like:
                    // [{ field: 'value', ... }, { field: 'value', ... }]
                    // We need to convert this to valid JSON and parse it

                    if (valueStr == null || valueStr.equals("[]") || valueStr.equals("NONE")) {
                        log.debug("SurrealDB returned empty or NONE result");
                        return Collections.emptyList();
                    }

                    // Convert SurrealDB format to JSON and parse
                    if (valueStr.startsWith("[")) {
                        String jsonContent = convertSurrealResponseToJson(valueStr);
                        log.debug("Converted to JSON: {}", jsonContent);

                        List<Object> parsed = objectMapper.readValue(jsonContent,
                            objectMapper.getTypeFactory().constructCollectionType(List.class, Object.class));

                        if (parsed != null && !parsed.isEmpty()) {
                            log.info("SurrealDB query returned {} results", parsed.size());
                            return parsed;
                        }
                    } else if (valueStr.startsWith("{")) {
                        // Single object result
                        String jsonContent = convertSurrealResponseToJson(valueStr);
                        Object parsed = objectMapper.readValue(jsonContent, Object.class);
                        if (parsed != null) {
                            log.info("SurrealDB query returned single result");
                            return Collections.singletonList(parsed);
                        }
                    }

                    log.debug("SurrealDB returned unrecognized format: {}", valueStr);
                    return Collections.emptyList();

                } catch (Exception e) {
                    log.error("Failed to parse SurrealDB response: {}", e.getMessage(), e);
                    return Collections.emptyList();
                }
            } else {
                log.debug("SurrealDB response has no result sets");
            }
            
            log.debug("Executed SurrealDB query successfully, no results returned");
            return Collections.emptyList();
        } catch (Exception e) {
            log.error("Failed to execute SurrealDB query: {}", e.getMessage(), e);
            return Collections.emptyList();
        } finally {
            // Always close the connection after use
            if (surrealDB != null) {
                try {
                    surrealDB.close();
                } catch (Exception e) {
                    log.warn("Error closing SurrealDB connection: {}", e.getMessage());
                }
            }
        }
    }

    /**
     * Parse results from SurrealDB SDK error message.
     * The SDK sometimes throws "Unexpected value: [data...]" when it can't deserialize,
     * but the actual data is in the error message.
     */
    private List<Object> parseResultsFromErrorMessage(String errorMessage) {
        try {
            log.debug("Parsing SurrealDB results from error message");
            String jsonContent = errorMessage.substring("Unexpected value: ".length());
            jsonContent = convertSurrealResponseToJson(jsonContent);

            List<Object> parsed = objectMapper.readValue(jsonContent,
                objectMapper.getTypeFactory().constructCollectionType(List.class, Object.class));

            if (parsed != null && !parsed.isEmpty()) {
                log.info("SurrealDB query returned {} results (parsed from response)", parsed.size());
                return parsed;
            }
        } catch (Exception parseEx) {
            log.warn("Failed to parse SurrealDB response from error message: {}", parseEx.getMessage());
        }
        return Collections.emptyList();
    }

    /**
     * Convert SurrealDB's Value.toString() format to valid JSON.
     *
     * SurrealDB returns data like:
     *   [{ markings: 'PRIVATE', id: document:2, name: 'test' }]
     *
     * We need to convert to:
     *   [{ "markings": "PRIVATE", "id": "document:2", "name": "test" }]
     */
    private String convertSurrealResponseToJson(String surrealResponse) {
        if (surrealResponse == null || surrealResponse.isEmpty()) {
            return "[]";
        }

        StringBuilder result = new StringBuilder();
        int i = 0;
        int len = surrealResponse.length();

        while (i < len) {
            char c = surrealResponse.charAt(i);

            if (c == '"') {
                // Already a double-quoted string - copy it as-is (it's already valid JSON)
                result.append('"');
                i++;
                while (i < len) {
                    char ch = surrealResponse.charAt(i);

                    // Handle escaped characters
                    if (ch == '\\' && i + 1 < len) {
                        result.append(ch).append(surrealResponse.charAt(i + 1));
                        i += 2;
                        continue;
                    }

                    // End of string
                    if (ch == '"') {
                        result.append('"');
                        i++;
                        break;
                    }

                    result.append(ch);
                    i++;
                }
            } else if (c == '\'') {
                // Convert single-quoted string to double-quoted
                result.append('"');
                i++;
                while (i < len) {
                    char ch = surrealResponse.charAt(i);

                    // Check for escaped single quote
                    if (ch == '\\' && i + 1 < len && surrealResponse.charAt(i + 1) == '\'') {
                        result.append("\\'");
                        i += 2;
                        continue;
                    }

                    // End of string - found unescaped single quote
                    if (ch == '\'') {
                        break;
                    }

                    // Escape special JSON characters
                    if (ch == '"') {
                        result.append('\\').append('"');
                    } else if (ch == '\\') {
                        result.append('\\').append('\\');
                    } else if (ch == '\n') {
                        result.append('\\').append('n');
                    } else if (ch == '\r') {
                        result.append('\\').append('r');
                    } else if (ch == '\t') {
                        result.append('\\').append('t');
                    } else if (ch == '\b') {
                        result.append('\\').append('b');
                    } else if (ch == '\f') {
                        result.append('\\').append('f');
                    } else if (ch < 32 || ch == 127) {
                        // Escape other control characters
                        result.append(String.format("\\u%04x", (int) ch));
                    } else {
                        result.append(ch);
                    }

                    i++;
                }
                result.append('"');
                i++; // skip closing quote
            } else if (c == '{' || c == ',') {
                result.append(c);
                i++;
                // Skip whitespace
                while (i < len && Character.isWhitespace(surrealResponse.charAt(i))) {
                    result.append(surrealResponse.charAt(i));
                    i++;
                }
                // Check if next token is an unquoted property name
                if (i < len && (Character.isLetter(surrealResponse.charAt(i)) || surrealResponse.charAt(i) == '_')) {
                    // Find the property name (ends at ':')
                    int start = i;
                    while (i < len && surrealResponse.charAt(i) != ':' && surrealResponse.charAt(i) != '}') {
                        i++;
                    }
                    String propName = surrealResponse.substring(start, i).trim();
                    result.append('"').append(propName).append('"');
                }
            } else if (c == ':') {
                result.append(c);
                i++;
                // Skip whitespace after colon
                while (i < len && Character.isWhitespace(surrealResponse.charAt(i))) {
                    result.append(surrealResponse.charAt(i));
                    i++;
                }
                // Check if value is unquoted (not starting with ', ", [, {, or digit, or true/false/null)
                if (i < len) {
                    char nextChar = surrealResponse.charAt(i);

                    // Handle numeric values (may have type suffix like 0.8f, 123i64, etc.)
                    if (Character.isDigit(nextChar) || nextChar == '-') {
                        int start = i;
                        // Read the numeric part (digits, dots, minus, e/E for scientific notation)
                        while (i < len && (Character.isDigit(surrealResponse.charAt(i)) ||
                                          surrealResponse.charAt(i) == '.' ||
                                          surrealResponse.charAt(i) == '-' ||
                                          surrealResponse.charAt(i) == 'e' ||
                                          surrealResponse.charAt(i) == 'E' ||
                                          surrealResponse.charAt(i) == '+')) {
                            i++;
                        }
                        // Skip any type suffix (f, d, i64, dec, etc.)
                        while (i < len && (Character.isLetter(surrealResponse.charAt(i)) ||
                                          Character.isDigit(surrealResponse.charAt(i)))) {
                            i++;
                        }
                        // Extract just the numeric part (without suffix)
                        String numStr = surrealResponse.substring(start, i).trim();
                        // Remove any trailing type suffix
                        numStr = numStr.replaceAll("[a-zA-Z]+[0-9]*$", "");
                        result.append(numStr);
                    } else if (nextChar != '\'' && nextChar != '"' && nextChar != '[' && nextChar != '{') {
                        // Could be: true, false, null, NULL, or an unquoted value like document:2
                        int start = i;
                        while (i < len && !isValueTerminator(surrealResponse.charAt(i))) {
                            i++;
                        }
                        String value = surrealResponse.substring(start, i).trim();
                        // Check if it's a boolean or null (SurrealDB uses NULL uppercase)
                        if (value.equals("true") || value.equals("false") ||
                            value.equals("null") || value.equals("NULL")) {
                            result.append(value.toLowerCase());
                        } else {
                            // It's an unquoted string value (like document:2)
                            result.append('"').append(value).append('"');
                        }
                    }
                }
            } else {
                result.append(c);
                i++;
            }
        }

        return result.toString();
    }

    /**
     * Check if character terminates a value in SurrealDB format.
     */
    private boolean isValueTerminator(char c) {
        return c == ',' || c == '}' || c == ']' || c == '\n';
    }

    /**
     * Answer a natural language question about documents and the knowledge graph using LLM.
     * This method:
     * 1. Extracts key terms from the question
     * 2. Searches for relevant documents by content (not just graph nodes)
     * 3. Queries the knowledge graph for relationships
     * 4. Retrieves actual document content for context
     * 5. Uses LLM to synthesize a natural language answer
     *
     * @param question Natural language question (e.g., "What do the documents say about agents?" or "How are the contrib guide and agents related?")
     * @param username User asking the question
     * @return Map containing the answer, context documents, nodes, and metadata
     */
    public Map<String, Object> answerQuestion(String question, String username) {
        log.info("Answering document question: {}", question);

        Map<String, Object> result = new HashMap<>();
        result.put("question", question);

        if (llmService == null) {
            log.warn("LLMService not available - cannot answer question");
            result.put("answer", "I'm sorry, but the LLM service is not available to answer your question.");
            result.put("error", "LLM service not configured");
            return result;
        }

        try {
            // Extract key terms from the question to search for in the graph
            List<String> keyTerms = extractKeyTermsFromQuestion(question);
            log.debug("Extracted key terms from question: {}", keyTerms);

            // Search for relevant nodes in the knowledge graph
            List<KnowledgeGraphNode> relevantNodes = new ArrayList<>();
            List<KnowledgeGraphRelationship> relevantRelationships = new ArrayList<>();
            List<Map<String, Object>> relevantDocuments = new ArrayList<>();

            for (String term : keyTerms) {
                // Search knowledge graph nodes - include relationships for LLM context
                KnowledgeGraphQueryRequest searchRequest = KnowledgeGraphQueryRequest.builder()
                        .queryType(KnowledgeGraphQueryRequest.QueryType.SEARCH)
                        .searchText(term)
                        .limit(5)
                        .build();

                // Use includeRelationships=true for LLM answering - the graph structure helps provide better context
                KnowledgeGraphQueryResponse searchResponse = executeQuery(searchRequest, username, true);
                if (searchResponse.getNodes() != null) {
                    relevantNodes.addAll(searchResponse.getNodes());
                }
                if (searchResponse.getRelationships() != null) {
                    relevantRelationships.addAll(searchResponse.getRelationships());
                }

                // Also search actual document content via document service
                try {
                    List<Map<String, Object>> docs = searchDocumentContent(term, username);
                    relevantDocuments.addAll(docs);
                } catch (Exception e) {
                    log.warn("Failed to search document content for term '{}': {}", term, e.getMessage());
                }
            }

            // Remove duplicates
            relevantNodes = relevantNodes.stream()
                    .distinct()
                    .limit(10)
                    .collect(java.util.stream.Collectors.toList());
            relevantRelationships = relevantRelationships.stream()
                    .distinct()
                    .limit(20)
                    .collect(java.util.stream.Collectors.toList());

            // Deduplicate documents by ID
            Map<Object, Map<String, Object>> uniqueDocs = new java.util.LinkedHashMap<>();
            for (Map<String, Object> doc : relevantDocuments) {
                Object docId = doc.get("id");
                if (docId != null && !uniqueDocs.containsKey(docId)) {
                    uniqueDocs.put(docId, doc);
                    if (uniqueDocs.size() >= 5) break; // Limit to top 5 documents
                }
            }
            relevantDocuments = new ArrayList<>(uniqueDocs.values());

            log.info("Found {} relevant nodes, {} relationships, and {} documents for question",
                    relevantNodes.size(), relevantRelationships.size(), relevantDocuments.size());

            // Build comprehensive context for LLM including document content
            String context = buildComprehensiveContext(relevantNodes, relevantRelationships, relevantDocuments);

            // Use LLM to answer the question
            String answer = askLLMToAnswerQuestion(question, context);

            result.put("answer", answer);
            result.put("contextNodes", relevantNodes);
            result.put("contextRelationships", relevantRelationships);
            result.put("contextDocuments", relevantDocuments);
            result.put("contextNodeCount", relevantNodes.size());
            result.put("contextRelationshipCount", relevantRelationships.size());
            result.put("contextDocumentCount", relevantDocuments.size());

        } catch (Exception e) {
            log.error("Failed to answer question: {}", e.getMessage(), e);
            result.put("answer", "I encountered an error while trying to answer your question: " + e.getMessage());
            result.put("error", e.getMessage());
        }

        return result;
    }

    /**
     * Search for documents by content using the document search API.
     * Returns document summaries and excerpts relevant to the search term.
     * This method now fetches the full document content from the database for richer context.
     */
    private List<Map<String, Object>> searchDocumentContent(String searchTerm, String username) {
        List<Map<String, Object>> documents = new ArrayList<>();

        // Early return if DocumentRepository is not available
        if (documentRepository == null) {
            log.debug("DocumentRepository not available, using node metadata only");
            return documents;
        }

        try {
            // First, search knowledge graph nodes to find relevant document IDs
            KnowledgeGraphQueryRequest searchRequest = KnowledgeGraphQueryRequest.builder()
                    .queryType(KnowledgeGraphQueryRequest.QueryType.SEARCH)
                    .searchText(searchTerm)
                    .nodeTypes(java.util.Arrays.asList("document"))
                    .limit(5)
                    .build();

            KnowledgeGraphQueryResponse response = executeQuery(searchRequest, username);

            if (response.getNodes() != null && !response.getNodes().isEmpty()) {
                // Collect all document IDs to batch fetch (avoid N+1 query problem)
                List<Long> documentIds = new ArrayList<>();
                Map<Long, KnowledgeGraphNode> nodesByDocId = new HashMap<>();
                
                for (KnowledgeGraphNode node : response.getNodes()) {
                    if ("document".equalsIgnoreCase(node.getNodeType()) && node.getEntityId() != null) {
                        documentIds.add(node.getEntityId());
                        nodesByDocId.put(node.getEntityId(), node);
                    }
                }

                if (!documentIds.isEmpty()) {
                    // Batch fetch all documents in a single query
                    List<Document> fetchedDocuments = documentRepository.findAllById(documentIds);
                    
                    for (Document document : fetchedDocuments) {
                        try {
                            // Check access control with proper evaluator
                            // Note: Passing null evaluator means complex markings won't be fully evaluated
                            // This is acceptable for knowledge graph search but should be improved with proper AccessEvaluator
                            if (!accessControlService.canAccessDocument(document, null, username)) {
                                log.debug("User {} does not have access to document {}", username, document.getId());
                                continue;
                            }
                            
                            Map<String, Object> doc = new HashMap<>();
                            doc.put("id", document.getId());
                            doc.put("name", document.getDocumentName());
                            doc.put("description", document.getSummary());
                            doc.put("summary", document.getSummary());
                            doc.put("type", document.getDocumentType());
                            
                            // Include the full content for richer context
                            String content = document.getContent();
                            if (content != null && !content.isEmpty()) {
                                // Extract relevant excerpts from the content based on search term
                                String excerpt = extractRelevantExcerpt(content, searchTerm);
                                doc.put("content", content); // Full content for comprehensive answers
                                doc.put("excerpt", excerpt); // Relevant excerpt for context summary
                            } else {
                                doc.put("content", "");
                                doc.put("excerpt", "");
                            }
                            
                            documents.add(doc);
                        } catch (Exception e) {
                            log.warn("Error processing document {}: {}", document.getId(), e.getMessage());
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Error searching document content: {}", e.getMessage());
        }

        return documents;
    }

    /**
     * Extract relevant excerpt from document content based on search term.
     * Returns a snippet around the first occurrence of the search term, or the beginning if not found.
     */
    private String extractRelevantExcerpt(String content, String searchTerm) {
        if (content == null || content.isEmpty()) {
            return "";
        }
        
        // Find the first occurrence of the search term (case-insensitive)
        int index = content.toLowerCase().indexOf(searchTerm.toLowerCase());
        
        if (index >= 0) {
            // Extract content around the search term
            int start = Math.max(0, index - EXCERPT_LENGTH / 2);
            int end = Math.min(content.length(), index + searchTerm.length() + EXCERPT_LENGTH / 2);
            
            // Adjust start to not break words if possible
            if (start > 0 && start < content.length()) {
                int spaceIndex = content.lastIndexOf(' ', start);
                if (spaceIndex > 0 && start - spaceIndex < 20) {
                    start = spaceIndex + 1;
                }
            }
            
            // Adjust end to not break words if possible
            if (end < content.length()) {
                int spaceIndex = content.indexOf(' ', end);
                if (spaceIndex > 0 && spaceIndex - end < 20) {
                    end = spaceIndex;
                }
            }
            
            String excerpt = content.substring(start, end);
            
            // Add ellipsis if we didn't start/end at document boundaries
            if (start > 0) excerpt = "..." + excerpt;
            if (end < content.length()) excerpt = excerpt + "...";
            
            return excerpt;
        } else {
            // If search term not found, return the beginning of the document
            int end = Math.min(content.length(), EXCERPT_LENGTH);
            
            // Try to end at a word boundary
            if (end < content.length()) {
                int spaceIndex = content.indexOf(' ', end);
                if (spaceIndex > 0 && spaceIndex - end < 20) {
                    end = spaceIndex;
                }
            }
            
            String excerpt = content.substring(0, end);
            if (end < content.length()) excerpt = excerpt + "...";
            return excerpt;
        }
    }

    /**
     * Build comprehensive context including both graph structure and document content.
     */
    private String buildComprehensiveContext(List<KnowledgeGraphNode> nodes,
                                            List<KnowledgeGraphRelationship> relationships,
                                            List<Map<String, Object>> documents) {
        StringBuilder context = new StringBuilder();

        context.append("DOCUMENT KNOWLEDGE BASE:\n\n");

        // Add document content first (most important for content questions)
        if (!documents.isEmpty()) {
            context.append("===== DOCUMENT CONTENT =====\n\n");
            for (Map<String, Object> doc : documents) {
                context.append("Document: ").append(doc.get("name")).append("\n");
                context.append("Type: ").append(doc.get("type")).append("\n");
                
                if (doc.get("summary") != null && !doc.get("summary").toString().isEmpty()) {
                    context.append("Summary: ").append(doc.get("summary")).append("\n");
                }
                
                // Include the full content or relevant excerpt
                if (doc.get("content") != null && !doc.get("content").toString().isEmpty()) {
                    String content = doc.get("content").toString();
                    // If content is very long, prefer the excerpt, otherwise use full content
                    if (content.length() > MAX_FULL_CONTENT_LENGTH && doc.get("excerpt") != null) {
                        context.append("\nRelevant Excerpt:\n").append(doc.get("excerpt")).append("\n");
                    } else {
                        context.append("\nFull Content:\n").append(content).append("\n");
                    }
                } else if (doc.get("description") != null && !doc.get("description").toString().isEmpty()) {
                    context.append("Description: ").append(doc.get("description")).append("\n");
                }
                context.append("\n---\n\n");
            }
        }

        // Add extracted knowledge from graph nodes (key facts, procedures, concepts)
        boolean hasExtractedContent = false;
        for (KnowledgeGraphNode node : nodes) {
            if (node.getProperties() != null && 
                (node.getProperties().containsKey("keyFacts") || 
                 node.getProperties().containsKey("procedures") ||
                 node.getProperties().containsKey("concepts"))) {
                hasExtractedContent = true;
                break;
            }
        }
        
        if (hasExtractedContent) {
            context.append("===== EXTRACTED KNOWLEDGE =====\n\n");
            for (KnowledgeGraphNode node : nodes) {
                if (node.getProperties() == null) continue;
                
                boolean nodeHasContent = false;
                StringBuilder nodeContent = new StringBuilder();
                
                // Add key facts
                if (node.getProperties().containsKey("keyFacts")) {
                    Object keyFacts = node.getProperties().get("keyFacts");
                    if (keyFacts instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<String> facts = (List<String>) keyFacts;
                        if (!facts.isEmpty()) {
                            nodeContent.append("Key Facts from ").append(node.getName()).append(":\n");
                            for (String fact : facts) {
                                nodeContent.append("  • ").append(fact).append("\n");
                            }
                            nodeHasContent = true;
                        }
                    }
                }
                
                // Add procedures
                if (node.getProperties().containsKey("procedures")) {
                    Object procedures = node.getProperties().get("procedures");
                    if (procedures instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> procs = (List<Map<String, Object>>) procedures;
                        if (!procs.isEmpty()) {
                            nodeContent.append("\nProcedures from ").append(node.getName()).append(":\n");
                            for (Map<String, Object> proc : procs) {
                                nodeContent.append("  ▶ ").append(proc.get("name")).append(":\n");
                                if (proc.containsKey("steps") && proc.get("steps") instanceof List) {
                                    @SuppressWarnings("unchecked")
                                    List<String> steps = (List<String>) proc.get("steps");
                                    for (int i = 0; i < steps.size(); i++) {
                                        nodeContent.append("    ").append(i + 1).append(". ").append(steps.get(i)).append("\n");
                                    }
                                }
                            }
                            nodeHasContent = true;
                        }
                    }
                }
                
                // Add concepts
                if (node.getProperties().containsKey("concepts")) {
                    Object concepts = node.getProperties().get("concepts");
                    if (concepts instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<String> conceptList = (List<String>) concepts;
                        if (!conceptList.isEmpty()) {
                            nodeContent.append("\nKey Concepts in ").append(node.getName()).append(": ");
                            nodeContent.append(String.join(", ", conceptList)).append("\n");
                            nodeHasContent = true;
                        }
                    }
                }
                
                if (nodeHasContent) {
                    context.append(nodeContent).append("\n");
                }
            }
            context.append("\n");
        }

        // Add graph nodes
        if (!nodes.isEmpty()) {
            context.append("===== KNOWLEDGE GRAPH NODES =====\n");
            for (KnowledgeGraphNode node : nodes) {
                context.append("• ").append(node.getName())
                       .append(" (").append(node.getNodeType()).append(")");
                if (node.getDescription() != null && !node.getDescription().isEmpty()) {
                    context.append(": ").append(node.getDescription());
                }
                context.append("\n");
            }
            context.append("\n");
        }

        // Add relationships with human-readable names
        if (!relationships.isEmpty()) {
            // Build a name lookup map from nodes
            Map<String, String> nodeNames = new HashMap<>();
            for (KnowledgeGraphNode node : nodes) {
                if (node.getId() != null && node.getName() != null) {
                    nodeNames.put(node.getId(), node.getName());
                }
            }

            context.append("===== DOCUMENT RELATIONSHIPS =====\n");
            context.append("(These relationships show how documents, concepts, and procedures are connected)\n\n");

            for (KnowledgeGraphRelationship rel : relationships) {
                // Get readable names, falling back to extracting from ID
                String fromName = nodeNames.getOrDefault(rel.getFromNode(), extractNameFromId(rel.getFromNode()));
                String toName = nodeNames.getOrDefault(rel.getToNode(), extractNameFromId(rel.getToNode()));

                // Format relationship type for readability
                String relType = rel.getRelationshipType();
                String readableRelType = formatRelationshipType(relType);

                context.append("• ").append(fromName)
                       .append(" ").append(readableRelType).append(" ")
                       .append(toName);
                if (rel.getProperties() != null && rel.getProperties().containsKey("reason")) {
                    context.append("\n  Reason: ").append(rel.getProperties().get("reason"));
                }
                context.append("\n");
            }
        }

        if (documents.isEmpty() && nodes.isEmpty() && relationships.isEmpty()) {
            context.append("No relevant information found in the document knowledge base.\n");
        }

        return context.toString();
    }

    /**
     * Format a relationship type for human readability.
     * e.g., "DISCUSSES" -> "discusses", "CONTAINS_PROCEDURE" -> "contains procedure"
     */
    private String formatRelationshipType(String relType) {
        if (relType == null) return "relates to";

        switch (relType.toUpperCase()) {
            case "DISCUSSES": return "discusses";
            case "CONTAINS_PROCEDURE": return "contains procedure";
            case "RELATED_TO": return "is related to";
            case "REFERENCES": return "references";
            case "SUPERSEDES": return "supersedes";
            case "DEPENDS_ON": return "depends on";
            default: return relType.toLowerCase().replace("_", " ");
        }
    }

    /**
     * Extract key terms from a natural language question to search the graph.
     */
    private List<String> extractKeyTermsFromQuestion(String question) {
        // Remove common question words and split into terms
        String[] stopWords = {"how", "what", "why", "when", "where", "who", "are", "is", "the", "a", "an",
                              "and", "or", "of", "to", "in", "for", "on", "with", "by", "from", "as", "at",
                              "be", "been", "being", "was", "were", "can", "could", "would", "should", "do",
                              "does", "did", "have", "has", "had", "related", "connected", "linked"};

        String cleaned = question.toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", " ")
                .trim();

        List<String> terms = new ArrayList<>();
        for (String word : cleaned.split("\\s+")) {
            // Skip stop words and very short terms
            if (word.length() > 2 && !java.util.Arrays.asList(stopWords).contains(word)) {
                terms.add(word);
            }
        }

        // Also extract potential compound terms (e.g., "contrib guide")
        String[] bigrams = question.toLowerCase().split("\\s+");
        for (int i = 0; i < bigrams.length - 1; i++) {
            String bigram = bigrams[i] + " " + bigrams[i + 1];
            if (bigram.length() > 5 && !bigram.matches(".*\\b(" + String.join("|", stopWords) + ")\\b.*")) {
                terms.add(bigram);
            }
        }

        return terms.stream().distinct().collect(java.util.stream.Collectors.toList());
    }

    /**
     * Build a text context summary from nodes and relationships for the LLM.
     */
    private String buildGraphContext(List<KnowledgeGraphNode> nodes,
                                     List<KnowledgeGraphRelationship> relationships) {
        StringBuilder context = new StringBuilder();

        context.append("KNOWLEDGE GRAPH CONTEXT:\n\n");

        if (!nodes.isEmpty()) {
            context.append("NODES:\n");
            for (KnowledgeGraphNode node : nodes) {
                context.append("- ").append(node.getName())
                       .append(" (").append(node.getNodeType()).append(")");
                if (node.getDescription() != null && !node.getDescription().isEmpty()) {
                    context.append(": ").append(node.getDescription());
                }
                context.append("\n");
            }
            context.append("\n");
        }

        if (!relationships.isEmpty()) {
            context.append("RELATIONSHIPS:\n");
            for (KnowledgeGraphRelationship rel : relationships) {
                context.append("- ").append(rel.getFromNode())
                       .append(" -[").append(rel.getRelationshipType()).append("]-> ")
                       .append(rel.getToNode());
                if (rel.getProperties() != null && rel.getProperties().containsKey("reason")) {
                    context.append(" (").append(rel.getProperties().get("reason")).append(")");
                }
                context.append("\n");
            }
        }

        if (nodes.isEmpty() && relationships.isEmpty()) {
            context.append("No relevant information found in the knowledge graph.\n");
        }

        return context.toString();
    }

    /**
     * Use LLM to generate a natural language answer based on the question and graph context.
     */
    private String askLLMToAnswerQuestion(String question, String graphContext) {
        try {
            // Create agent execution for LLM service
            io.sentrius.sso.core.dto.UserDTO systemUser = io.sentrius.sso.core.dto.UserDTO.builder()
                    .username("knowledge-graph-assistant")
                    .build();

            // Use AgentExecutionService to create proper AgentExecution
            io.sentrius.sso.core.services.agents.AgentExecutionService executionService =
                    new io.sentrius.sso.core.services.agents.AgentExecutionService();
            io.sentrius.sso.core.dto.agents.AgentExecution agentExecution =
                    executionService.getAgentExecution(systemUser);
            agentExecution.setCommunicationId(java.util.UUID.randomUUID().toString());

            String systemPrompt = "You are a knowledge graph assistant that answers questions by EXTRACTING AND SYNTHESIZING information from the provided document content. " +
                    "The knowledge base includes:\n" +
                    "- Full document content\n" +
                    "- Extracted key facts and procedures from documents\n" +
                    "- Concepts and topics discovered through analysis\n" +
                    "- Relationships between documents\n" +
                    "\n" +
                    "CRITICAL RULES:\n" +
                    "1. NEVER tell users to 'refer to' or 'consult' a document - you must answer the question directly using the content provided\n" +
                    "2. Extract specific steps, procedures, code examples, or instructions from the document content\n" +
                    "3. Synthesize information from multiple sources (full content, extracted facts, procedures)\n" +
                    "4. When you cite sources, use them to support your answer - not replace it\n" +
                    "5. If the content is insufficient to answer fully, say what you CAN answer from the content, then explain what's missing\n" +
                    "\n" +
                    "CORRECT ANSWER PATTERN:\n" +
                    "Q: How do I create a custom agent?\n" +
                    "A: To create a custom agent, you need to:\n" +
                    "   1. Create a new directory under python-agent/agents/ with your agent name\n" +
                    "   2. Add an __init__.py file and your main agent implementation file\n" +
                    "   3. Register the agent in main.py by importing it and adding to the registry\n" +
                    "   (Source: CUSTOM_AGENTS.md)\n" +
                    "\n" +
                    "FORBIDDEN ANSWER PATTERN:\n" +
                    "Q: How do I create a custom agent?\n" +
                    "A: You should refer to the CUSTOM_AGENTS.md document for instructions.\n" +
                    "A: Please consult the agents documentation.\n" +
                    "A: See the CUSTOM_AGENTS.md file for details.\n" +
                    "\n" +
                    "Answer questions FROM the knowledge graph content, not ABOUT the knowledge graph content.";

            String userPrompt = "Question: " + question + "\n\n" + graphContext +
                    "\n\nProvide a direct, detailed answer to the question above by extracting and synthesizing information from the document content provided. " +
                    "DO NOT tell me to refer to or consult any documents - answer the question directly using the content above.";

            io.sentrius.sso.genai.model.LLMRequest request = io.sentrius.sso.genai.model.LLMRequest.builder()
                    .model("gpt-4.1")
                    .messages(java.util.Arrays.asList(
                            io.sentrius.sso.genai.Message.builder().role("system").content(systemPrompt).build(),
                            io.sentrius.sso.genai.Message.builder().role("user").content(userPrompt).build()
                    ))
                    .temperature(0.3F)
                    .maxTokens(1000)
                    .build();

            String response;
            try {
                response = llmService.askQuestion(agentExecution, request);
            } catch (io.sentrius.sso.core.exceptions.ZtatException e) {
                log.error("ZtatException while calling LLM service: {}", e.getMessage(), e);
                return "I encountered an error while calling the LLM service: " + e.getMessage();
            }

            // Extract text from the response (handling different formats)
            try {
                com.fasterxml.jackson.databind.JsonNode responseNode = objectMapper.readTree(response);

                log.debug("LLM response structure: has 'output'={}, has 'choices'={}, has 'content'={}",
                        responseNode.has("output"), responseNode.has("choices"), responseNode.has("content"));

                // Try different response formats

                // Format 1: Direct output array at top level (your current format)
                if (responseNode.has("output") && responseNode.get("output").isArray()) {
                    com.fasterxml.jackson.databind.JsonNode outputArray = responseNode.get("output");
                    if (!outputArray.isEmpty()) {
                        com.fasterxml.jackson.databind.JsonNode firstOutput = outputArray.get(0);
                        if (firstOutput.has("content") && firstOutput.get("content").isArray()) {
                            com.fasterxml.jackson.databind.JsonNode contentArray = firstOutput.get("content");
                            if (!contentArray.isEmpty()) {
                                com.fasterxml.jackson.databind.JsonNode firstContent = contentArray.get(0);
                                if (firstContent.has("text")) {
                                    String extractedText = firstContent.get("text").asText();
                                    log.info("Successfully extracted text from LLM response (format: output.content.text)");
                                    return extractedText;
                                }
                            }
                        }
                    }
                }

                // Format 2: OpenAI-style response
                if (responseNode.has("choices")) {
                    String extractedText = responseNode.get("choices").get(0).get("message").get("content").asText();
                    log.info("Successfully extracted text from LLM response (format: choices.message.content)");
                    return extractedText;
                }

                // Format 3: Array response with nested output field
                else if (responseNode.isArray() && !responseNode.isEmpty()) {
                    com.fasterxml.jackson.databind.JsonNode firstResponse = responseNode.get(0);
                    if (firstResponse.has("output") && firstResponse.get("output").isArray()) {
                        com.fasterxml.jackson.databind.JsonNode output = firstResponse.get("output").get(0);
                        if (output.has("content") && output.get("content").isArray()) {
                            com.fasterxml.jackson.databind.JsonNode contentNode = output.get("content").get(0);
                            if (contentNode.has("text")) {
                                String extractedText = contentNode.get("text").asText();
                                log.info("Successfully extracted text from LLM response (format: array.output.content.text)");
                                return extractedText;
                            }
                        }
                    }
                }

                // Format 4: Direct content array
                else if (responseNode.has("content") && responseNode.get("content").isArray()) {
                    com.fasterxml.jackson.databind.JsonNode contentArray = responseNode.get("content");
                    if (!contentArray.isEmpty() && contentArray.get(0).has("text")) {
                        String extractedText = contentArray.get(0).get("text").asText();
                        log.info("Successfully extracted text from LLM response (format: content.text)");
                        return extractedText;
                    }
                }

                // If we can't extract, log the structure and return error message
                log.warn("Could not extract text from LLM response. Response structure: {}",
                        response.length() > 500 ? response.substring(0, 500) + "..." : response);
                return "I received a response from the LLM but couldn't extract the answer text. Please try again.";
            } catch (Exception parseEx) {
                // If parsing fails, log and return error
                log.error("Failed to parse LLM response: {}", parseEx.getMessage(), parseEx);
                log.debug("Raw response preview: {}", response.length() > 500 ? response.substring(0, 500) + "..." : response);
                return "I encountered an error while parsing the LLM response. Please try again.";
            }

        } catch (Exception e) {
            log.error("Failed to get LLM answer: {}", e.getMessage(), e);
            return "I encountered an error while processing your question with the LLM: " + e.getMessage();
        }
    }
}

