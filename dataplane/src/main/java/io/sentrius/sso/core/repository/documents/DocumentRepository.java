package io.sentrius.sso.core.repository.documents;

import io.sentrius.sso.core.model.documents.Document;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for Document entities.
 */
@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {

    // === Basic Finders ===

    Optional<Document> findByDocumentName(String documentName);

    List<Document> findByDocumentTypeOrderByCreatedAtDesc(String documentType);

    List<Document> findByCreatedByOrderByCreatedAtDesc(String createdBy);

    Page<Document> findByDocumentTypeOrderByCreatedAtDesc(String documentType, Pageable pageable);

    // === Tag Search ===

    @Query("SELECT d FROM Document d WHERE d.tags LIKE %:tag%")
    List<Document> findByTagsContaining(@Param("tag") String tag);

    // === Classification ===

    List<Document> findByClassificationOrderByCreatedAtDesc(String classification);

    @Query("SELECT d FROM Document d WHERE d.markings LIKE %:marking%")
    List<Document> findByMarkingsContaining(@Param("marking") String marking);

    // === Text Search ===

    @Query("SELECT d FROM Document d WHERE " +
           "LOWER(d.documentName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(d.content) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(d.summary) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    List<Document> searchByContent(@Param("searchTerm") String searchTerm);

    // === Vector Search ===

    @Query(value = """
        SELECT * FROM documents d
        WHERE d.embedding IS NOT NULL
        ORDER BY d.embedding <-> CAST(:embedding AS vector)
        LIMIT :limit
        """, nativeQuery = true)
    List<Document> findSimilarDocuments(@Param("embedding") String embedding, @Param("limit") int limit);

    @Query(value = """
        SELECT * FROM documents d
        WHERE d.embedding IS NOT NULL
          AND d.document_type = :documentType
        ORDER BY d.embedding <-> CAST(:embedding AS vector)
        LIMIT :limit
        """, nativeQuery = true)
    List<Document> findSimilarDocumentsByType(@Param("embedding") String embedding, 
                                               @Param("documentType") String documentType, 
                                               @Param("limit") int limit);

    @Query(value = """
        SELECT * FROM documents d
        WHERE d.embedding IS NOT NULL
          AND d.markings LIKE %:markings%
        ORDER BY d.embedding <-> CAST(:embedding AS vector)
        LIMIT :limit
        """, nativeQuery = true)
    List<Document> findSimilarDocumentsByMarkings(@Param("embedding") String embedding, 
                                                   @Param("markings") String markings, 
                                                   @Param("limit") int limit);

    // === Statistics ===

    @Query("SELECT COUNT(d) FROM Document d WHERE d.embedding IS NOT NULL")
    long countDocumentsWithEmbeddings();

    @Query(value = "SELECT * FROM documents d WHERE d.embedding IS NULL LIMIT :limit", nativeQuery = true)
    List<Document> findDocumentsWithoutEmbeddings(@Param("limit") int limit);

    // === Checksum for deduplication ===

    Optional<Document> findByChecksum(String checksum);

    boolean existsByChecksum(String checksum);

    // === Recent Documents for Analysis ===

    @Query(value = """
        SELECT * FROM documents d 
        WHERE d.created_at >= NOW() - MAKE_INTERVAL(mins => :minutes)
        ORDER BY d.created_at DESC
        LIMIT 100
        """, nativeQuery = true)
    List<Document> findRecentDocuments(@Param("minutes") int minutes);

    @Query("SELECT d FROM Document d ORDER BY d.createdAt DESC")
    List<Document> findAllOrderByCreatedAtDesc();
}
