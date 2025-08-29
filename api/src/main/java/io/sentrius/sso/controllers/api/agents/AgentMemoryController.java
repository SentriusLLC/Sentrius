package io.sentrius.sso.controllers.api.agents;

import io.sentrius.sso.core.dto.agents.AgentMemoryDTO;
import io.sentrius.sso.core.dto.agents.MemoryQueryDTO;
import io.sentrius.sso.core.model.agents.AgentMemory;
import io.sentrius.sso.core.services.agents.PersistentAgentMemoryStore;
import io.sentrius.sso.core.services.agents.VectorAgentMemoryStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.security.Principal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/v1/agents/memory")
public class AgentMemoryController {

    private final PersistentAgentMemoryStore memoryStore;
    private final VectorAgentMemoryStore vectorMemoryStore;

    public AgentMemoryController(PersistentAgentMemoryStore memoryStore, VectorAgentMemoryStore vectorMemoryStore) {
        this.memoryStore = memoryStore;
        this.vectorMemoryStore = vectorMemoryStore;
    }

    /**
     * Store agent memory
     */
    @PostMapping("/{agentId}")
    public ResponseEntity<AgentMemoryDTO> storeMemory(
            @PathVariable String agentId,
            @RequestBody @Valid AgentMemoryDTO memoryDTO,
            @RequestParam(defaultValue = "false") boolean generateEmbedding,
            Principal principal) {
        
        log.info("Storing memory for agent: {}, key: {}, embedding: {}", 
                agentId, memoryDTO.getMemoryKey(), generateEmbedding);
        
        try {
            String userId = getUserId(principal);
            
            AgentMemory memory;
            
            if (generateEmbedding) {
                // Use vector store for embedding generation
                memory = vectorMemoryStore.storeMemoryWithEmbedding(
                        agentId,
                        memoryDTO.getMemoryKey(),
                        memoryDTO.getMemoryValue(),
                        memoryDTO.getClassification(),
                        memoryDTO.getMarkings(),
                        userId
                );
            } else {
                // Use traditional storage
                memory = memoryStore.storeMemory(
                        agentId,
                        memoryDTO.getMemoryKey(),
                        memoryDTO.getMemoryValue(),
                        memoryDTO.getClassification(),
                        memoryDTO.getMarkings(),
                        userId
                );
            }
            
            AgentMemoryDTO responseDTO = convertToDTO(memory);
            return ResponseEntity.ok(responseDTO);
            
        } catch (Exception e) {
            log.error("Error storing memory for agent: {}, key: {}", agentId, memoryDTO.getMemoryKey(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Retrieve agent memory
     */
    @GetMapping("/{agentId}/{memoryKey}")
    public ResponseEntity<AgentMemoryDTO> retrieveMemory(
            @PathVariable String agentId,
            @PathVariable String memoryKey,
            Principal principal) {
        
        log.debug("Retrieving memory for agent: {}, key: {}", agentId, memoryKey);
        
        try {
            String userId = getUserId(principal);
            
            Optional<AgentMemory> memoryOpt = memoryStore.retrieveMemory(agentId, memoryKey, userId);
            
            if (memoryOpt.isPresent()) {
                AgentMemoryDTO responseDTO = convertToDTO(memoryOpt.get());
                return ResponseEntity.ok(responseDTO);
            } else {
                return ResponseEntity.notFound().build();
            }
            
        } catch (Exception e) {
            log.error("Error retrieving memory for agent: {}, key: {}", agentId, memoryKey, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Query memories with filters
     */
    @PostMapping("/query")
    public ResponseEntity<Page<AgentMemoryDTO>> queryMemories(
            @RequestBody @Valid MemoryQueryDTO queryDTO,
            Principal principal) {
        
        log.debug("Querying memories with filters: {}", queryDTO);
        
        try {
            String userId = getUserId(principal);
            
            PageRequest pageRequest = PageRequest.of(
                    queryDTO.getPage(),
                    queryDTO.getSize(),
                    Sort.by(queryDTO.getSortDirection(), queryDTO.getSortBy())
            );
            
            Page<AgentMemory> memories = memoryStore.queryMemories(
                    queryDTO.getAgentId(),
                    queryDTO.getClassification(),
                    queryDTO.getMarkings(),
                    userId,
                    pageRequest
            );
            
            Page<AgentMemoryDTO> responsePage = memories.map(this::convertToDTO);
            return ResponseEntity.ok(responsePage);
            
        } catch (Exception e) {
            log.error("Error querying memories", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get shareable memories for an agent
     */
    @GetMapping("/{agentId}/shareable")
    public ResponseEntity<List<AgentMemoryDTO>> getShareableMemories(
            @PathVariable String agentId,
            Principal principal) {
        
        log.debug("Getting shareable memories for agent: {}", agentId);
        
        try {
            String userId = getUserId(principal);
            
            List<AgentMemory> shareableMemories = memoryStore.findShareableMemories(agentId, userId);
            List<AgentMemoryDTO> responseDTOs = shareableMemories.stream()
                    .map(this::convertToDTO)
                    .collect(Collectors.toList());
            
            return ResponseEntity.ok(responseDTOs);
            
        } catch (Exception e) {
            log.error("Error getting shareable memories for agent: {}", agentId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Share memory with specific agents
     */
    @PostMapping("/{agentId}/{memoryKey}/share")
    public ResponseEntity<Map<String, Object>> shareMemory(
            @PathVariable String agentId,
            @PathVariable String memoryKey,
            @RequestBody Map<String, Object> shareRequest,
            Principal principal) {
        
        log.info("Sharing memory: agent={}, key={}", agentId, memoryKey);
        
        try {
            String userId = getUserId(principal);
            
            @SuppressWarnings("unchecked")
            List<String> targetAgentsList = (List<String>) shareRequest.get("targetAgents");
            String[] targetAgents = targetAgentsList.toArray(new String[0]);
            
            boolean success = memoryStore.shareMemoryWithAgents(agentId, memoryKey, targetAgents, userId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", success);
            response.put("sharedWith", targetAgents);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error sharing memory: agent={}, key={}", agentId, memoryKey, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Search memories by markings
     */
    @GetMapping("/search/markings/{marking}")
    public ResponseEntity<List<AgentMemoryDTO>> searchByMarkings(
            @PathVariable String marking,
            Principal principal) {
        
        log.debug("Searching memories by marking: {}", marking);
        
        try {
            String userId = getUserId(principal);
            
            List<AgentMemory> memories = memoryStore.findMemoriesByMarkings(marking, userId);
            List<AgentMemoryDTO> responseDTOs = memories.stream()
                    .map(this::convertToDTO)
                    .collect(Collectors.toList());
            
            return ResponseEntity.ok(responseDTOs);
            
        } catch (Exception e) {
            log.error("Error searching memories by marking: {}", marking, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Delete memory
     */
    @DeleteMapping("/{agentId}/{memoryKey}")
    public ResponseEntity<Map<String, Object>> deleteMemory(
            @PathVariable String agentId,
            @PathVariable String memoryKey,
            Principal principal) {
        
        log.info("Deleting memory: agent={}, key={}", agentId, memoryKey);
        
        try {
            String userId = getUserId(principal);
            
            boolean success = memoryStore.deleteMemory(agentId, memoryKey, userId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", success);
            response.put("deleted", success);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error deleting memory: agent={}, key={}", agentId, memoryKey, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get memory statistics for an agent
     */
    @GetMapping("/{agentId}/statistics")
    public ResponseEntity<Map<String, Long>> getMemoryStatistics(@PathVariable String agentId) {
        log.debug("Getting memory statistics for agent: {}", agentId);
        
        try {
            Map<String, Long> stats = memoryStore.getMemoryStatistics(agentId);
            return ResponseEntity.ok(stats);
            
        } catch (Exception e) {
            log.error("Error getting memory statistics for agent: {}", agentId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Clean up expired memories (admin endpoint)
     */
    @PostMapping("/cleanup/expired")
    public ResponseEntity<Map<String, Object>> cleanupExpiredMemories(Principal principal) {
        log.info("Cleaning up expired memories, requested by: {}", getUserId(principal));
        
        try {
            memoryStore.cleanupExpiredMemories();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Expired memories cleanup completed");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error cleaning up expired memories", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // === SEMANTIC SEARCH ENDPOINTS ===

    /**
     * Find semantically similar memories using vector similarity
     */
    @PostMapping("/search/semantic")
    public ResponseEntity<List<AgentMemoryDTO>> semanticSearch(
            @RequestBody Map<String, Object> searchRequest,
            Principal principal) {
        
        String queryText = (String) searchRequest.get("query");
        Integer limit = (Integer) searchRequest.getOrDefault("limit", 10);
        Double threshold = (Double) searchRequest.getOrDefault("threshold", 0.7);
        
        log.debug("Semantic search query: {}, limit: {}, threshold: {}", queryText, limit, threshold);
        
        try {
            String userId = getUserId(principal);
            
            List<AgentMemory> similarMemories = vectorMemoryStore.findSimilarMemories(
                    queryText, userId, limit, threshold);
            
            List<AgentMemoryDTO> responseDTOs = similarMemories.stream()
                    .map(this::convertToDTO)
                    .collect(Collectors.toList());
            
            return ResponseEntity.ok(responseDTOs);
            
        } catch (Exception e) {
            log.error("Error in semantic search", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Find semantically similar memories for a specific agent
     */
    @PostMapping("/search/semantic/{agentId}")
    public ResponseEntity<List<AgentMemoryDTO>> semanticSearchForAgent(
            @PathVariable String agentId,
            @RequestBody Map<String, Object> searchRequest,
            Principal principal) {
        
        String queryText = (String) searchRequest.get("query");
        Integer limit = (Integer) searchRequest.getOrDefault("limit", 10);
        Double threshold = (Double) searchRequest.getOrDefault("threshold", 0.7);
        
        log.debug("Agent semantic search - agent: {}, query: {}, limit: {}", agentId, queryText, limit);
        
        try {
            String userId = getUserId(principal);
            
            List<AgentMemory> similarMemories = vectorMemoryStore.findSimilarMemoriesForAgent(
                    queryText, agentId, userId, limit, threshold);
            
            List<AgentMemoryDTO> responseDTOs = similarMemories.stream()
                    .map(this::convertToDTO)
                    .collect(Collectors.toList());
            
            return ResponseEntity.ok(responseDTOs);
            
        } catch (Exception e) {
            log.error("Error in agent semantic search for agent: {}", agentId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Hybrid search combining text and vector similarity
     */
    @PostMapping("/search/hybrid")
    public ResponseEntity<List<AgentMemoryDTO>> hybridSearch(
            @RequestBody Map<String, Object> searchRequest,
            Principal principal) {
        
        String searchTerm = (String) searchRequest.get("searchTerm");
        String markingsFilter = (String) searchRequest.get("markings");
        Integer limit = (Integer) searchRequest.getOrDefault("limit", 10);
        Double threshold = (Double) searchRequest.getOrDefault("threshold", 0.7);
        
        log.debug("Hybrid search - term: {}, markings: {}, limit: {}", searchTerm, markingsFilter, limit);
        
        try {
            String userId = getUserId(principal);
            
            List<AgentMemory> results = vectorMemoryStore.hybridSearch(
                    searchTerm, markingsFilter, userId, limit, threshold);
            
            List<AgentMemoryDTO> responseDTOs = results.stream()
                    .map(this::convertToDTO)
                    .collect(Collectors.toList());
            
            return ResponseEntity.ok(responseDTOs);
            
        } catch (Exception e) {
            log.error("Error in hybrid search", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Generate embeddings for memories that don't have them (admin endpoint)
     */
    @PostMapping("/embeddings/generate")
    public ResponseEntity<Map<String, Object>> generateMissingEmbeddings(
            @RequestParam(defaultValue = "100") int batchSize,
            Principal principal) {
        
        log.info("Generating missing embeddings, batch size: {}, requested by: {}", 
                batchSize, getUserId(principal));
        
        try {
            vectorMemoryStore.generateMissingEmbeddings(batchSize);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Embedding generation started for batch size: " + batchSize);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error generating embeddings", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get vector store statistics
     */
    @GetMapping("/statistics/vector")
    public ResponseEntity<Map<String, Object>> getVectorStoreStatistics() {
        log.debug("Getting vector store statistics");
        
        try {
            Map<String, Object> stats = vectorMemoryStore.getVectorStoreStatistics();
            return ResponseEntity.ok(stats);
            
        } catch (Exception e) {
            log.error("Error getting vector store statistics", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Convert AgentMemory entity to DTO
     */
    private AgentMemoryDTO convertToDTO(AgentMemory memory) {
        AgentMemoryDTO dto = AgentMemoryDTO.builder()
                .id(memory.getId())
                .memoryKey(memory.getMemoryKey())
                .memoryValue(memory.getMemoryValue())
                .memoryType(memory.getMemoryType())
                .agentId(memory.getAgentId())
                .agentName(memory.getAgentName())
                .conversationId(memory.getConversationId())
                .classification(memory.getClassification())
                .markings(memory.getMarkingsArray())
                .accessLevel(memory.getAccessLevel())
                .creatorUserId(memory.getCreatorUserId())
                .creatorUserType(memory.getCreatorUserType())
                .createdAt(memory.getCreatedAt())
                .updatedAt(memory.getUpdatedAt())
                .expiresAt(memory.getExpiresAt())
                .sharedWithAgents(memory.getSharedAgentsArray())
                .metadata(memory.getMetadataAsMap())
                .version(memory.getVersion())
                .hasEmbedding(memory.hasEmbedding())
                .build();
        
        // Only include embedding if it exists (optional for performance)
        if (memory.hasEmbedding()) {
            dto.setEmbeddingFromArray(memory.getEmbedding());
        }
        
        return dto;
    }

    /**
     * Extract user ID from principal
     */
    private String getUserId(Principal principal) {
        if (principal instanceof Authentication) {
            return ((Authentication) principal).getName();
        }
        return principal != null ? principal.getName() : "system";
    }
}