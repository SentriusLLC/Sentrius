package io.sentrius.sso.core.services.agents;

import io.sentrius.sso.core.model.agents.AgentMemory;
import io.sentrius.sso.core.repository.AgentMemoryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Vector-enhanced agent memory store that provides semantic search capabilities
 * while maintaining the existing ABAC security model and markings-based access control.
 */
@Slf4j
@Service
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class VectorAgentMemoryStore {

    private final PersistentAgentMemoryStore persistentMemoryStore;
    private final AgentMemoryRepository agentMemoryRepository;
    private final EmbeddingService embeddingService;
    private final MemoryAccessControlService accessControlService;

    public VectorAgentMemoryStore(
            PersistentAgentMemoryStore persistentMemoryStore,
            AgentMemoryRepository agentMemoryRepository,
            EmbeddingService embeddingService,
            MemoryAccessControlService accessControlService) {
        this.persistentMemoryStore = persistentMemoryStore;
        this.agentMemoryRepository = agentMemoryRepository;
        this.embeddingService = embeddingService;
        this.accessControlService = accessControlService;
    }

    /**
     * Store memory with automatic embedding generation
     */
    @Transactional
    public AgentMemory storeMemoryWithEmbedding(String agentId, String memoryKey, Object memoryValue,
                                                String classification, String[] markings, String creatorUserId) {
        log.info("Storing memory with embedding for agent: {}, key: {}", agentId, memoryKey);

        // Store the memory using the existing service
        AgentMemory memory = persistentMemoryStore.storeMemory(agentId, memoryKey, memoryValue, 
                                                               classification, markings, creatorUserId);

        // Generate and store embedding if embedding service is available
        if (embeddingService.isAvailable()) {
            try {
                generateAndStoreEmbedding(memory);
                log.info("Generated embedding for memory: agent={}, key={}", agentId, memoryKey);
            } catch (Exception e) {
                log.warn("Failed to generate embedding for memory: agent={}, key={}, error={}", 
                        agentId, memoryKey, e.getMessage());
                // Continue without embedding - memory is still stored with text-based search
            }
        } else {
            log.debug("No embedding service available - storing memory without embedding");
        }

        return memory;
    }

    /**
     * Find semantically similar memories using vector similarity
     */
    public List<AgentMemory> findSimilarMemories(String queryText, String requestingUserId, 
                                                 int limit, double threshold) {
        log.debug("Finding similar memories for query: {}, user: {}", queryText, requestingUserId);

        if (embeddingService == null || !embeddingService.isAvailable()) {
            log.debug("No embedding service available - falling back to text search");
            return fallbackToTextSearch(queryText, requestingUserId, limit);
        }

        try {
            // Generate embedding for the query
            float[] queryEmbedding = embeddingService.embed(queryText);
            if (queryEmbedding == null) {
                return fallbackToTextSearch(queryText, requestingUserId, limit);
            }
            
            String embeddingString = Arrays.toString(queryEmbedding);

            // Find similar memories using vector similarity
            List<AgentMemory> similarMemories = agentMemoryRepository.findSimilarMemories(embeddingString, limit * 2);

            // Filter based on access control and threshold
            return similarMemories.stream()
                    .filter(memory -> !memory.isExpired())
                    .filter(memory -> memory.calculateCosineSimilarity(queryEmbedding) >= threshold)
                    .filter(memory -> accessControlService.canAccessMemory(memory, requestingUserId, null, "READ"))
                    .limit(limit)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Error in semantic search, falling back to text search", e);
            return fallbackToTextSearch(queryText, requestingUserId, limit);
        }
    }

    /**
     * Find similar memories for a specific agent with access control
     */
    public List<AgentMemory> findSimilarMemoriesForAgent(String queryText, String agentId, 
                                                         String requestingUserId, int limit, double threshold) {
        log.debug("Finding similar memories for agent: {}, query: {}, user: {}", agentId, queryText, requestingUserId);

        if (embeddingService == null || !embeddingService.isAvailable()) {
            return persistentMemoryStore.findShareableMemories(agentId, requestingUserId)
                    .stream().limit(limit).collect(Collectors.toList());
        }

        try {
            float[] queryEmbedding = embeddingService.embed(queryText);
            if (queryEmbedding == null) {
                return persistentMemoryStore.findShareableMemories(agentId, requestingUserId)
                        .stream().limit(limit).collect(Collectors.toList());
            }
            
            String embeddingString = Arrays.toString(queryEmbedding);

            List<AgentMemory> similarMemories = agentMemoryRepository.findSimilarMemoriesForAgent(
                    embeddingString, agentId, threshold, limit * 2);

            return similarMemories.stream()
                    .filter(memory -> !memory.isExpired())
                    .filter(memory -> accessControlService.canAccessMemory(memory, requestingUserId, agentId, "READ"))
                    .limit(limit)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Error in agent-specific semantic search", e);
            return persistentMemoryStore.findShareableMemories(agentId, requestingUserId)
                    .stream().limit(limit).collect(Collectors.toList());
        }
    }

    /**
     * Hybrid search combining text and vector similarity with markings filter
     */
    public List<AgentMemory> hybridSearch(String searchTerm, String markingsFilter, 
                                         String requestingUserId, int limit, double threshold) {
        log.debug("Hybrid search - term: {}, markings: {}, user: {}", searchTerm, markingsFilter, requestingUserId);

        if (embeddingService == null || !embeddingService.isAvailable()) {
            return persistentMemoryStore.findMemoriesByMarkings(markingsFilter, requestingUserId)
                    .stream().limit(limit).collect(Collectors.toList());
        }

        try {
            float[] queryEmbedding = embeddingService.embed(searchTerm);
            if (queryEmbedding == null) {
                return persistentMemoryStore.findMemoriesByMarkings(markingsFilter, requestingUserId)
                        .stream().limit(limit).collect(Collectors.toList());
            }
            
            String embeddingString = Arrays.toString(queryEmbedding);

            List<AgentMemory> results;
            if (markingsFilter != null && !markingsFilter.trim().isEmpty()) {
                results = agentMemoryRepository.findSimilarMemoriesByMarkings(embeddingString, markingsFilter, limit * 2);
            } else {
                results = agentMemoryRepository.hybridSearch(searchTerm, embeddingString, threshold, limit * 2);
            }

            return results.stream()
                    .filter(memory -> !memory.isExpired())
                    .filter(memory -> accessControlService.canAccessMemory(memory, requestingUserId, null, "READ"))
                    .limit(limit)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Error in hybrid search", e);
            return persistentMemoryStore.findMemoriesByMarkings(markingsFilter, requestingUserId)
                    .stream().limit(limit).collect(Collectors.toList());
        }
    }

    /**
     * Generate embeddings for memories that don't have them yet
     */
    @Transactional
    public void generateMissingEmbeddings(int batchSize) {
        if (embeddingService == null || !embeddingService.isAvailable()) {
            log.debug("No embedding service available - skipping embedding generation");
            return;
        }

        log.info("Generating missing embeddings with batch size: {}", batchSize);

        List<AgentMemory> memoriesWithoutEmbeddings = agentMemoryRepository
                .findMemoriesWithoutEmbeddings(PageRequest.of(0, batchSize));

        int processed = 0;
        for (AgentMemory memory : memoriesWithoutEmbeddings) {
            try {
                generateAndStoreEmbedding(memory);
                processed++;
                
                if (processed % 10 == 0) {
                    log.info("Generated embeddings for {} memories", processed);
                }
            } catch (Exception e) {
                log.warn("Failed to generate embedding for memory ID: {}, error: {}", 
                        memory.getId(), e.getMessage());
            }
        }

        log.info("Completed embedding generation: {} out of {} memories processed", 
                processed, memoriesWithoutEmbeddings.size());
    }

    /**
     * Get statistics about vector store usage
     */
    public Map<String, Object> getVectorStoreStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        long totalMemories = agentMemoryRepository.count();
        long memoriesWithEmbeddings = agentMemoryRepository.countMemoriesWithEmbeddings();
        
        stats.put("total_memories", totalMemories);
        stats.put("memories_with_embeddings", memoriesWithEmbeddings);
        stats.put("embedding_coverage_percentage", 
                totalMemories > 0 ? (memoriesWithEmbeddings * 100.0 / totalMemories) : 0.0);
        stats.put("embedding_service_available", embeddingService != null && embeddingService.isAvailable());
        stats.put("vector_store_enabled", true);
        
        return stats;
    }

    // Private helper methods

    private void generateAndStoreEmbedding(AgentMemory memory) {
        // Create text for embedding from memory content and metadata
        String textForEmbedding = buildTextForEmbedding(memory);
        
        // Generate embedding
        float[] embedding = embeddingService.embed(textForEmbedding);
        if (embedding == null) {
            throw new RuntimeException("Failed to generate embedding");
        }
        
        // Store embedding in the memory object
        memory.setEmbedding(embedding);
        agentMemoryRepository.save(memory);
    }

    private String buildTextForEmbedding(AgentMemory memory) {
        StringBuilder text = new StringBuilder();
        
        // Include memory key and value
        if (memory.getMemoryKey() != null) {
            text.append(memory.getMemoryKey()).append(" ");
        }
        if (memory.getMemoryValue() != null) {
            text.append(memory.getMemoryValue()).append(" ");
        }
        
        // Include markings for context
        if (memory.getMarkings() != null) {
            text.append("markings: ").append(memory.getMarkings()).append(" ");
        }
        
        // Include classification for context
        if (memory.getClassification() != null) {
            text.append("classification: ").append(memory.getClassification());
        }
        
        return text.toString().trim();
    }

    private List<AgentMemory> fallbackToTextSearch(String queryText, String requestingUserId, int limit) {
        // Use existing text-based search as fallback
        return agentMemoryRepository.searchByMemoryValue(queryText)
                .stream()
                .filter(memory -> !memory.isExpired())
                .filter(memory -> accessControlService.canAccessMemory(memory, requestingUserId, null, "READ"))
                .limit(limit)
                .collect(Collectors.toList());
    }
}