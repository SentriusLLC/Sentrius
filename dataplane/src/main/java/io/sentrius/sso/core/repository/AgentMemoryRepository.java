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

    // === Basic Finders ===

    List<AgentMemory> findByAgentIdOrderByCreatedAtDesc(String agentId);

    List<AgentMemory> findByConversationIdOrderByCreatedAtDesc(String conversationId);

    Optional<AgentMemory> findByMemoryKey(String memoryKey);

    Optional<AgentMemory> findByAgentIdAndMemoryKey(String agentId, String memoryKey);

    List<AgentMemory> findByClassificationOrderByCreatedAtDesc(String classification);

    List<AgentMemory> findByAccessLevelOrderByCreatedAtDesc(String accessLevel);

    List<AgentMemory> findByCreatorUserIdOrderByCreatedAtDesc(String creatorUserId);

    // === Sharing ===

    @Query("""
        SELECT m FROM AgentMemory m
        WHERE (m.accessLevel = 'ALL_USERS' OR m.agentId = :agentId OR m.sharedWithAgents LIKE %:agentId%)
          AND (m.expiresAt IS NULL OR m.expiresAt > :now)
        """)
    List<AgentMemory> findShareableMemories(@Param("agentId") String agentId, @Param("now") Instant now);

    // === Markings ===

    @Query("SELECT m FROM AgentMemory m WHERE m.markings LIKE %:marking%")
    List<AgentMemory> findByMarkingsContaining(@Param("marking") String marking);

    // === JPQL filterable query ===

    @Query("""
        SELECT m FROM AgentMemory m
        WHERE (:agentId IS NULL OR m.agentId = :agentId)
          AND (:classification IS NULL OR m.classification = :classification)
          AND (:markings IS NULL OR m.markings LIKE CONCAT('%', :markings, '%'))
          AND (m.expiresAt IS NULL OR m.expiresAt > :now)
        """)
    Page<AgentMemory> findMemoriesWithFilters(
        @Param("agentId") String agentId,
        @Param("classification") String classification,
        @Param("markings") String markings,
        @Param("now") Instant now,
        Pageable pageable);

    // === Native filterable query (explicit casting for Postgres) ===

    @Query(
        value = """
            SELECT * 
            FROM agent_memory m
            WHERE (:agentId IS NULL OR m.agent_id = :agentId)
              AND (:classification IS NULL OR m.classification = :classification)
              AND (:markings IS NULL OR m.markings LIKE CONCAT('%', CAST(:markings AS VARCHAR), '%'))
              AND (m.expires_at IS NULL OR m.expires_at > :now)
            ORDER BY m.created_at DESC
            """,
        countQuery = """
            SELECT COUNT(*) 
            FROM agent_memory m
            WHERE (:agentId IS NULL OR m.agent_id = :agentId)
              AND (:classification IS NULL OR m.classification = :classification)
              AND (:markings IS NULL OR m.markings LIKE CONCAT('%', CAST(:markings AS VARCHAR), '%'))
              AND (m.expires_at IS NULL OR m.expires_at > :now)
            """,
        nativeQuery = true
    )
    Page<AgentMemory> findMemoriesWithFiltersNative(
        @Param("agentId") String agentId,
        @Param("classification") String classification,
        @Param("markings") String markings,
        @Param("now") Instant now,
        Pageable pageable);


    @Query(
        value = """
        SELECT * 
        FROM agent_memory m
        WHERE (:agentId IS NULL OR m.agent_id = :agentId)
          AND (:classification IS NULL OR m.classification = :classification)
          AND (:markings IS NULL OR m.markings LIKE CONCAT('%', CAST(:markings AS VARCHAR), '%'))
          AND (m.expires_at IS NULL OR m.expires_at > :now)
        ORDER BY m.embedding <#> CAST(:embedding AS vector)
        LIMIT :limit
        """,
        nativeQuery = true
    )
    List<AgentMemory> findNearestMemories(
        @Param("embedding") String embedding,
        @Param("agentId") String agentId,
        @Param("classification") String classification,
        @Param("markings") String markings,
        @Param("now") Instant now,
        @Param("limit") int limit
    );

    // === Expiration ===

    @Query("SELECT m FROM AgentMemory m WHERE m.expiresAt IS NULL OR m.expiresAt > :now")
    List<AgentMemory> findNonExpiredMemories(@Param("now") Instant now);

    @Query("SELECT m FROM AgentMemory m WHERE m.expiresAt IS NOT NULL AND m.expiresAt <= :now")
    List<AgentMemory> findExpiredMemories(@Param("now") Instant now);

    void deleteByExpiresAtLessThanEqual(Instant expiredBefore);

    // === Search ===

    @Query("SELECT m FROM AgentMemory m WHERE LOWER(m.memoryValue) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    List<AgentMemory> searchByMemoryValue(@Param("searchTerm") String searchTerm);

    // === Counts ===

    long countByAgentId(String agentId);

    long countByClassification(String classification);

    @Query(
        value = "SELECT COUNT(*) FROM agent_memory WHERE embedding IS NOT NULL",
        nativeQuery = true
    )
    long countMemoriesWithEmbeddings();


    // === Embeddings ===

    @Query(
        value = "SELECT * FROM agent_memory WHERE embedding IS NULL ORDER BY created_at DESC",
        nativeQuery = true
    )
    List<AgentMemory> findMemoriesWithoutEmbeddings(Pageable pageable);

    // === Vector similarity searches ===

    @Query(value = """
        SELECT * FROM agent_memory
        WHERE embedding IS NOT NULL
        ORDER BY embedding <=> CAST(:queryEmbedding AS vector)
        LIMIT :limit
        """, nativeQuery = true)
    List<AgentMemory> findSimilarMemories(@Param("queryEmbedding") String queryEmbedding,
                                          @Param("limit") int limit);

    @Query(value = """
        SELECT * FROM agent_memory
        WHERE embedding IS NOT NULL
          AND classification = :classification
        ORDER BY embedding <=> CAST(:queryEmbedding AS vector)
        LIMIT :limit
        """, nativeQuery = true)
    List<AgentMemory> findSimilarMemoriesByClassification(@Param("queryEmbedding") String queryEmbedding,
                                                          @Param("classification") String classification,
                                                          @Param("limit") int limit);

    @Query(value = """
        SELECT * FROM agent_memory
        WHERE embedding IS NOT NULL
          AND markings LIKE CONCAT('%', CAST(:markings AS VARCHAR), '%')
        ORDER BY embedding <=> CAST(:queryEmbedding AS vector)
        LIMIT :limit
        """, nativeQuery = true)
    List<AgentMemory> findSimilarMemoriesByMarkings(@Param("queryEmbedding") String queryEmbedding,
                                                    @Param("markings") String markings,
                                                    @Param("limit") int limit);

    @Query(value = """
        SELECT *, (embedding <=> CAST(:queryEmbedding AS vector)) AS distance
        FROM agent_memory
        WHERE embedding IS NOT NULL
          AND (agent_id = :agentId OR access_level = 'ALL_USERS' OR shared_with_agents LIKE CONCAT('%', CAST(:agentId AS VARCHAR), '%'))
          AND (embedding <=> CAST(:queryEmbedding AS vector)) < :threshold
        ORDER BY distance
        LIMIT :limit
        """, nativeQuery = true)
    List<AgentMemory> findSimilarMemoriesForAgent(@Param("queryEmbedding") String queryEmbedding,
                                                  @Param("agentId") String agentId,
                                                  @Param("threshold") double threshold,
                                                  @Param("limit") int limit);

    @Query(value = """
        SELECT *, (embedding <=> CAST(:queryEmbedding AS vector)) AS distance
        FROM agent_memory
        WHERE embedding IS NOT NULL
          AND (
            LOWER(memory_value) LIKE LOWER(CONCAT('%', :searchTerm, '%'))
            OR markings LIKE CONCAT('%', CAST(:searchTerm AS VARCHAR), '%')
            OR (embedding <=> CAST(:queryEmbedding AS vector)) < :threshold
          )
        ORDER BY
          CASE WHEN LOWER(memory_value) LIKE LOWER(CONCAT('%', :searchTerm, '%')) THEN 0 ELSE 1 END,
          distance
        LIMIT :limit
        """, nativeQuery = true)
    List<AgentMemory> hybridSearch(@Param("searchTerm") String searchTerm,
                                   @Param("queryEmbedding") String queryEmbedding,
                                   @Param("threshold") double threshold,
                                   @Param("limit") int limit);

    // Lexical (keyword) search on content
    @Query(value = """
    SELECT * 
    FROM agent_memory m
    WHERE (LOWER(m.memory_value) LIKE LOWER(CONCAT('%', :searchTerm, '%'))
           OR LOWER(m.memory_key) LIKE LOWER(CONCAT('%', :searchTerm, '%'))
           OR LOWER(CAST(m.metadata AS TEXT)) LIKE LOWER(CONCAT('%', :searchTerm, '%')))
      AND (m.expires_at IS NULL OR m.expires_at > :now)
    ORDER BY m.created_at DESC
    LIMIT :limit
    """,
        nativeQuery = true)
    List<AgentMemory> lexicalSearch(
        @Param("searchTerm") String searchTerm,
        @Param("now") Instant now,
        @Param("limit") int limit);
}

