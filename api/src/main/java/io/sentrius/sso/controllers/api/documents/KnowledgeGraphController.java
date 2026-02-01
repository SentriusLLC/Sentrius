package io.sentrius.sso.controllers.api.documents;

import io.sentrius.sso.core.model.documents.*;
import io.sentrius.sso.core.services.documents.KnowledgeGraphService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST API Controller for knowledge graph operations.
 * Provides endpoints for querying and managing the document knowledge graph.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/knowledge-graph")
public class KnowledgeGraphController {
    
    private final KnowledgeGraphService knowledgeGraphService;
    
    @Autowired
    public KnowledgeGraphController(@Autowired(required = false) KnowledgeGraphService knowledgeGraphService) {
        this.knowledgeGraphService = knowledgeGraphService;
        if (knowledgeGraphService == null) {
            log.warn("KnowledgeGraphController initialized without KnowledgeGraphService - SurrealDB may not be configured");
        } else {
            log.info("KnowledgeGraphController initialized with KnowledgeGraphService");
        }
    }
    
    /**
     * Create a node in the knowledge graph
     */
    @PostMapping("/nodes")
    public ResponseEntity<KnowledgeGraphNode> createNode(
            @RequestBody KnowledgeGraphNode node,
            Authentication authentication) {
        
        log.info("POST /api/v1/knowledge-graph/nodes - Create node request received");

        if (knowledgeGraphService == null) {
            log.warn("KnowledgeGraphService not available - SurrealDB is not configured");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(null);
        }
        
        try {
            String username = authentication.getName();
            log.info("Create node request from user: {}, nodeType: {}, nodeName: {}",
                username, node.getNodeType(), node.getName());
            log.debug("Node details: {}", node);

            // Actually create the node via the service
            KnowledgeGraphNode createdNode = knowledgeGraphService.createNode(node, username);

            if (createdNode == null) {
                log.error("Failed to create node - service returned null");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }

            return ResponseEntity.ok(createdNode);
        } catch (Exception e) {
            log.error("Failed to create knowledge graph node: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Create a relationship between two nodes.
     * Accepts both JSON request body (preferred) and query parameters (backward compatibility).
     */
    @PostMapping("/relationships")
    public ResponseEntity<KnowledgeGraphRelationship> createRelationship(
            @RequestBody(required = false) Map<String, Object> requestBody,
            @RequestParam(required = false) String fromNodeId,
            @RequestParam(required = false) String toNodeId,
            @RequestParam(required = false) String relationshipType,
            @RequestParam(required = false) Double weight,
            Authentication authentication) {
        
        // Extract parameters from JSON body if provided, otherwise use query params
        String fromNode = requestBody != null && requestBody.containsKey("fromNodeId")
            ? (String) requestBody.get("fromNodeId") : fromNodeId;
        String toNode = requestBody != null && requestBody.containsKey("toNodeId")
            ? (String) requestBody.get("toNodeId") : toNodeId;
        String relType = requestBody != null && requestBody.containsKey("relationshipType")
            ? (String) requestBody.get("relationshipType") : relationshipType;
        Double relWeight = requestBody != null && requestBody.containsKey("weight")
            ? ((Number) requestBody.get("weight")).doubleValue() : weight;

        log.info("POST /api/v1/knowledge-graph/relationships - Create relationship request: {} -> {} (type: {})",
            fromNode, toNode, relType);

        // Validate required parameters
        if (fromNode == null || toNode == null || relType == null) {
            log.error("Missing required parameters: fromNodeId={}, toNodeId={}, relationshipType={}",
                fromNode, toNode, relType);
            return ResponseEntity.badRequest().build();
        }

        if (knowledgeGraphService == null) {
            log.warn("KnowledgeGraphService not available - SurrealDB is not configured");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        
        try {
            String username = authentication.getName();
            log.info("Creating relationship from user: {}, fromNode: {}, toNode: {}, type: {}, weight: {}",
                username, fromNode, toNode, relType, relWeight);

            KnowledgeGraphRelationship relationship = knowledgeGraphService.createRelationship(
                    fromNode, toNode, relType, relWeight, username);

            if (relationship == null) {
                log.error("Failed to create relationship - service returned null");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
            
            log.info("Successfully created relationship: {} -> {} (type: {})", fromNode, toNode, relType);
            return ResponseEntity.ok(relationship);
        } catch (Exception e) {
            log.error("Failed to create relationship from {} to {} (type: {}): {}",
                fromNode, toNode, relType, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Find similar documents using the knowledge graph
     */
    @GetMapping("/documents/{documentId}/similar")
    public ResponseEntity<KnowledgeGraphQueryResponse> findSimilarDocuments(
            @PathVariable Long documentId,
            @RequestParam(defaultValue = "10") int limit,
            Authentication authentication) {
        
        log.info("GET /api/v1/knowledge-graph/documents/{}/similar - Find similar documents request (limit: {})",
            documentId, limit);

        if (knowledgeGraphService == null) {
            log.warn("KnowledgeGraphService not available - SurrealDB is not configured");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        
        try {
            String username = authentication.getName();
            log.info("Finding similar documents for documentId: {}, user: {}, limit: {}",
                documentId, username, limit);

            KnowledgeGraphQueryResponse response = knowledgeGraphService.findSimilarDocuments(
                    documentId, username, limit);

            int nodeCount = response.getNodes() != null ? response.getNodes().size() : 0;
            log.info("Found {} similar documents for documentId: {} in {}ms",
                nodeCount, documentId, response.getExecutionTimeMs());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to find similar documents for documentId {}: {}", documentId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Execute a knowledge graph query
     */
    @PostMapping("/query")
    public ResponseEntity<KnowledgeGraphQueryResponse> executeQuery(
            @RequestBody KnowledgeGraphQueryRequest request,
            Authentication authentication) {

        log.info("POST /api/v1/knowledge-graph/query - Execute query request: type={}, searchText={}",
            request.getQueryType(), request.getSearchText());

        if (knowledgeGraphService == null) {
            log.warn("KnowledgeGraphService not available - SurrealDB is not configured");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        
        try {
            String username = authentication.getName();
            log.info("Executing knowledge graph query - user: {}, type: {}, searchText: {}, limit: {}",
                username, request.getQueryType(), request.getSearchText(), request.getLimit());
            log.debug("Full query request: {}", request);

            KnowledgeGraphQueryResponse response = knowledgeGraphService.executeQuery(request, username);

            int nodeCount = response.getNodes() != null ? response.getNodes().size() : 0;
            int relationshipCount = response.getRelationships() != null ? response.getRelationships().size() : 0;
            log.info("Query completed - found {} nodes, {} relationships in {}ms",
                nodeCount, relationshipCount, response.getExecutionTimeMs());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to execute knowledge graph query (type: {}): {}",
                request.getQueryType(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Update node properties in the knowledge graph
     */
    @PatchMapping("/nodes/{nodeId}/properties")
    public ResponseEntity<Map<String, Object>> updateNodeProperties(
            @PathVariable String nodeId,
            @RequestBody Map<String, Object> properties,
            Authentication authentication) {

        log.info("PATCH /api/v1/knowledge-graph/nodes/{}/properties - Update node properties request", nodeId);

        if (knowledgeGraphService == null) {
            log.warn("KnowledgeGraphService not available - SurrealDB is not configured");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }

        try {
            String username = authentication.getName();
            log.info("Updating node properties: {} by user: {}, properties count: {}",
                nodeId, username, properties.size());
            log.debug("Properties to update: {}", properties);

            boolean updated = knowledgeGraphService.updateNodeProperties(nodeId, properties, username);

            if (updated) {
                log.info("Successfully updated node properties: {}", nodeId);
                Map<String, Object> response = new HashMap<>();
                response.put("nodeId", nodeId);
                response.put("updated", true);
                response.put("propertiesCount", properties.size());
                return ResponseEntity.ok(response);
            } else {
                log.error("Failed to update node properties: {} - service returned false", nodeId);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        } catch (Exception e) {
            log.error("Failed to update node properties {}: {}", nodeId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Delete a node from the knowledge graph
     */
    @DeleteMapping("/nodes/{nodeId}")
    public ResponseEntity<Void> deleteNode(
            @PathVariable String nodeId,
            Authentication authentication) {
        
        log.info("DELETE /api/v1/knowledge-graph/nodes/{} - Delete node request", nodeId);

        if (knowledgeGraphService == null) {
            log.warn("KnowledgeGraphService not available - SurrealDB is not configured");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        
        try {
            String username = authentication.getName();
            log.info("Deleting node: {} by user: {}", nodeId, username);

            boolean deleted = knowledgeGraphService.deleteNode(nodeId, username);
            
            if (deleted) {
                log.info("Successfully deleted node: {}", nodeId);
                return ResponseEntity.noContent().build();
            } else {
                log.error("Failed to delete node: {} - service returned false", nodeId);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        } catch (Exception e) {
            log.error("Failed to delete node {}: {}", nodeId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Get knowledge graph statistics
     */
    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Object>> getStatistics(Authentication authentication) {
        log.info("GET /api/v1/knowledge-graph/statistics - Get statistics request");

        if (knowledgeGraphService == null) {
            log.warn("KnowledgeGraphService not available - SurrealDB is not configured");
            Map<String, Object> stats = new HashMap<>();
            stats.put("enabled", false);
            stats.put("status", "service_unavailable");
            return ResponseEntity.ok(stats);
        }
        
        try {
            String username = authentication != null ? authentication.getName() : "anonymous";
            log.info("Getting knowledge graph statistics for user: {}", username);

            Map<String, Object> stats = knowledgeGraphService.getStatistics();
            log.info("Returning knowledge graph statistics: {}", stats);
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("Failed to get knowledge graph statistics: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * List all nodes in the knowledge graph (for debugging/admin)
     */
    @GetMapping("/nodes")
    public ResponseEntity<KnowledgeGraphQueryResponse> listNodes(
            @RequestParam(defaultValue = "100") int limit,
            Authentication authentication) {

        log.info("GET /api/v1/knowledge-graph/nodes - List all nodes (limit: {})", limit);

        if (knowledgeGraphService == null) {
            log.warn("KnowledgeGraphService not available - SurrealDB is not configured");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }

        try {
            String username = authentication != null ? authentication.getName() : "anonymous";
            log.info("Listing knowledge graph nodes for user: {}, limit: {}", username, limit);

            long startTime = System.currentTimeMillis();
            List<KnowledgeGraphNode> nodes = knowledgeGraphService.getAllNodes(limit);
            long executionTime = System.currentTimeMillis() - startTime;

            log.info("Listed {} nodes in {}ms", nodes.size(), executionTime);

            KnowledgeGraphQueryResponse response = KnowledgeGraphQueryResponse.builder()
                    .nodes(nodes)
                    .relationships(Collections.emptyList())
                    .totalCount(nodes.size())
                    .executionTimeMs(executionTime)
                    .build();

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to list nodes: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Ask a natural language question about the knowledge graph.
     * This endpoint queries the graph and uses LLM to provide an intelligent answer.
     */
    @PostMapping("/ask")
    public ResponseEntity<Map<String, Object>> askQuestion(
            @RequestBody Map<String, String> request,
            Authentication authentication) {

        String question = request.get("question");
        log.info("POST /api/v1/knowledge-graph/ask - Question: {}", question);

        if (knowledgeGraphService == null) {
            log.warn("KnowledgeGraphService not available - SurrealDB is not configured");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }

        if (question == null || question.trim().isEmpty()) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Question is required");
            return ResponseEntity.badRequest().body(error);
        }

        try {
            String username = authentication.getName();
            log.info("Processing knowledge graph question from user: {}", username);

            // Use the knowledge graph service to answer the question with LLM
            Map<String, Object> response = knowledgeGraphService.answerQuestion(question, username);

            log.info("Successfully answered question with {} context nodes",
                    response.getOrDefault("contextNodeCount", 0));

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to answer knowledge graph question: {}", e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to process question: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * Fetch relationships for a list of node IDs.
     * This endpoint is designed to be called async from the UI after the main query returns.
     */
    @PostMapping("/relationships/for-nodes")
    public ResponseEntity<Map<String, Object>> getRelationshipsForNodes(
            @RequestBody Map<String, Object> request,
            Authentication authentication) {

        @SuppressWarnings("unchecked")
        List<String> nodeIds = (List<String>) request.get("nodeIds");
        log.info("POST /api/v1/knowledge-graph/relationships/for-nodes - Fetching relationships for {} nodes",
            nodeIds != null ? nodeIds.size() : 0);

        if (knowledgeGraphService == null) {
            log.warn("KnowledgeGraphService not available - SurrealDB is not configured");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }

        if (nodeIds == null || nodeIds.isEmpty()) {
            Map<String, Object> result = new HashMap<>();
            result.put("relationships", Collections.emptyList());
            result.put("connectedNodes", Collections.emptyList());
            return ResponseEntity.ok(result);
        }

        try {
            String username = authentication.getName();

            // Create minimal node objects for the service method
            List<KnowledgeGraphNode> nodes = nodeIds.stream()
                .map(id -> KnowledgeGraphNode.builder().id(id).build())
                .collect(java.util.stream.Collectors.toList());

            // Use the service to fetch relationships
            KnowledgeGraphQueryResponse response = knowledgeGraphService.executeQuery(
                KnowledgeGraphQueryRequest.builder()
                    .queryType(KnowledgeGraphQueryRequest.QueryType.SEARCH)
                    .searchText("")
                    .limit(0)
                    .build(),
                username,
                true  // Include relationships
            );

            // Actually we need a dedicated method - let me just call the internal methods via a new service method
            Map<String, Object> result = knowledgeGraphService.getRelationshipsForNodeIds(nodeIds, username);

            log.info("Found {} relationships for {} nodes",
                result.getOrDefault("relationshipCount", 0), nodeIds.size());

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Failed to fetch relationships for nodes: {}", e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to fetch relationships: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }
}
