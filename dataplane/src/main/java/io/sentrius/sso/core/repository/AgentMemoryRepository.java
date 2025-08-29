package io.sentrius.sso.core.repository;

import io.sentrius.sso.core.model.agents.AgentMemory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface AgentMemoryRepository extends JpaRepository<AgentMemory, Long> {

    // Find by agent ID
    List<AgentMemory> findByAgentIdOrderByCreatedAtDesc(String agentId);
    
    // Find by conversation ID
    List<AgentMemory> findByConversationIdOrderByCreatedAtDesc(String conversationId);
    
    // Find by memory key
    Optional<AgentMemory> findByMemoryKey(String memoryKey);
    
    // Find by agent and memory key
    Optional<AgentMemory> findByAgentIdAndMemoryKey(String agentId, String memoryKey);
    
    // Find by classification
    List<AgentMemory> findByClassificationOrderByCreatedAtDesc(String classification);
    
    // Find by access level
    List<AgentMemory> findByAccessLevelOrderByCreatedAtDesc(String accessLevel);
    
    // Find by creator
    List<AgentMemory> findByCreatorUserIdOrderByCreatedAtDesc(String creatorUserId);
    
    // Find shareable memories for an agent
    @Query("SELECT m FROM AgentMemory m WHERE " +
           "(m.accessLevel = 'ALL_USERS' OR " +
           "m.agentId = :agentId OR " +
           "m.sharedWithAgents LIKE %:agentId%) AND " +
           "(m.expiresAt IS NULL OR m.expiresAt > :now)")
    List<AgentMemory> findShareableMemories(@Param("agentId") String agentId, @Param("now") Instant now);
    
    // Find memories by markings
    @Query("SELECT m FROM AgentMemory m WHERE m.markings LIKE %:marking%")
    List<AgentMemory> findByMarkingsContaining(@Param("marking") String marking);
    
    // Find memories with multiple filters
    @Query("SELECT m FROM AgentMemory m WHERE " +
           "(:agentId IS NULL OR m.agentId = :agentId) AND " +
           "(:classification IS NULL OR m.classification = :classification) AND " +
           "(:markings IS NULL OR m.markings LIKE %:markings%) AND " +
           "(m.expiresAt IS NULL OR m.expiresAt > :now)")
    Page<AgentMemory> findMemoriesWithFilters(
        @Param("agentId") String agentId,
        @Param("classification") String classification,
        @Param("markings") String markings,
        @Param("now") Instant now,
        Pageable pageable);
    
    // Find non-expired memories
    @Query("SELECT m FROM AgentMemory m WHERE m.expiresAt IS NULL OR m.expiresAt > :now")
    List<AgentMemory> findNonExpiredMemories(@Param("now") Instant now);
    
    // Find expired memories for cleanup
    @Query("SELECT m FROM AgentMemory m WHERE m.expiresAt IS NOT NULL AND m.expiresAt <= :now")
    List<AgentMemory> findExpiredMemories(@Param("now") Instant now);
    
    // Search memories by value content
    @Query("SELECT m FROM AgentMemory m WHERE LOWER(m.memoryValue) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    List<AgentMemory> searchByMemoryValue(@Param("searchTerm") String searchTerm);
    
    // Count memories by agent
    long countByAgentId(String agentId);
    
    // Count memories by classification
    long countByClassification(String classification);
    
    // Delete expired memories
    void deleteByExpiresAtLessThanEqual(Instant expiredBefore);
    
    // Vector similarity search methods
    
    // Find similar memories using vector similarity (cosine distance)
    @Query(value = "SELECT * FROM agent_memory WHERE embedding IS NOT NULL " +
                  "ORDER BY embedding <=> CAST(:queryEmbedding AS vector) LIMIT :limit", 
           nativeQuery = true)
    List<AgentMemory> findSimilarMemories(@Param("queryEmbedding") String queryEmbedding, @Param("limit") int limit);
    
    // Find similar memories with classification filter
    @Query(value = "SELECT * FROM agent_memory WHERE embedding IS NOT NULL " +
                  "AND classification = :classification " +
                  "ORDER BY embedding <=> CAST(:queryEmbedding AS vector) LIMIT :limit", 
           nativeQuery = true)
    List<AgentMemory> findSimilarMemoriesByClassification(
        @Param("queryEmbedding") String queryEmbedding, 
        @Param("classification") String classification, 
        @Param("limit") int limit);
    
    // Find similar memories with markings filter
    @Query(value = "SELECT * FROM agent_memory WHERE embedding IS NOT NULL " +
                  "AND markings LIKE %:markings% " +
                  "ORDER BY embedding <=> CAST(:queryEmbedding AS vector) LIMIT :limit", 
           nativeQuery = true)
    List<AgentMemory> findSimilarMemoriesByMarkings(
        @Param("queryEmbedding") String queryEmbedding, 
        @Param("markings") String markings, 
        @Param("limit") int limit);
    
    // Find similar memories for a specific agent with distance threshold
    @Query(value = "SELECT *, (embedding <=> CAST(:queryEmbedding AS vector)) as distance " +
                  "FROM agent_memory WHERE embedding IS NOT NULL " +
                  "AND (agent_id = :agentId OR access_level = 'ALL_USERS' OR shared_with_agents LIKE %:agentId%) " +
                  "AND (embedding <=> CAST(:queryEmbedding AS vector)) < :threshold " +
                  "ORDER BY distance LIMIT :limit", 
           nativeQuery = true)
    List<AgentMemory> findSimilarMemoriesForAgent(
        @Param("queryEmbedding") String queryEmbedding,
        @Param("agentId") String agentId,
        @Param("threshold") double threshold,
        @Param("limit") int limit);
    
    // Hybrid search combining text and vector similarity
    @Query(value = "SELECT *, (embedding <=> CAST(:queryEmbedding AS vector)) as distance " +
                  "FROM agent_memory WHERE embedding IS NOT NULL " +
                  "AND (LOWER(memory_value) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
                  "     OR markings LIKE %:searchTerm% " +
                  "     OR (embedding <=> CAST(:queryEmbedding AS vector)) < :threshold) " +
                  "ORDER BY " +
                  "  CASE WHEN LOWER(memory_value) LIKE LOWER(CONCAT('%', :searchTerm, '%')) THEN 0 ELSE 1 END, " +
                  "  distance " +
                  "LIMIT :limit", 
           nativeQuery = true)
    List<AgentMemory> hybridSearch(
        @Param("searchTerm") String searchTerm,
        @Param("queryEmbedding") String queryEmbedding,
        @Param("threshold") double threshold,
        @Param("limit") int limit);
    
    // Count memories with embeddings
    @Query("SELECT COUNT(m) FROM AgentMemory m WHERE m.embedding IS NOT NULL")
    long countMemoriesWithEmbeddings();
    
    // Find memories without embeddings (for batch embedding generation)
    @Query("SELECT m FROM AgentMemory m WHERE m.embedding IS NULL ORDER BY m.createdAt DESC")
    List<AgentMemory> findMemoriesWithoutEmbeddings(Pageable pageable);
}