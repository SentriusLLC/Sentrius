package io.sentrius.sso.core.services.documents;

import io.sentrius.sso.core.config.SystemOptions;
import io.sentrius.sso.core.dto.documents.DocumentSearchDTO;
import io.sentrius.sso.core.model.documents.Document;
import io.sentrius.sso.core.repository.documents.DocumentRepository;
import io.sentrius.sso.core.services.agents.EmbeddingService;
import io.sentrius.sso.core.services.security.KeycloakService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * Test for document search returning empty results when no matches found.
 * This test validates the fix for the issue: "document search always returns hits"
 */
@ExtendWith(MockitoExtension.class)
class DocumentSearchEmptyResultsTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private EmbeddingService embeddingService;

    @Mock
    private KeycloakService keycloakService;

    @Mock
    private DocumentAccessControlService accessControlService;

    @Mock
    private KnowledgeGraphService knowledgeGraphService;

    private DocumentService documentService;
    private SystemOptions systemOptions = new SystemOptions();

    @BeforeEach
    void setUp() {
        documentService = new DocumentService(documentRepository, embeddingService, 
                keycloakService, systemOptions, accessControlService, knowledgeGraphService);
    }

    @Test
    void testSearchDocuments_NoMatchesReturnsEmptyList() {
        // Arrange: Search for non-existent content
        DocumentSearchDTO searchDTO = DocumentSearchDTO.builder()
                .query("NONEXISTENT_CONTENT_QUERY_12345")
                .useSemanticSearch(false)
                .build();

        // Mock repository to return empty list (no matches)
        when(documentRepository.searchByContent(anyString()))
                .thenReturn(Collections.emptyList());

        // Act
        List<Document> results = documentService.searchDocuments(searchDTO);

        // Assert: Should return empty list, not fall back to all documents
        assertNotNull(results, "Results should not be null");
        assertTrue(results.isEmpty(), 
                "Search with no matches should return empty list, not all documents");
    }

    @Test
    void testSearchDocuments_WithTypeFilterNoMatches() {
        // Arrange: Search with type filter that has no matches
        DocumentSearchDTO searchDTO = DocumentSearchDTO.builder()
                .query("test")
                .documentType("NONEXISTENT_TYPE")
                .useSemanticSearch(false)
                .build();

        // Mock repository to return document with different type
        Document doc = Document.builder()
                .id(1L)
                .documentName("Test")
                .documentType("TSG")
                .build();
        when(documentRepository.searchByContent(anyString()))
                .thenReturn(List.of(doc));

        // Act
        List<Document> results = documentService.searchDocuments(searchDTO);

        // Assert: Filter should exclude the result
        assertNotNull(results);
        assertTrue(results.isEmpty(), 
                "Search with non-matching type filter should return empty list");
    }

    @Test
    void testSearchDocuments_EmptyQueryWithTypeFilterNoMatches() {
        // Arrange: Empty query with type filter that has no matches
        DocumentSearchDTO searchDTO = DocumentSearchDTO.builder()
                .query("") // Empty query
                .documentType("NONEXISTENT_TYPE")
                .page(0)
                .size(20)
                .build();

        // Mock repository to return documents with different types
        Document doc1 = Document.builder().id(1L).documentType("TSG").build();
        Document doc2 = Document.builder().id(2L).documentType("MANUAL").build();
        
        Page<Document> page = new PageImpl<>(List.of(doc1, doc2));
        when(documentRepository.findAll(any(Pageable.class))).thenReturn(page);

        // Act
        List<Document> results = documentService.searchDocuments(searchDTO);

        // Assert: Filter should exclude all results
        assertNotNull(results);
        assertTrue(results.isEmpty(), 
                "Search with non-matching type filter should return empty list even with empty query");
    }

    @Test
    void testSearchDocuments_EmptyQueryWithMarkingsFilterNoMatches() {
        // Arrange: Empty query with markings filter that has no matches
        DocumentSearchDTO searchDTO = DocumentSearchDTO.builder()
                .query(null) // Null query
                .markings("NONEXISTENT_MARKINGS")
                .page(0)
                .size(20)
                .build();

        // Mock repository to return documents with different markings
        Document doc1 = Document.builder().id(1L).markings("ABC").build();
        Document doc2 = Document.builder().id(2L).markings("DEF").build();
        
        Page<Document> page = new PageImpl<>(List.of(doc1, doc2));
        when(documentRepository.findAll(any(Pageable.class))).thenReturn(page);

        // Act
        List<Document> results = documentService.searchDocuments(searchDTO);

        // Assert: Filter should exclude all results
        assertNotNull(results);
        assertTrue(results.isEmpty(), 
                "Search with non-matching markings filter should return empty list even with null query");
    }

    @Test
    void testSearchDocuments_MultipleFiltersNoMatches() {
        // Arrange: Search with multiple filters that don't match
        DocumentSearchDTO searchDTO = DocumentSearchDTO.builder()
                .query("test")
                .documentType("TSG")
                .markings("SPECIAL")
                .useSemanticSearch(false)
                .build();

        // Mock repository to return documents that match query but not all filters
        Document doc1 = Document.builder()
                .id(1L)
                .documentType("TSG")
                .markings("PUBLIC") // Wrong markings
                .content("test content")
                .build();
        Document doc2 = Document.builder()
                .id(2L)
                .documentType("MANUAL") // Wrong type
                .markings("SPECIAL")
                .content("test content")
                .build();
        
        when(documentRepository.searchByContent(anyString()))
                .thenReturn(List.of(doc1, doc2));

        // Act
        List<Document> results = documentService.searchDocuments(searchDTO);

        // Assert: No documents should match all filters
        assertNotNull(results);
        assertTrue(results.isEmpty(), 
                "Search with multiple non-matching filters should return empty list");
    }

    @Test
    void testSearchDocuments_HybridSearch_WithNoTextMatches() {
        // Arrange: Hybrid search where vector search returns docs but text search doesn't
        DocumentSearchDTO searchDTO = DocumentSearchDTO.builder()
                .query("xyznonexistent")
                .useSemanticSearch(true)
                .threshold(0.7)
                .limit(10)
                .build();

        // Mock: Text search returns empty (no exact matches)
        when(documentRepository.searchByContent(anyString()))
                .thenReturn(Collections.emptyList());

        // Mock: Embedding service returns embedding
        float[] queryEmbedding = new float[1536];
        Arrays.fill(queryEmbedding, 0.1f);
        when(embeddingService.isAvailable()).thenReturn(true);
        when(embeddingService.embed(anyString())).thenReturn(queryEmbedding);

        // Mock: Vector search returns 2 documents with low similarity
        Document doc1 = Document.builder()
                .id(1L)
                .documentName("Doc 1")
                .content("unrelated content")
                .embedding(new float[1536]) // All zeros = very low similarity
                .build();
        Document doc2 = Document.builder()
                .id(2L)
                .documentName("Doc 2")
                .content("different content")
                .embedding(new float[1536]) // All zeros = very low similarity
                .build();

        when(documentRepository.findSimilarDocuments(anyString(), anyInt()))
                .thenReturn(List.of(doc1, doc2));

        // Act
        List<Document> results = documentService.searchDocuments(searchDTO);

        // Assert: Should return empty or only high-similarity docs (above threshold)
        // With all-zero embeddings vs 0.1f query, similarity will be very low (below 0.7 threshold)
        assertTrue(results.isEmpty() || results.size() < 2,
                "Hybrid search should filter out documents below similarity threshold");
    }
}
