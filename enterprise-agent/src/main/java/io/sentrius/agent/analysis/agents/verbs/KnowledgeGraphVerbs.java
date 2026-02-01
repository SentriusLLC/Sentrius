package io.sentrius.agent.analysis.agents.verbs;

import io.sentrius.sso.core.dto.documents.KnowledgeGraphNodeDTO;
import io.sentrius.sso.core.dto.documents.KnowledgeGraphQueryRequestDTO;
import io.sentrius.sso.core.dto.documents.KnowledgeGraphQueryResponseDTO;
import io.sentrius.sso.core.dto.documents.KnowledgeGraphRelationshipDTO;
import io.sentrius.sso.core.dto.agents.AgentExecutionContextDTO;
import io.sentrius.sso.core.dto.ztat.TokenDTO;
import io.sentrius.sso.core.exceptions.ZtatException;
import io.sentrius.sso.core.model.verbs.Verb;
import io.sentrius.sso.core.services.agents.ZeroTrustClientService;
import io.sentrius.sso.core.utils.JsonUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The `KnowledgeGraphVerbs` class provides methods for agents to interact with the knowledge graph.
 * Enables agents to query, traverse, and investigate document relationships and semantic connections.
 */
@Slf4j
@Service
public class KnowledgeGraphVerbs {

    private final ZeroTrustClientService zeroTrustClientService;

    public KnowledgeGraphVerbs(ZeroTrustClientService zeroTrustClientService) {
        this.zeroTrustClientService = zeroTrustClientService;
    }

    /**
     * Execute a knowledge graph query with specified parameters.
     *
     * @param token The zero trust token
     * @param contextDTO The execution context containing query parameters
     * @return Query results including nodes and relationships
     * @throws ZtatException If there is an error during the operation
     */
    @Verb(
        name = "query_knowledge_graph",
        description = "Query the knowledge graph for document relationships and semantic connections. Supports SEARCH, TRAVERSE, PATH, NEIGHBORS, and SUBGRAPH query types. Requires 'queryType' parameter. Optional: 'searchText', 'startNodeId', 'targetNodeId', 'nodeTypes', 'relationshipTypes', 'maxDepth', 'limit'.",
        returnType = KnowledgeGraphQueryResponseDTO.class,
        returnName = "queryResults",
        isAiCallable = true,
        requiresTokenManagement = true,
        paramDescriptions = {
            "queryType: Type of query (SEARCH, TRAVERSE, PATH, NEIGHBORS, SUBGRAPH)",
            "searchText: Text to search for in nodes (for SEARCH) - optional",
            "startNodeId: Starting node ID (e.g., 'document:123') - optional",
            "targetNodeId: Target node ID for path finding (for PATH) - optional",
            "nodeTypes: Array of node types to filter (e.g., ['document', 'concept']) - optional",
            "relationshipTypes: Array of relationship types to traverse - optional",
            "maxDepth: Maximum traversal depth (default 2) - optional",
            "limit: Maximum number of results (default 50) - optional"
        }
    )
    public KnowledgeGraphQueryResponseDTO queryKnowledgeGraph(TokenDTO token, AgentExecutionContextDTO contextDTO) 
            throws ZtatException {
        try {
            String queryType = contextDTO.getExecutionArgumentScoped("queryType", String.class)
                .orElseThrow(() -> new IllegalArgumentException("queryType parameter is required"));
            
            String searchText = contextDTO.getExecutionArgumentScoped("searchText", String.class)
                .orElse(null);
            
            String startNodeId = contextDTO.getExecutionArgumentScoped("startNodeId", String.class)
                .orElse(null);
            
            String targetNodeId = contextDTO.getExecutionArgumentScoped("targetNodeId", String.class)
                .orElse(null);
            
            @SuppressWarnings("unchecked")
            List<String> nodeTypes = contextDTO.getExecutionArgumentScoped("nodeTypes", List.class)
                .orElse(null);
            
            @SuppressWarnings("unchecked")
            List<String> relationshipTypes = contextDTO.getExecutionArgumentScoped("relationshipTypes", List.class)
                .orElse(null);
            
            Integer maxDepth = contextDTO.getExecutionArgumentScoped("maxDepth", Integer.class)
                .orElse(2);
            
            Integer limit = contextDTO.getExecutionArgumentScoped("limit", Integer.class)
                .orElse(50);
            
            log.info("Querying knowledge graph: type={}, searchText={}, startNode={}, limit={}", 
                    queryType, searchText, startNodeId, limit);
            
            // Build query request
            KnowledgeGraphQueryRequestDTO queryRequest = KnowledgeGraphQueryRequestDTO.builder()
                .queryType(queryType)
                .searchText(searchText)
                .startNodeId(startNodeId)
                .targetNodeId(targetNodeId)
                .nodeTypes(nodeTypes)
                .relationshipTypes(relationshipTypes)
                .maxDepth(maxDepth)
                .limit(limit)
                .build();
            
            // Call the knowledge graph query endpoint
            String requestBody = JsonUtil.MAPPER.writeValueAsString(queryRequest);
            String response = zeroTrustClientService.callPostOnApi(token, 
                    "/api/v1/knowledge-graph/query", requestBody);
            
            if (response == null) {
                log.warn("No results from knowledge graph query");
                return KnowledgeGraphQueryResponseDTO.builder()
                        .nodes(Collections.emptyList())
                        .relationships(Collections.emptyList())
                        .totalCount(0)
                        .build();
            }
            
            // Parse response
            KnowledgeGraphQueryResponseDTO queryResponse = JsonUtil.MAPPER.readValue(response, 
                    KnowledgeGraphQueryResponseDTO.class);
            
            log.info("Knowledge graph query returned {} nodes, {} relationships", 
                    queryResponse.getNodes() != null ? queryResponse.getNodes().size() : 0,
                    queryResponse.getRelationships() != null ? queryResponse.getRelationships().size() : 0);
            
            return queryResponse;
            
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to query knowledge graph", e);
            throw new RuntimeException("Failed to query knowledge graph: " + e.getMessage(), e);
        }
    }

    /**
     * Find similar documents using knowledge graph relationships.
     *
     * @param token The zero trust token
     * @param contextDTO The execution context containing documentId parameter
     * @return Similar documents connected via relationships
     * @throws ZtatException If there is an error during the operation
     */
    @Verb(
        name = "find_similar_documents",
        description = "Find documents similar to a given document using knowledge graph relationships. Requires 'documentId' parameter. Optional: 'limit' (default 10).",
        returnType = KnowledgeGraphQueryResponseDTO.class,
        returnName = "similarDocuments",
        isAiCallable = true,
        requiresTokenManagement = true,
        paramDescriptions = {
            "documentId: ID of the document to find similar documents for",
            "limit: Maximum number of similar documents to return (default 10) - optional"
        }
    )
    public KnowledgeGraphQueryResponseDTO findSimilarDocuments(TokenDTO token, AgentExecutionContextDTO contextDTO) 
            throws ZtatException {
        try {
            Long documentId = contextDTO.getExecutionArgumentScoped("documentId", Long.class)
                .orElseThrow(() -> new IllegalArgumentException("documentId parameter is required"));
            
            Integer limit = contextDTO.getExecutionArgumentScoped("limit", Integer.class)
                .orElse(10);
            
            log.info("Finding similar documents: documentId={}, limit={}", documentId, limit);
            
            // Call the similar documents endpoint
            String response = zeroTrustClientService.callGetOnApi(token, 
                    "/api/v1/knowledge-graph/documents/" + documentId + "/similar?limit=" + limit);
            
            if (response == null) {
                log.warn("No similar documents found for document: {}", documentId);
                return KnowledgeGraphQueryResponseDTO.builder()
                        .nodes(Collections.emptyList())
                        .relationships(Collections.emptyList())
                        .totalCount(0)
                        .build();
            }
            
            // Parse response
            KnowledgeGraphQueryResponseDTO queryResponse = JsonUtil.MAPPER.readValue(response, 
                    KnowledgeGraphQueryResponseDTO.class);
            
            log.info("Found {} similar documents", 
                    queryResponse.getNodes() != null ? queryResponse.getNodes().size() : 0);
            
            return queryResponse;
            
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to find similar documents", e);
            throw new RuntimeException("Failed to find similar documents: " + e.getMessage(), e);
        }
    }

    /**
     * Create a relationship between two nodes in the knowledge graph.
     *
     * @param token The zero trust token
     * @param contextDTO The execution context containing relationship parameters
     * @return The created relationship
     * @throws ZtatException If there is an error during the operation
     */
    @Verb(
        name = "create_knowledge_graph_relationship",
        description = "Create a typed relationship between two nodes in the knowledge graph. Requires 'fromNodeId', 'toNodeId', and 'relationshipType' parameters. Optional: 'weight' (0.0-1.0).",
        returnType = KnowledgeGraphRelationshipDTO.class,
        returnName = "relationship",
        isAiCallable = true,
        requiresTokenManagement = true,
        paramDescriptions = {
            "fromNodeId: Source node ID (e.g., 'document:123')",
            "toNodeId: Target node ID (e.g., 'document:456')",
            "relationshipType: Type of relationship (e.g., 'references', 'similar_to', 'derived_from')",
            "weight: Relationship strength from 0.0 to 1.0 (default 1.0) - optional"
        }
    )
    public KnowledgeGraphRelationshipDTO createRelationship(TokenDTO token, AgentExecutionContextDTO contextDTO) 
            throws ZtatException {
        try {
            String fromNodeId = contextDTO.getExecutionArgumentScoped("fromNodeId", String.class)
                .orElseThrow(() -> new IllegalArgumentException("fromNodeId parameter is required"));
            
            String toNodeId = contextDTO.getExecutionArgumentScoped("toNodeId", String.class)
                .orElseThrow(() -> new IllegalArgumentException("toNodeId parameter is required"));
            
            String relationshipType = contextDTO.getExecutionArgumentScoped("relationshipType", String.class)
                .orElseThrow(() -> new IllegalArgumentException("relationshipType parameter is required"));
            
            Double weight = contextDTO.getExecutionArgumentScoped("weight", Double.class)
                .orElse(null);
            
            log.info("Creating knowledge graph relationship: {} -> {} -> {}", 
                    fromNodeId, relationshipType, toNodeId);
            
            // Build query parameters
            Map<String, Object> params = new HashMap<>();
            params.put("fromNodeId", fromNodeId);
            params.put("toNodeId", toNodeId);
            params.put("relationshipType", relationshipType);
            if (weight != null) {
                params.put("weight", weight);
            }
            
            // Build query string
            StringBuilder queryString = new StringBuilder("?");
            params.forEach((key, value) -> {
                if (queryString.length() > 1) queryString.append("&");
                queryString.append(key).append("=").append(value);
            });
            
            // Call the create relationship endpoint
            String response = zeroTrustClientService.callPostOnApi(token, 
                    "/api/v1/knowledge-graph/relationships" + queryString.toString(), "");
            
            if (response == null) {
                log.warn("Failed to create relationship");
                return null;
            }
            
            // Parse response
            KnowledgeGraphRelationshipDTO relationship = JsonUtil.MAPPER.readValue(response, 
                    KnowledgeGraphRelationshipDTO.class);
            
            log.info("Created relationship: {}", relationship.getId());
            return relationship;
            
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to create knowledge graph relationship", e);
            throw new RuntimeException("Failed to create knowledge graph relationship: " + e.getMessage(), e);
        }
    }

    /**
     * Get immediate neighbors of a node in the knowledge graph.
     *
     * @param token The zero trust token
     * @param contextDTO The execution context containing nodeId parameter
     * @return Neighboring nodes and their relationships
     * @throws ZtatException If there is an error during the operation
     */
    @Verb(
        name = "get_knowledge_graph_neighbors",
        description = "Get immediate neighbors (connected nodes) of a specified node in the knowledge graph. Requires 'nodeId' parameter. Optional: 'limit' (default 20).",
        returnType = KnowledgeGraphQueryResponseDTO.class,
        returnName = "neighbors",
        isAiCallable = true,
        requiresTokenManagement = true,
        paramDescriptions = {
            "nodeId: ID of the node to get neighbors for (e.g., 'document:123')",
            "limit: Maximum number of neighbors to return (default 20) - optional"
        }
    )
    public KnowledgeGraphQueryResponseDTO getNeighbors(TokenDTO token, AgentExecutionContextDTO contextDTO) 
            throws ZtatException {
        try {
            String nodeId = contextDTO.getExecutionArgumentScoped("nodeId", String.class)
                .orElseThrow(() -> new IllegalArgumentException("nodeId parameter is required"));
            
            Integer limit = contextDTO.getExecutionArgumentScoped("limit", Integer.class)
                .orElse(20);
            
            log.info("Getting neighbors for node: {}, limit={}", nodeId, limit);
            
            // Build query request for NEIGHBORS
            KnowledgeGraphQueryRequestDTO queryRequest = KnowledgeGraphQueryRequestDTO.builder()
                .queryType("NEIGHBORS")
                .startNodeId(nodeId)
                .limit(limit)
                .build();
            
            // Call the knowledge graph query endpoint
            String requestBody = JsonUtil.MAPPER.writeValueAsString(queryRequest);
            String response = zeroTrustClientService.callPostOnApi(token, 
                    "/api/v1/knowledge-graph/query", requestBody);
            
            if (response == null) {
                log.warn("No neighbors found for node: {}", nodeId);
                return KnowledgeGraphQueryResponseDTO.builder()
                        .nodes(Collections.emptyList())
                        .relationships(Collections.emptyList())
                        .totalCount(0)
                        .build();
            }
            
            // Parse response
            KnowledgeGraphQueryResponseDTO queryResponse = JsonUtil.MAPPER.readValue(response, 
                    KnowledgeGraphQueryResponseDTO.class);
            
            log.info("Found {} neighbors", 
                    queryResponse.getNodes() != null ? queryResponse.getNodes().size() : 0);
            
            return queryResponse;
            
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to get knowledge graph neighbors", e);
            throw new RuntimeException("Failed to get knowledge graph neighbors: " + e.getMessage(), e);
        }
    }

    /**
     * Traverse the knowledge graph from a starting node.
     *
     * @param token The zero trust token
     * @param contextDTO The execution context containing traversal parameters
     * @return Nodes and relationships discovered during traversal
     * @throws ZtatException If there is an error during the operation
     */
    @Verb(
        name = "traverse_knowledge_graph",
        description = "Traverse the knowledge graph from a starting node, following relationships up to a specified depth. Requires 'startNodeId' parameter. Optional: 'maxDepth' (default 2), 'relationshipTypes', 'limit' (default 50).",
        returnType = KnowledgeGraphQueryResponseDTO.class,
        returnName = "traversalResults",
        isAiCallable = true,
        requiresTokenManagement = true,
        paramDescriptions = {
            "startNodeId: Starting node ID for traversal (e.g., 'document:123')",
            "maxDepth: Maximum depth to traverse (default 2) - optional",
            "relationshipTypes: Array of relationship types to follow - optional",
            "limit: Maximum number of nodes to return (default 50) - optional"
        }
    )
    public KnowledgeGraphQueryResponseDTO traverseGraph(TokenDTO token, AgentExecutionContextDTO contextDTO) 
            throws ZtatException {
        try {
            String startNodeId = contextDTO.getExecutionArgumentScoped("startNodeId", String.class)
                .orElseThrow(() -> new IllegalArgumentException("startNodeId parameter is required"));
            
            Integer maxDepth = contextDTO.getExecutionArgumentScoped("maxDepth", Integer.class)
                .orElse(2);
            
            @SuppressWarnings("unchecked")
            List<String> relationshipTypes = contextDTO.getExecutionArgumentScoped("relationshipTypes", List.class)
                .orElse(null);
            
            Integer limit = contextDTO.getExecutionArgumentScoped("limit", Integer.class)
                .orElse(50);
            
            log.info("Traversing knowledge graph: startNode={}, maxDepth={}, limit={}", 
                    startNodeId, maxDepth, limit);
            
            // Build query request for TRAVERSE
            KnowledgeGraphQueryRequestDTO queryRequest = KnowledgeGraphQueryRequestDTO.builder()
                .queryType("TRAVERSE")
                .startNodeId(startNodeId)
                .maxDepth(maxDepth)
                .relationshipTypes(relationshipTypes)
                .limit(limit)
                .build();
            
            // Call the knowledge graph query endpoint
            String requestBody = JsonUtil.MAPPER.writeValueAsString(queryRequest);
            String response = zeroTrustClientService.callPostOnApi(token, 
                    "/api/v1/knowledge-graph/query", requestBody);
            
            if (response == null) {
                log.warn("No results from graph traversal starting at: {}", startNodeId);
                return KnowledgeGraphQueryResponseDTO.builder()
                        .nodes(Collections.emptyList())
                        .relationships(Collections.emptyList())
                        .totalCount(0)
                        .build();
            }
            
            // Parse response
            KnowledgeGraphQueryResponseDTO queryResponse = JsonUtil.MAPPER.readValue(response, 
                    KnowledgeGraphQueryResponseDTO.class);
            
            log.info("Traversal found {} nodes", 
                    queryResponse.getNodes() != null ? queryResponse.getNodes().size() : 0);
            
            return queryResponse;
            
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to traverse knowledge graph", e);
            throw new RuntimeException("Failed to traverse knowledge graph: " + e.getMessage(), e);
        }
    }

    /**
     * Search for nodes in the knowledge graph by text.
     *
     * @param token The zero trust token
     * @param contextDTO The execution context containing search parameters
     * @return Matching nodes from the search
     * @throws ZtatException If there is an error during the operation
     */
    @Verb(
        name = "search_knowledge_graph",
        description = "Search for nodes in the knowledge graph by text or properties. Requires 'searchText' parameter. Optional: 'nodeTypes', 'limit' (default 50).",
        returnType = KnowledgeGraphQueryResponseDTO.class,
        returnName = "searchResults",
        isAiCallable = true,
        requiresTokenManagement = true,
        paramDescriptions = {
            "searchText: Text to search for in node names and descriptions",
            "nodeTypes: Array of node types to filter (e.g., ['document', 'concept']) - optional",
            "limit: Maximum number of results (default 50) - optional"
        }
    )
    public KnowledgeGraphQueryResponseDTO searchGraph(TokenDTO token, AgentExecutionContextDTO contextDTO) 
            throws ZtatException {
        try {
            String searchText = contextDTO.getExecutionArgumentScoped("searchText", String.class)
                .orElseThrow(() -> new IllegalArgumentException("searchText parameter is required"));
            
            @SuppressWarnings("unchecked")
            List<String> nodeTypes = contextDTO.getExecutionArgumentScoped("nodeTypes", List.class)
                .orElse(null);
            
            Integer limit = contextDTO.getExecutionArgumentScoped("limit", Integer.class)
                .orElse(50);
            
            log.info("Searching knowledge graph: searchText={}, nodeTypes={}, limit={}", 
                    searchText, nodeTypes, limit);
            
            // Build query request for SEARCH
            KnowledgeGraphQueryRequestDTO queryRequest = KnowledgeGraphQueryRequestDTO.builder()
                .queryType("SEARCH")
                .searchText(searchText)
                .nodeTypes(nodeTypes)
                .limit(limit)
                .build();
            
            // Call the knowledge graph query endpoint
            String requestBody = JsonUtil.MAPPER.writeValueAsString(queryRequest);
            String response = zeroTrustClientService.callPostOnApi(token, 
                    "/api/v1/knowledge-graph/query", requestBody);
            
            if (response == null) {
                log.warn("No results from knowledge graph search: {}", searchText);
                return KnowledgeGraphQueryResponseDTO.builder()
                        .nodes(Collections.emptyList())
                        .relationships(Collections.emptyList())
                        .totalCount(0)
                        .build();
            }
            
            // Parse response
            KnowledgeGraphQueryResponseDTO queryResponse = JsonUtil.MAPPER.readValue(response, 
                    KnowledgeGraphQueryResponseDTO.class);
            
            log.info("Search found {} nodes", 
                    queryResponse.getNodes() != null ? queryResponse.getNodes().size() : 0);
            
            return queryResponse;
            
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to search knowledge graph", e);
            throw new RuntimeException("Failed to search knowledge graph: " + e.getMessage(), e);
        }
    }

    /**
     * Find path between two nodes in the knowledge graph.
     *
     * @param token The zero trust token
     * @param contextDTO The execution context containing path parameters
     * @return Path of nodes and relationships connecting the two nodes
     * @throws ZtatException If there is an error during the operation
     */
    @Verb(
        name = "find_knowledge_graph_path",
        description = "Find a path between two nodes in the knowledge graph. Requires 'startNodeId' and 'targetNodeId' parameters.",
        returnType = KnowledgeGraphQueryResponseDTO.class,
        returnName = "pathResults",
        isAiCallable = true,
        requiresTokenManagement = true,
        paramDescriptions = {
            "startNodeId: Starting node ID (e.g., 'document:123')",
            "targetNodeId: Target node ID (e.g., 'document:456')"
        }
    )
    public KnowledgeGraphQueryResponseDTO findPath(TokenDTO token, AgentExecutionContextDTO contextDTO) 
            throws ZtatException {
        try {
            String startNodeId = contextDTO.getExecutionArgumentScoped("startNodeId", String.class)
                .orElseThrow(() -> new IllegalArgumentException("startNodeId parameter is required"));
            
            String targetNodeId = contextDTO.getExecutionArgumentScoped("targetNodeId", String.class)
                .orElseThrow(() -> new IllegalArgumentException("targetNodeId parameter is required"));
            
            log.info("Finding path in knowledge graph: {} -> {}", startNodeId, targetNodeId);
            
            // Build query request for PATH
            KnowledgeGraphQueryRequestDTO queryRequest = KnowledgeGraphQueryRequestDTO.builder()
                .queryType("PATH")
                .startNodeId(startNodeId)
                .targetNodeId(targetNodeId)
                .build();
            
            // Call the knowledge graph query endpoint
            String requestBody = JsonUtil.MAPPER.writeValueAsString(queryRequest);
            String response = zeroTrustClientService.callPostOnApi(token, 
                    "/api/v1/knowledge-graph/query", requestBody);
            
            if (response == null) {
                log.warn("No path found between {} and {}", startNodeId, targetNodeId);
                return KnowledgeGraphQueryResponseDTO.builder()
                        .nodes(Collections.emptyList())
                        .relationships(Collections.emptyList())
                        .totalCount(0)
                        .build();
            }
            
            // Parse response
            KnowledgeGraphQueryResponseDTO queryResponse = JsonUtil.MAPPER.readValue(response, 
                    KnowledgeGraphQueryResponseDTO.class);
            
            log.info("Found path with {} nodes", 
                    queryResponse.getNodes() != null ? queryResponse.getNodes().size() : 0);
            
            return queryResponse;
            
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to find path in knowledge graph", e);
            throw new RuntimeException("Failed to find path in knowledge graph: " + e.getMessage(), e);
        }
    }

    /**
     * Get statistics about the knowledge graph.
     *
     * @param token The zero trust token
     * @param contextDTO The execution context
     * @return Statistics about the knowledge graph
     * @throws ZtatException If there is an error during the operation
     */
    @Verb(
        name = "get_knowledge_graph_statistics",
        description = "Get statistics about the knowledge graph (enabled status, database type, etc.).",
        returnType = Map.class,
        returnName = "statistics",
        isAiCallable = true,
        requiresTokenManagement = true,
        paramDescriptions = {}
    )
    public Map<String, Object> getStatistics(TokenDTO token, AgentExecutionContextDTO contextDTO) 
            throws ZtatException {
        try {
            log.info("Getting knowledge graph statistics");
            
            // Call the statistics endpoint
            String response = zeroTrustClientService.callGetOnApi(token, 
                    "/api/v1/knowledge-graph/statistics");
            
            if (response == null) {
                log.warn("Failed to get knowledge graph statistics");
                return Collections.emptyMap();
            }
            
            // Parse response
            @SuppressWarnings("unchecked")
            Map<String, Object> statistics = JsonUtil.MAPPER.readValue(response, Map.class);
            
            log.info("Retrieved knowledge graph statistics: {}", statistics);
            return statistics != null ? statistics : Collections.emptyMap();
            
        } catch (Exception e) {
            log.error("Failed to get knowledge graph statistics", e);
            throw new RuntimeException("Failed to get knowledge graph statistics: " + e.getMessage(), e);
        }
    }
}
