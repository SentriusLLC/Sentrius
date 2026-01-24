package io.sentrius.agent.analysis.agents.verbs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.sentrius.sso.core.dto.agents.AgentExecutionContextDTO;
import io.sentrius.sso.core.dto.ztat.TokenDTO;
import io.sentrius.sso.core.exceptions.ZtatException;
import io.sentrius.sso.core.model.verbs.Verb;
import io.sentrius.sso.core.services.agents.ZeroTrustClientService;
import io.sentrius.sso.core.utils.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Verbs for interacting with Agent Memory API.
 * Provides AI agents with the ability to store, retrieve, search, and manage memories.
 */
@Slf4j
@Service
public class AgentMemoryVerbs {

    private static final String DEFAULT_AGENT_ID = "current";

    private final ZeroTrustClientService zeroTrustClientService;

    public AgentMemoryVerbs(ZeroTrustClientService zeroTrustClientService) {
        this.zeroTrustClientService = zeroTrustClientService;
    }

    /**
     * Store a memory with optional embedding.
     *
     * @param token The zero trust token
     * @param contextDTO The execution context containing memory details
     * @return The stored memory result
     * @throws ZtatException If there is an error during the operation
     */
    @Verb(
        name = "store_agent_memory",
        description = "Store a memory for the agent with optional embedding. " +
                     "Requires 'memoryKey' and 'memoryValue' parameters. " +
                     "Optional: 'embedding', 'metadata', 'shareable'.",
        returnType = JsonNode.class,
        returnName = "memory_stored",
        isAiCallable = true,
        requiresTokenManagement = true,
        paramDescriptions = {
            "memoryKey: Unique key for the memory",
            "memoryValue: The content to store",
            "embedding: Embedding vector for semantic search - optional",
            "metadata: Additional metadata as JSON - optional",
            "shareable: Whether memory can be shared with other agents - optional"
        }
    )
    public JsonNode storeAgentMemory(TokenDTO token, AgentExecutionContextDTO contextDTO) 
            throws ZtatException {
        try {
            String memoryKey = contextDTO.getExecutionArgumentScoped("memoryKey", String.class)
                .orElseThrow(() -> new IllegalArgumentException("memoryKey parameter is required"));
            JsonNode memoryValue = contextDTO.getExecutionArgumentScoped("memoryValue", JsonNode.class)
                .orElseThrow(() -> new IllegalArgumentException("memoryValue parameter is required"));
            
            log.info("Storing agent memory with key: {}", memoryKey);
            
            // Build request body
            ObjectNode requestBody = JsonUtil.MAPPER.createObjectNode();
            requestBody.put("memoryKey", memoryKey);
            requestBody.set("memoryValue", memoryValue);
            
            // Add optional parameters
            contextDTO.getExecutionArgumentScoped("embedding", JsonNode.class)
                .ifPresent(embedding -> requestBody.set("embedding", embedding));
            contextDTO.getExecutionArgumentScoped("metadata", JsonNode.class)
                .ifPresent(metadata -> requestBody.set("metadata", metadata));
            contextDTO.getExecutionArgumentScoped("shareable", Boolean.class)
                .ifPresent(shareable -> requestBody.put("shareable", shareable));
            
            // Call the API memory store endpoint
            String response = zeroTrustClientService.callPostOnApi(token, 
                "/api/v1/agents/memory/store", requestBody);
            
            if (response == null) {
                throw new RuntimeException("No response from memory store endpoint");
            }
            
            JsonNode responseNode = JsonUtil.MAPPER.readTree(response);
            log.info("Successfully stored agent memory");
            return responseNode;
            
        } catch (Exception e) {
            log.error("Failed to store agent memory", e);
            throw new RuntimeException("Failed to store agent memory: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieve a specific memory by key.
     *
     * @param token The zero trust token
     * @param contextDTO The execution context containing memoryKey
     * @return The retrieved memory
     * @throws ZtatException If there is an error during the operation
     */
    @Verb(
        name = "retrieve_agent_memory",
        description = "Retrieve a specific agent memory by key. " +
                     "Requires 'memoryKey' parameter.",
        returnType = JsonNode.class,
        returnName = "memory",
        isAiCallable = true,
        requiresTokenManagement = true,
        paramDescriptions = {
            "memoryKey: The memory key to retrieve"
        }
    )
    public JsonNode retrieveAgentMemory(TokenDTO token, AgentExecutionContextDTO contextDTO) 
            throws ZtatException {
        try {
            String memoryKey = contextDTO.getExecutionArgumentScoped("memoryKey", String.class)
                .orElseThrow(() -> new IllegalArgumentException("memoryKey parameter is required"));
            
            log.info("Retrieving agent memory with key: {}", memoryKey);
            
            // Get agentId from context or use current agent
            String agentId = contextDTO.getExecutionArgumentScoped("agentId", String.class)
                .orElse(DEFAULT_AGENT_ID);
            
            // Call the API memory retrieve endpoint
            String response = zeroTrustClientService.callGetOnApi(token, 
                String.format("/api/v1/agents/memory/%s/%s", agentId, memoryKey));
            
            if (response == null) {
                throw new RuntimeException("Memory not found: " + memoryKey);
            }
            
            JsonNode responseNode = JsonUtil.MAPPER.readTree(response);
            log.info("Successfully retrieved agent memory");
            return responseNode;
            
        } catch (Exception e) {
            log.error("Failed to retrieve agent memory", e);
            throw new RuntimeException("Failed to retrieve agent memory: " + e.getMessage(), e);
        }
    }

    /**
     * Query memories with filters and pagination.
     *
     * @param token The zero trust token
     * @param contextDTO The execution context containing query parameters
     * @return The query results
     * @throws ZtatException If there is an error during the operation
     */
    @Verb(
        name = "query_agent_memories",
        description = "Query agent memories with filters and pagination. " +
                     "Optional: 'filters', 'page', 'pageSize', 'sortBy'.",
        returnType = JsonNode.class,
        returnName = "query_results",
        isAiCallable = true,
        requiresTokenManagement = true,
        paramDescriptions = {
            "filters: Filter criteria as JSON - optional",
            "page: Page number - optional",
            "pageSize: Items per page - optional",
            "sortBy: Sort field - optional"
        }
    )
    public JsonNode queryAgentMemories(TokenDTO token, AgentExecutionContextDTO contextDTO) 
            throws ZtatException {
        try {
            log.info("Querying agent memories");
            
            // Build request body
            ObjectNode requestBody = JsonUtil.MAPPER.createObjectNode();
            
            // Add optional parameters
            contextDTO.getExecutionArgumentScoped("filters", JsonNode.class)
                .ifPresent(filters -> requestBody.set("filters", filters));
            contextDTO.getExecutionArgumentScoped("page", Integer.class)
                .ifPresent(page -> requestBody.put("page", page));
            contextDTO.getExecutionArgumentScoped("pageSize", Integer.class)
                .ifPresent(pageSize -> requestBody.put("pageSize", pageSize));
            contextDTO.getExecutionArgumentScoped("sortBy", String.class)
                .ifPresent(sortBy -> requestBody.put("sortBy", sortBy));
            
            // Call the API memory query endpoint
            String response = zeroTrustClientService.callPostOnApi(token, 
                "/api/v1/agents/memory/query", requestBody);
            
            if (response == null) {
                throw new RuntimeException("No response from memory query endpoint");
            }
            
            JsonNode responseNode = JsonUtil.MAPPER.readTree(response);
            log.info("Successfully queried agent memories");
            return responseNode;
            
        } catch (Exception e) {
            log.error("Failed to query agent memories", e);
            throw new RuntimeException("Failed to query agent memories: " + e.getMessage(), e);
        }
    }

    /**
     * Perform semantic search on memories using embeddings.
     *
     * @param token The zero trust token
     * @param contextDTO The execution context containing search query
     * @return The search results
     * @throws ZtatException If there is an error during the operation
     */
    @Verb(
        name = "semantic_search_memories",
        description = "Perform semantic search on agent memories using embeddings. " +
                     "Requires 'query' or 'embedding' parameter. " +
                     "Optional: 'topK', 'threshold'.",
        returnType = JsonNode.class,
        returnName = "search_results",
        isAiCallable = true,
        requiresTokenManagement = true,
        paramDescriptions = {
            "query: Text query for semantic search - optional if embedding provided",
            "embedding: Embedding vector for search - optional if query provided",
            "topK: Number of top results to return - optional",
            "threshold: Similarity threshold - optional"
        }
    )
    public JsonNode semanticSearchMemories(TokenDTO token, AgentExecutionContextDTO contextDTO) 
            throws ZtatException {
        try {
            log.info("Performing semantic search on agent memories");
            
            // Build request body
            ObjectNode requestBody = JsonUtil.MAPPER.createObjectNode();
            
            // Add query or embedding (at least one is required)
            contextDTO.getExecutionArgumentScoped("query", String.class)
                .ifPresent(query -> requestBody.put("query", query));
            contextDTO.getExecutionArgumentScoped("embedding", JsonNode.class)
                .ifPresent(embedding -> requestBody.set("embedding", embedding));
            
            if (!requestBody.has("query") && !requestBody.has("embedding")) {
                throw new IllegalArgumentException("Either 'query' or 'embedding' parameter is required");
            }
            
            // Add optional parameters
            contextDTO.getExecutionArgumentScoped("topK", Integer.class)
                .ifPresent(topK -> requestBody.put("topK", topK));
            contextDTO.getExecutionArgumentScoped("threshold", Double.class)
                .ifPresent(threshold -> requestBody.put("threshold", threshold));
            
            // Get agentId from context or use current agent
            String agentId = contextDTO.getExecutionArgumentScoped("agentId", String.class)
                .orElse(DEFAULT_AGENT_ID);
            
            // Call the API semantic search endpoint
            String response = zeroTrustClientService.callPostOnApi(token, 
                String.format("/api/v1/agents/memory/search/semantic/%s", agentId), requestBody);
            
            if (response == null) {
                throw new RuntimeException("No response from semantic search endpoint");
            }
            
            JsonNode responseNode = JsonUtil.MAPPER.readTree(response);
            log.info("Successfully performed semantic search");
            return responseNode;
            
        } catch (Exception e) {
            log.error("Failed to perform semantic search", e);
            throw new RuntimeException("Failed to perform semantic search: " + e.getMessage(), e);
        }
    }

    /**
     * Perform hybrid text + vector search on memories.
     *
     * @param token The zero trust token
     * @param contextDTO The execution context containing search parameters
     * @return The search results
     * @throws ZtatException If there is an error during the operation
     */
    @Verb(
        name = "hybrid_search_memories",
        description = "Perform hybrid text + vector search on agent memories. " +
                     "Requires 'textQuery' and 'embedding' parameters. " +
                     "Optional: 'topK', 'textWeight', 'vectorWeight'.",
        returnType = JsonNode.class,
        returnName = "hybrid_results",
        isAiCallable = true,
        requiresTokenManagement = true,
        paramDescriptions = {
            "textQuery: Text query for keyword search",
            "embedding: Embedding vector for semantic search",
            "topK: Number of top results to return - optional",
            "textWeight: Weight for text search (0-1) - optional",
            "vectorWeight: Weight for vector search (0-1) - optional"
        }
    )
    public JsonNode hybridSearchMemories(TokenDTO token, AgentExecutionContextDTO contextDTO) 
            throws ZtatException {
        try {
            String textQuery = contextDTO.getExecutionArgumentScoped("textQuery", String.class)
                .orElseThrow(() -> new IllegalArgumentException("textQuery parameter is required"));
            JsonNode embedding = contextDTO.getExecutionArgumentScoped("embedding", JsonNode.class)
                .orElseThrow(() -> new IllegalArgumentException("embedding parameter is required"));
            
            log.info("Performing hybrid search on agent memories");
            
            // Build request body
            ObjectNode requestBody = JsonUtil.MAPPER.createObjectNode();
            requestBody.put("textQuery", textQuery);
            requestBody.set("embedding", embedding);
            
            // Add optional parameters
            contextDTO.getExecutionArgumentScoped("topK", Integer.class)
                .ifPresent(topK -> requestBody.put("topK", topK));
            contextDTO.getExecutionArgumentScoped("textWeight", Double.class)
                .ifPresent(textWeight -> requestBody.put("textWeight", textWeight));
            contextDTO.getExecutionArgumentScoped("vectorWeight", Double.class)
                .ifPresent(vectorWeight -> requestBody.put("vectorWeight", vectorWeight));
            
            // Call the API hybrid search endpoint
            String response = zeroTrustClientService.callPostOnApi(token, 
                "/api/v1/agents/memory/search/hybrid", requestBody);
            
            if (response == null) {
                throw new RuntimeException("No response from hybrid search endpoint");
            }
            
            JsonNode responseNode = JsonUtil.MAPPER.readTree(response);
            log.info("Successfully performed hybrid search");
            return responseNode;
            
        } catch (Exception e) {
            log.error("Failed to perform hybrid search", e);
            throw new RuntimeException("Failed to perform hybrid search: " + e.getMessage(), e);
        }
    }

    /**
     * Delete a specific memory.
     *
     * @param token The zero trust token
     * @param contextDTO The execution context containing memoryKey
     * @return The deletion result
     * @throws ZtatException If there is an error during the operation
     */
    @Verb(
        name = "delete_agent_memory",
        description = "Delete a specific agent memory by key. " +
                     "Requires 'memoryKey' parameter.",
        returnType = Boolean.class,
        returnName = "deleted",
        isAiCallable = false,  // Disabled for AI due to destructive nature
        requiresTokenManagement = true,
        paramDescriptions = {
            "memoryKey: The memory key to delete"
        }
    )
    public Boolean deleteAgentMemory(TokenDTO token, AgentExecutionContextDTO contextDTO) 
            throws ZtatException {
        try {
            String memoryKey = contextDTO.getExecutionArgumentScoped("memoryKey", String.class)
                .orElseThrow(() -> new IllegalArgumentException("memoryKey parameter is required"));
            
            log.warn("Deleting agent memory with key: {}", memoryKey);
            
            // Get agentId from context or use current agent
            String agentId = contextDTO.getExecutionArgumentScoped("agentId", String.class)
                .orElse(DEFAULT_AGENT_ID);
            
            // Call the API memory delete endpoint
            String response = zeroTrustClientService.callDeleteOnApi(token, 
                String.format("/api/v1/agents/memory/%s/%s/delete", agentId, memoryKey));
            
            log.info("Successfully deleted agent memory");
            return response != null;
            
        } catch (Exception e) {
            log.error("Failed to delete agent memory", e);
            return false;
        }
    }

    /**
     * Get memory statistics for the agent.
     *
     * @param token The zero trust token
     * @param contextDTO The execution context
     * @return Memory statistics
     * @throws ZtatException If there is an error during the operation
     */
    @Verb(
        name = "get_memory_statistics",
        description = "Get memory statistics for the agent. " +
                     "Returns counts, sizes, and usage information.",
        returnType = JsonNode.class,
        returnName = "statistics",
        isAiCallable = true,
        requiresTokenManagement = true,
        skipMemoryStorage = true
    )
    public JsonNode getMemoryStatistics(TokenDTO token, AgentExecutionContextDTO contextDTO) 
            throws ZtatException {
        try {
            log.info("Getting agent memory statistics");
            
            // Get agentId from context or use current agent
            String agentId = contextDTO.getExecutionArgumentScoped("agentId", String.class)
                .orElse(DEFAULT_AGENT_ID);
            
            // Call the API memory statistics endpoint
            String response = zeroTrustClientService.callGetOnApi(token, 
                String.format("/api/v1/agents/memory/%s/statistics", agentId));
            
            if (response == null) {
                throw new RuntimeException("No response from memory statistics endpoint");
            }
            
            JsonNode responseNode = JsonUtil.MAPPER.readTree(response);
            log.info("Successfully retrieved memory statistics");
            return responseNode;
            
        } catch (Exception e) {
            log.error("Failed to get memory statistics", e);
            throw new RuntimeException("Failed to get memory statistics: " + e.getMessage(), e);
        }
    }

    /**
     * Get shareable memories for the agent.
     *
     * @param token The zero trust token
     * @param contextDTO The execution context
     * @return List of shareable memories
     * @throws ZtatException If there is an error during the operation
     */
    @Verb(
        name = "get_shareable_memories",
        description = "Get all shareable memories for the agent.",
        returnType = JsonNode.class,
        returnName = "shareable_memories",
        isAiCallable = true,
        requiresTokenManagement = true,
        skipMemoryStorage = true
    )
    public JsonNode getShareableMemories(TokenDTO token, AgentExecutionContextDTO contextDTO) 
            throws ZtatException {
        try {
            log.info("Getting shareable memories");
            
            // Get agentId from context or use current agent
            String agentId = contextDTO.getExecutionArgumentScoped("agentId", String.class)
                .orElse(DEFAULT_AGENT_ID);
            
            // Call the API shareable memories endpoint
            String response = zeroTrustClientService.callGetOnApi(token, 
                String.format("/api/v1/agents/memory/%s/shareable", agentId));
            
            if (response == null) {
                throw new RuntimeException("No response from shareable memories endpoint");
            }
            
            JsonNode responseNode = JsonUtil.MAPPER.readTree(response);
            log.info("Successfully retrieved shareable memories");
            return responseNode;
            
        } catch (Exception e) {
            log.error("Failed to get shareable memories", e);
            throw new RuntimeException("Failed to get shareable memories: " + e.getMessage(), e);
        }
    }

    /**
     * Share a memory with other agents.
     *
     * @param token The zero trust token
     * @param contextDTO The execution context containing memoryKey and targetAgents
     * @return The share result
     * @throws ZtatException If there is an error during the operation
     */
    @Verb(
        name = "share_agent_memory",
        description = "Share a memory with other agents. " +
                     "Requires 'memoryKey' and 'targetAgents' parameters.",
        returnType = JsonNode.class,
        returnName = "share_result",
        isAiCallable = true,
        requiresTokenManagement = true,
        paramDescriptions = {
            "memoryKey: The memory key to share",
            "targetAgents: Array of target agent IDs"
        }
    )
    public JsonNode shareAgentMemory(TokenDTO token, AgentExecutionContextDTO contextDTO) 
            throws ZtatException {
        try {
            String memoryKey = contextDTO.getExecutionArgumentScoped("memoryKey", String.class)
                .orElseThrow(() -> new IllegalArgumentException("memoryKey parameter is required"));
            JsonNode targetAgents = contextDTO.getExecutionArgumentScoped("targetAgents", JsonNode.class)
                .orElseThrow(() -> new IllegalArgumentException("targetAgents parameter is required"));
            
            log.info("Sharing agent memory: {}", memoryKey);
            
            // Get agentId from context or use current agent
            String agentId = contextDTO.getExecutionArgumentScoped("agentId", String.class)
                .orElse(DEFAULT_AGENT_ID);
            
            // Build request body
            ObjectNode requestBody = JsonUtil.MAPPER.createObjectNode();
            requestBody.set("targetAgents", targetAgents);
            
            // Call the API memory share endpoint
            String response = zeroTrustClientService.callPostOnApi(token, 
                String.format("/api/v1/agents/memory/%s/%s/share", agentId, memoryKey), requestBody);
            
            if (response == null) {
                throw new RuntimeException("No response from memory share endpoint");
            }
            
            JsonNode responseNode = JsonUtil.MAPPER.readTree(response);
            log.info("Successfully shared agent memory");
            return responseNode;
            
        } catch (Exception e) {
            log.error("Failed to share agent memory", e);
            throw new RuntimeException("Failed to share agent memory: " + e.getMessage(), e);
        }
    }
}
