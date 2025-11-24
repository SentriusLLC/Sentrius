package io.sentrius.sso.core.services.agents;

import io.sentrius.sso.core.dto.agents.AgentMemoryDTO;
import io.sentrius.sso.core.model.agents.AgentMemory;
import io.sentrius.sso.core.repository.AgentMemoryRepository;
import io.sentrius.sso.core.services.endpoints.CosineSimilarity;
import lombok.extern.slf4j.Slf4j;
import org.apache.accumulo.access.AccessEvaluator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
    private final MemoryQueryExpansionService queryExpansionService;
    
    // Memory keys that should be excluded from search results to prevent recursive nesting
    private static final Set<String> EXCLUDED_MEMORY_KEY_PREFIXES = Set.of(
        "lookup_agent_memory",
        "search_agent_memory_semantic"
    );

    public VectorAgentMemoryStore(
            PersistentAgentMemoryStore persistentMemoryStore,
            AgentMemoryRepository agentMemoryRepository,
            EmbeddingService embeddingService,
            MemoryAccessControlService accessControlService,
            MemoryQueryExpansionService queryExpansionService) {
        this.persistentMemoryStore = persistentMemoryStore;
        this.agentMemoryRepository = agentMemoryRepository;
        this.embeddingService = embeddingService;
        this.accessControlService = accessControlService;
        this.queryExpansionService = queryExpansionService;
    }
    
    /**
     * Check if a memory key should be excluded from search results.
     * Excludes temporary lookup/search results to prevent recursive nesting.
     */
    private boolean isExcludedMemoryKey(String memoryKey) {
        if (memoryKey == null) {
            return false;
        }
        return EXCLUDED_MEMORY_KEY_PREFIXES.stream()
            .anyMatch(memoryKey::startsWith);
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
                    .filter(memory -> !isExcludedMemoryKey(memory.getMemoryKey()))
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
                    .filter(memory -> !isExcludedMemoryKey(memory.getMemoryKey()))
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
     * Hybrid search combining text and vector similarity with markings filter.
     * Enhanced with query expansion to improve recall.
     */
    public List<AgentMemory>  hybridSearch(
        AccessEvaluator evaluator, String searchTerm, String markingsFilter,
        String requestingUserId, int limit, double threshold) {

        log.info("Hybrid search - term: '{}', markings: {}, user: {}, threshold: {}", 
                 searchTerm, markingsFilter, requestingUserId, threshold);

        try {
            // Use a more lenient threshold if not explicitly provided
            // Lower threshold helps with recall for queries like "user name" vs "my name is marc"
            double effectiveThreshold = threshold > 0 ? threshold : queryExpansionService.getSuggestedThreshold(searchTerm);
            log.info("Using effective threshold: {}", effectiveThreshold);

            // --- 1. Expand the query to improve matching
            List<String> expandedTerms = queryExpansionService.getTopSearchTerms(searchTerm, 5);
            log.info("Expanded query '{}' to terms: {}", searchTerm, expandedTerms);

            // --- 2. Get semantic embedding for original query
            float[] queryEmbedding = embeddingService.embed(searchTerm);
            if (queryEmbedding == null) {
                log.warn("Embedding service returned null, falling back to lexical only");
                return persistentMemoryStore.findMemoriesByMarkings(markingsFilter, requestingUserId)
                    .stream().limit(limit).collect(Collectors.toList());
            }

            String embeddingString = Arrays.toString(queryEmbedding);

            // --- 3. Run lexical searches for all expanded terms
            // Use LinkedHashSet to maintain insertion order for predictable results
            Set<AgentMemory> allLexicalResults = new LinkedHashSet<>();
            for (String term : expandedTerms) {
                List<AgentMemory> termResults = persistentMemoryStore.lexicalSearch(term, requestingUserId);
                allLexicalResults.addAll(termResults);
                log.debug("Lexical search for '{}' found {} results", term, termResults.size());
            }

            // --- 4. Run semantic search with original query embedding
            List<AgentMemory> semanticResults = (markingsFilter != null && !markingsFilter.trim().isEmpty())
                ? agentMemoryRepository.findSimilarMemoriesByMarkings(embeddingString, markingsFilter, limit * 3)
                : agentMemoryRepository.findSimilarMemories(embeddingString, limit * 3);

            log.info("Lexical (expanded) found {}, Semantic found {}", allLexicalResults.size(), semanticResults.size());

            // --- 5. Score + normalize
            Map<Long, Double> scores = new HashMap<>();

            // Boost lexical matches (exact/partial text matches are highly relevant)
            for (AgentMemory m : allLexicalResults) {
                scores.put(m.getId(), 1.5);
            }

            // Filter + score semantic matches with the effective threshold
            List<AgentMemory> filteredSemantic = semanticResults.stream()
                .filter(m -> m.getEmbedding() != null)
                .filter(m -> {
                    float sim = CosineSimilarity.score(queryEmbedding, m.getEmbedding());
                    double normalized = (sim + 1) / 2.0;
                    log.debug("Semantic match - ID: {}, key: {}, similarity: {}, normalized: {}", 
                             m.getId(), m.getMemoryKey(), sim, normalized);
                    if (normalized >= effectiveThreshold) {
                        scores.merge(m.getId(), normalized, Double::sum);
                        return true;
                    }
                    return false;
                })
                .toList();

            log.info("After threshold filtering: {} semantic results passed (threshold: {})", 
                     filteredSemantic.size(), effectiveThreshold);

            // --- 6. Merge + dedupe + sort
            // Convert to List for better stream performance
            List<AgentMemory> lexicalList = new ArrayList<>(allLexicalResults);
            Set<Long> seen = new HashSet<>();
            List<AgentMemory> results = Stream.concat(lexicalList.stream(), filteredSemantic.stream())
                .filter(m -> seen.add(m.getId())) // dedupe by ID
                .filter(m -> !m.isExpired())
                .filter(m -> !isExcludedMemoryKey(m.getMemoryKey())) // Exclude temporary lookup results
                .filter(m -> accessControlService.canAccessMemory(m, evaluator, requestingUserId, null, "READ"))
                .sorted((a, b) -> Double.compare(
                    scores.getOrDefault(b.getId(), 0.0),
                    scores.getOrDefault(a.getId(), 0.0)))
                .limit(limit)
                .toList();

            log.info("Hybrid search returned {} results for query '{}'", results.size(), searchTerm);
            return results;

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

    public List<AgentMemoryDTO> generateEmbeddings(List<AgentMemoryDTO> memories) {
        // Create text for embedding from memory content and metadata
        List<String> textsForEmbedding = memories.stream()
                .map(this::buildTextForEmbedding)
                .collect(Collectors.toList());

        // Generate embedding
        List<float[]> embeddings = embeddingService.embed(textsForEmbedding);

        if (embeddings == null || embeddings.size() != memories.size()) {
            throw new RuntimeException("Failed to generate embeddings");
        }
        for(int i=0; i<memories.size(); i++) {
            memories.get(i).setEmbedding(embeddings.get(i));
            memories.get(i).setHasEmbedding(true);
        }
        return memories;
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

    private String buildTextForEmbedding(AgentMemoryDTO memory) {
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

    public AgentMemory storeMemoryWithProvidedEmbedding(String agentId, String memoryKey, String memoryValue, String classification, String[] markings, float[] embedding, String userId) {
        AgentMemory memory = persistentMemoryStore.storeMemory(agentId, memoryKey, memoryValue, classification, markings, userId);
        memory.setEmbedding(embedding);
        return agentMemoryRepository.save(memory);
    }

    /**
     * VectorMemoryStore: merge memory across generations with weighted decay.
     * Retrieves memories from the current agent and its ancestors, applying decay based on generational distance.
     *
     * @param agentId The current agent ID
     * @param queryText The search query
     * @param requestingUserId The user requesting access
     * @param maxGenerations The maximum number of generations to traverse (default 3)
     * @param limit The maximum number of results to return
     * @param threshold The similarity threshold
     * @return List of memories composed across generations with weighted scores
     */
    public List<AgentMemory> getComposedMemory(String agentId, String queryText, String requestingUserId, 
                                                int maxGenerations, int limit, double threshold) {
        log.info("Getting composed memory for agent: {}, maxGenerations: {}", agentId, maxGenerations);

        if (embeddingService == null || !embeddingService.isAvailable()) {
            log.debug("No embedding service available - returning current agent memories only");
            return findSimilarMemoriesForAgent(queryText, agentId, requestingUserId, limit, threshold);
        }

        try {
            // Generate query embedding
            float[] queryEmbedding = embeddingService.embed(queryText);
            if (queryEmbedding == null) {
                return findSimilarMemoriesForAgent(queryText, agentId, requestingUserId, limit, threshold);
            }

            String embeddingString = Arrays.toString(queryEmbedding);

            // Retrieve memories from current agent
            List<AgentMemory> currentMemories = agentMemoryRepository.findSimilarMemoriesForAgent(
                    embeddingString, agentId, threshold, limit * 2);

            // Retrieve inherited memories (from parent generations)
            List<AgentMemory> inheritedMemories = agentMemoryRepository
                    .findByAgentIdAndMarkingsContaining(agentId, "INHERITED");

            // Combine and score all memories with generational decay
            Map<Long, ScoredMemory> scoredMemories = new HashMap<>();

            // Score current generation memories (no decay)
            for (AgentMemory memory : currentMemories) {
                if (!memory.isExpired()) {
                    double similarity = memory.calculateCosineSimilarity(queryEmbedding);
                    if (similarity >= threshold) {
                        scoredMemories.put(memory.getId(), new ScoredMemory(memory, similarity, 1.0));
                    }
                }
            }

            // Score inherited memories with decay
            for (AgentMemory memory : inheritedMemories) {
                if (!memory.isExpired()) {
                    double similarity = memory.calculateCosineSimilarity(queryEmbedding);
                    if (similarity >= threshold) {
                        // Extract decay factor from metadata
                        double decayFactor = extractDecayFactor(memory);
                        double weightedScore = similarity * decayFactor;
                        
                        // Only add if not already present or has higher score
                        ScoredMemory existing = scoredMemories.get(memory.getId());
                        if (existing == null || weightedScore > existing.weightedScore) {
                            scoredMemories.put(memory.getId(), new ScoredMemory(memory, similarity, decayFactor));
                        }
                    }
                }
            }

            // Sort by weighted score and return top results
            return scoredMemories.values().stream()
                    .sorted((a, b) -> Double.compare(b.getWeightedScore(), a.getWeightedScore()))
                    .filter(sm -> !isExcludedMemoryKey(sm.memory.getMemoryKey()))
                    .filter(sm -> accessControlService.canAccessMemory(sm.memory, requestingUserId, agentId, "READ"))
                    .map(sm -> sm.memory)
                    .limit(limit)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Error in composed memory retrieval", e);
            return findSimilarMemoriesForAgent(queryText, agentId, requestingUserId, limit, threshold);
        }
    }

    /**
     * Extracts the decay factor from memory metadata.
     */
    private double extractDecayFactor(AgentMemory memory) {
        try {
            Map<String, Object> metadata = memory.getMetadataAsMap();
            if (metadata.containsKey("decay_factor")) {
                Object decayObj = metadata.get("decay_factor");
                if (decayObj instanceof Number) {
                    return ((Number) decayObj).doubleValue();
                }
            }
        } catch (Exception e) {
            log.debug("Could not extract decay factor from memory {}, using default", memory.getId());
        }
        return 0.9; // Default decay factor
    }

    /**
     * Helper class to track memories with their similarity scores and decay factors.
     */
    private static class ScoredMemory {
        final AgentMemory memory;
        final double similarityScore;
        final double decayFactor;
        final double weightedScore;

        ScoredMemory(AgentMemory memory, double similarityScore, double decayFactor) {
            this.memory = memory;
            this.similarityScore = similarityScore;
            this.decayFactor = decayFactor;
            this.weightedScore = similarityScore * decayFactor;
        }

        double getWeightedScore() {
            return weightedScore;
        }
    }
}