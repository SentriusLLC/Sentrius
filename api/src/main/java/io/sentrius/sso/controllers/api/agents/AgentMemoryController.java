package io.sentrius.sso.controllers.api.agents;

import io.sentrius.sso.core.annotations.LimitAccess;
import io.sentrius.sso.core.config.SystemOptions;
import io.sentrius.sso.core.controllers.BaseController;
import io.sentrius.sso.core.dto.agents.AgentMemoryDTO;
import io.sentrius.sso.core.dto.agents.MemoryQueryDTO;
import io.sentrius.sso.core.embeddings.EmbeddingService;
import io.sentrius.sso.core.model.agents.AgentMemory;
import io.sentrius.sso.core.model.security.enums.ApplicationAccessEnum;
import io.sentrius.sso.core.services.ErrorOutputService;
import io.sentrius.sso.core.services.UserService;
import io.sentrius.sso.core.services.agents.PersistentAgentMemoryStore;
import io.sentrius.sso.core.services.agents.VectorAgentMemoryStore;
import io.sentrius.sso.core.services.users.UserAttributeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.accumulo.access.AccessEvaluator;
import org.apache.accumulo.access.Authorizations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/v1/agents/memory")
public class AgentMemoryController extends BaseController {

    private final PersistentAgentMemoryStore memoryStore;
    private final VectorAgentMemoryStore vectorMemoryStore;
    private final UserAttributeService userAttributeService;
    private final EmbeddingService embeddingService;

    public AgentMemoryController(PersistentAgentMemoryStore memoryStore, VectorAgentMemoryStore vectorMemoryStore, UserService userService, SystemOptions systemOptions, ErrorOutputService errorOutputService,
                                 UserAttributeService userAttributeService,
                                 EmbeddingService embeddingService
    ) {
        super(userService, systemOptions, errorOutputService);
        this.memoryStore = memoryStore;
        this.vectorMemoryStore = vectorMemoryStore;
        this.userAttributeService = userAttributeService;
        this.embeddingService = embeddingService;
    }

    /**
     * Store agent memory
     */
    @PostMapping("/store")
    public ResponseEntity<List<AgentMemoryDTO>> storeMemories(
        @RequestParam(name = "agentId") String agentId,
        @RequestBody List<AgentMemoryDTO> memoryDTOs,
        @RequestParam(defaultValue = "false") boolean generateEmbedding,
        HttpServletRequest request, HttpServletResponse response) {



        
        try {
            var operatingUser = getOperatingUser(request,response);
            String userId = operatingUser.getUserId();

            List<AgentMemoryDTO> agentMemoryDTOs = new ArrayList<>();
            AgentMemory memory;
            if (generateEmbedding) {

                memoryDTOs = vectorMemoryStore.generateEmbeddings(memoryDTOs);

            }

            for(AgentMemoryDTO memoryDTO : memoryDTOs) {
                log.info("Storing memory for agent: {}, key: {}, embedding: {}",
                    agentId, memoryDTO.getMemoryKey(), generateEmbedding || memoryDTO.isHasEmbedding());
                if (memoryDTO.isHasEmbedding() && memoryDTO.getEmbedding() != null) {
                    // Store with provided embedding
                    memory = vectorMemoryStore.storeMemoryWithProvidedEmbedding(
                        agentId,
                        memoryDTO.getMemoryKey(),
                        memoryDTO.getMemoryValue(),
                        memoryDTO.getClassification(),
                        memoryDTO.getMarkings(),
                        memoryDTO.getEmbedding(),
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

                agentMemoryDTOs.add(responseDTO);

        }
        return ResponseEntity.ok(agentMemoryDTOs);
            
        } catch (Exception e) {
            log.error("Error storing memory for agent: {}, memoryDTOs: {}", agentId, memoryDTOs.size(), e);
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
            HttpServletRequest request, HttpServletResponse response) {
        
        log.debug("Retrieving memory for agent: {}, key: {}", agentId, memoryKey);
        
        try {
            var operatingUser = getOperatingUser(request,response);
            String userId = operatingUser.getUserId();

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
            HttpServletRequest request, HttpServletResponse response) {
        
        log.debug("Querying memories with filters: {}", queryDTO);
        
        try {
            var operatingUser = getOperatingUser(request,response);
            String userId = operatingUser.getUserId();

            PageRequest pageRequest = PageRequest.of(
                    queryDTO.getPage(),
                    queryDTO.getSize(),
                    Sort.by(queryDTO.getSortDirection(), queryDTO.getSortBy())
            );


            Page<AgentMemory> memories = memoryStore.findByMemoryKey(
                    queryDTO.getAgentId(),
                    queryDTO.getClassification(),
                    queryDTO.getMarkings(),
                    userId,
                    queryDTO.getMemoryKey(),
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
            HttpServletRequest request, HttpServletResponse response) {
        
        log.debug("Getting shareable memories for agent: {}", agentId);
        
        try {
            var operatingUser = getOperatingUser(request,response);
            String userId = operatingUser.getUserId();

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
            HttpServletRequest request, HttpServletResponse response) {
        
        log.info("Sharing memory: agent={}, key={}", agentId, memoryKey);
        
        try {
            var operatingUser = getOperatingUser(request,response);
            String userId = operatingUser.getUserId();

            @SuppressWarnings("unchecked")
            List<String> targetAgentsList = (List<String>) shareRequest.get("targetAgents");
            String[] targetAgents = targetAgentsList.toArray(new String[0]);
            
            boolean success = memoryStore.shareMemoryWithAgents(agentId, memoryKey, targetAgents, userId);
            
            Map<String, Object> userResponse = new HashMap<>();
            userResponse.put("success", success);
            userResponse.put("sharedWith", targetAgents);
            
            return ResponseEntity.ok(userResponse);
            
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
            HttpServletRequest request, HttpServletResponse response) {
        
        log.debug("Searching memories by marking: {}", marking);
        
        try {
            var operatingUser = getOperatingUser(request,response);
            String userId = operatingUser.getUserId();

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
            HttpServletRequest request, HttpServletResponse response) {
        
        log.info("Deleting memory: agent={}, key={}", agentId, memoryKey);
        
        try {
            var operatingUser = getOperatingUser(request,response);
            String userId = operatingUser.getUserId();

            boolean success = memoryStore.deleteMemory(agentId, memoryKey, userId);
            
            Map<String, Object> userResponse = new HashMap<>();
            userResponse.put("success", success);
            userResponse.put("deleted", success);
            
            return ResponseEntity.ok(userResponse);
            
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
    public ResponseEntity<Map<String, Object>> cleanupExpiredMemories(HttpServletRequest request, HttpServletResponse response) {
        var operatingUser = getOperatingUser(request,response);
        String userId = operatingUser.getUserId();
        log.info("Cleaning up expired memories, requested by: {}", userId);
        
        try {
            memoryStore.cleanupExpiredMemories();
            
            Map<String, Object> userResponse = new HashMap<>();
            userResponse.put("success", true);
            userResponse.put("message", "Expired memories cleanup completed");
            
            return ResponseEntity.ok(userResponse);
            
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
            HttpServletRequest request, HttpServletResponse response) {
        
        String queryText = (String) searchRequest.get("query");
        Integer limit = (Integer) searchRequest.getOrDefault("limit", 10);
        Double threshold = (Double) searchRequest.getOrDefault("threshold", 0.7);
        
        log.debug("Semantic search query: {}, limit: {}, threshold: {}", queryText, limit, threshold);
        
        try {
            var operatingUser = getOperatingUser(request,response);
            String userId = operatingUser.getUserId();

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
            HttpServletRequest request, HttpServletResponse response) {
        
        String queryText = (String) searchRequest.get("query");
        Integer limit = (Integer) searchRequest.getOrDefault("limit", 10);
        Double threshold = (Double) searchRequest.getOrDefault("threshold", 0.7);
        
        log.debug("Agent semantic search - agent: {}, query: {}, limit: {}", agentId, queryText, limit);
        
        try {
            var operatingUser = getOperatingUser(request,response);
            String userId = operatingUser.getUserId();

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
            HttpServletRequest request, HttpServletResponse response) {
        
        String searchTerm = (String) searchRequest.get("searchTerm");
        String markingsFilter = (String) searchRequest.get("markings");
        Integer limit = (Integer) searchRequest.getOrDefault("limit", 10);
        Double threshold = (Double) searchRequest.getOrDefault("threshold", 0.7);
        
        log.debug("Hybrid search - term: {}, markings: {}, limit: {}", searchTerm, markingsFilter, limit);
        
        try {
            var operatingUser = getOperatingUser(request,response);
            String userId = operatingUser.getUserId();


            var attributes  = userAttributeService.getUserAttributes(userId);

            List<Authorizations> authorizations = new ArrayList<>();

            for(var attr : attributes) {
                authorizations.add( Authorizations.of( attr.getAttributeValue() ) );
            }

            AccessEvaluator evaluator = authorizations.isEmpty() ? null :  AccessEvaluator.of(authorizations);

            List<AgentMemory> results = vectorMemoryStore.hybridSearch(evaluator,
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
            HttpServletRequest request, HttpServletResponse response) {

        var operatingUser = getOperatingUser(request,response);
        String userId = operatingUser.getUserId();
        log.info("Generating missing embeddings, batch size: {}, requested by: {}", 
                batchSize, userId);
        
        try {
            vectorMemoryStore.generateMissingEmbeddings(batchSize);
            
            Map<String, Object> userResponse = new HashMap<>();
            userResponse.put("success", true);
            userResponse.put("message", "Embedding generation started for batch size: " + batchSize);
            
            return ResponseEntity.ok(userResponse);
            
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

    @GetMapping("/search")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public ResponseEntity<Page<AgentMemoryDTO>> searchAgentMemory(
        @RequestParam(required = false) String content,
        @RequestParam(required = false, defaultValue = "") String markings,
        @RequestParam(required = false) String agent,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        HttpServletRequest request, HttpServletResponse response) {

        var operatingUser = getOperatingUser(request,response);

        try {
            log.info("Searching agent memory with parameters - content: '{}', agent: '{}', startDate: {}, endDate: {}, page: {}, size: {}",
                content, agent, startDate, endDate, page, size);
            var queryDTOBuilder = MemoryQueryDTO.builder()
                .searchTerm(content)
                .includeExpired(false)
                .page(page)
                .size(size)
                .sortBy("createdAt")
                .sortDirection(Sort.Direction.DESC);

            if (agent != null && !agent.isBlank()) {
                queryDTOBuilder.agentId(agent);
            }

            queryDTOBuilder.markings(markings);

            var queryDTO = queryDTOBuilder.build();

            PageRequest pageRequest = PageRequest.of(
                queryDTO.getPage(),
                queryDTO.getSize(),
                Sort.by(queryDTO.getSortDirection(), queryDTO.getSortBy())
            );

            var attributes  = userAttributeService.getUserAttributes(operatingUser.getUserId()
            );

            List<Authorizations> authorizations = new ArrayList<>();

            for(var attr : attributes) {
                log.info("Searching agent memory with attributes - attribute: '{}'", attr);
                authorizations.add( Authorizations.of( attr.getAttributeValue() ) );
            }

            log.info("Searching agent memory with authorizations - authorizations): '{}'", authorizations);
            AccessEvaluator evaluator = authorizations.isEmpty() ? null :  AccessEvaluator.of(authorizations);

            List<AgentMemory> results = vectorMemoryStore.hybridSearch(evaluator,
                content, markings, operatingUser.getUserId(), 10, 0.75);

            Page<AgentMemoryDTO> responseDTOs = results.stream()
                .map(this::convertToDTO).collect(Collectors.collectingAndThen(
                    Collectors.toList(),
                    list -> {
                        int start = Math.min((int)pageRequest.getOffset(), list.size());
                        int end = Math.min((start + pageRequest.getPageSize()), list.size());
                        return new org.springframework.data.domain.PageImpl<>(list.subList(start, end), pageRequest, list.size());
                    }
                ));

            return ResponseEntity.ok(responseDTOs);

        } catch (Exception e) {
            log.error("Error searching agent memory", e);
            return ResponseEntity.status(500).build();
        }
    }

}