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

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for DocumentService.
 */
@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private EmbeddingService embeddingService;

    private DocumentService documentService;

    private SystemOptions systemOptions = new SystemOptions();

    @Mock
    private KeycloakService keycloakService;

    @BeforeEach
    void setUp() {
        documentService = new DocumentService(documentRepository, embeddingService, keycloakService, systemOptions);
    }

    @Test
    void testStoreDocument_Success() {
        // Arrange
        String documentName = "Test TSG";
        String documentType = "TSG";
        String content = "This is a test troubleshooting guide";
        String contentType = "text/plain";
        String summary = "Test summary";
        String[] tags = {"test", "tsg"};
        String classification = "UNCLASSIFIED";
        String markings = "PUBLIC";
        String createdBy = "test-user";

        Document savedDocument = Document.builder()
                .id(1L)
                .documentName(documentName)
                .documentType(documentType)
                .content(content)
                .build();

        when(documentRepository.findByChecksum(anyString())).thenReturn(Optional.empty());
        when(documentRepository.save(any(Document.class))).thenReturn(savedDocument);
        when(embeddingService.isAvailable()).thenReturn(false);

        // Act
        Document result = documentService.storeDocument(documentName, documentType, content,
                contentType, summary, tags, classification, markings, createdBy);

        // Assert
        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals(documentName, result.getDocumentName());
        assertEquals(documentType, result.getDocumentType());
        verify(documentRepository).save(any(Document.class));
    }

    @Test
    void testStoreDocument_DuplicateChecksum() {
        // Arrange
        String content = "Duplicate content";
        Document existingDocument = Document.builder()
                .id(1L)
                .documentName("Existing")
                .content(content)
                .build();

        when(documentRepository.findByChecksum(anyString())).thenReturn(Optional.of(existingDocument));

        // Act
        Document result = documentService.storeDocument("New Doc", "TSG", content,
                "text/plain", null, null, null, null, "user");

        // Assert
        assertNotNull(result);
        assertEquals(1L, result.getId());
        verify(documentRepository, never()).save(any(Document.class));
    }

    @Test
    void testStoreDocument_WithEmbedding() {
        // Arrange
        String content = "Content for embedding";
        Document savedDocument = Document.builder()
                .id(1L)
                .documentName("Test")
                .content(content)
                .build();

        float[] mockEmbedding = new float[1536];
        Arrays.fill(mockEmbedding, 0.1f);

        when(documentRepository.findByChecksum(anyString())).thenReturn(Optional.empty());
        when(documentRepository.save(any(Document.class))).thenReturn(savedDocument);
        when(embeddingService.isAvailable()).thenReturn(true);
        when(embeddingService.embed(anyString())).thenReturn(mockEmbedding);

        // Act
        Document result = documentService.storeDocument("Test", "TSG", content,
                "text/plain", null, null, null, null, "user");

        // Assert
        assertNotNull(result);
        verify(embeddingService).embed(anyString());
        verify(documentRepository, times(2)).save(any(Document.class)); // Once for initial save, once for embedding
    }

    @Test
    void testGetDocument_Found() {
        // Arrange
        Long id = 1L;
        Document document = Document.builder().id(id).documentName("Test").build();
        when(documentRepository.findById(id)).thenReturn(Optional.of(document));

        // Act
        Optional<Document> result = documentService.getDocument(id);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(id, result.get().getId());
        verify(documentRepository).findById(id);
    }

    @Test
    void testGetDocument_NotFound() {
        // Arrange
        Long id = 999L;
        when(documentRepository.findById(id)).thenReturn(Optional.empty());

        // Act
        Optional<Document> result = documentService.getDocument(id);

        // Assert
        assertFalse(result.isPresent());
        verify(documentRepository).findById(id);
    }

    @Test
    void testSearchDocuments_TextOnly() {
        // Arrange
        DocumentSearchDTO searchDTO = DocumentSearchDTO.builder()
                .query("test query")
                .useSemanticSearch(false)
                .limit(10)
                .build();

        Document doc1 = Document.builder().id(1L).documentName("Doc1").build();
        Document doc2 = Document.builder().id(2L).documentName("Doc2").build();
        List<Document> expectedResults = Arrays.asList(doc1, doc2);

        when(documentRepository.searchByContent("test query")).thenReturn(expectedResults);

        // Act
        List<Document> results = documentService.searchDocuments(searchDTO);

        // Assert
        assertEquals(2, results.size());
        verify(documentRepository).searchByContent("test query");
    }

    @Test
    void testGetDocumentsByType() {
        // Arrange
        String documentType = "TSG";
        Document doc1 = Document.builder().id(1L).documentType(documentType).build();
        Document doc2 = Document.builder().id(2L).documentType(documentType).build();
        List<Document> expectedResults = Arrays.asList(doc1, doc2);

        when(documentRepository.findByDocumentTypeOrderByCreatedAtDesc(documentType))
                .thenReturn(expectedResults);

        // Act
        List<Document> results = documentService.getDocumentsByType(documentType);

        // Assert
        assertEquals(2, results.size());
        assertEquals(documentType, results.get(0).getDocumentType());
        verify(documentRepository).findByDocumentTypeOrderByCreatedAtDesc(documentType);
    }

    @Test
    void testGetDocumentsByTag() {
        // Arrange
        String tag = "troubleshooting";
        Document doc1 = Document.builder().id(1L).tags("ssh,troubleshooting").build();
        List<Document> expectedResults = Collections.singletonList(doc1);

        when(documentRepository.findByTagsContaining(tag)).thenReturn(expectedResults);

        // Act
        List<Document> results = documentService.getDocumentsByTag(tag);

        // Assert
        assertEquals(1, results.size());
        verify(documentRepository).findByTagsContaining(tag);
    }

    @Test
    void testUpdateDocument_Success() {
        // Arrange
        Long id = 1L;
        String newContent = "Updated content";
        String newSummary = "Updated summary";
        String[] newTags = {"updated", "tags"};

        Document existingDocument = Document.builder()
                .id(id)
                .documentName("Test")
                .content("Old content")
                .build();

        when(documentRepository.findById(id)).thenReturn(Optional.of(existingDocument));
        when(documentRepository.save(any(Document.class))).thenReturn(existingDocument);
        when(embeddingService.isAvailable()).thenReturn(false);

        // Act
        Document result = documentService.updateDocument(id, newContent, newSummary, newTags);

        // Assert
        assertNotNull(result);
        assertEquals(newContent, result.getContent());
        assertEquals(newSummary, result.getSummary());
        verify(documentRepository).save(existingDocument);
    }

    @Test
    void testUpdateDocument_NotFound() {
        // Arrange
        Long id = 999L;
        when(documentRepository.findById(id)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(RuntimeException.class, () -> 
                documentService.updateDocument(id, "content", "summary", null));
    }

    @Test
    void testDeleteDocument_Success() {
        // Arrange
        Long id = 1L;
        when(documentRepository.existsById(id)).thenReturn(true);

        // Act
        boolean result = documentService.deleteDocument(id);

        // Assert
        assertTrue(result);
        verify(documentRepository).deleteById(id);
    }

    @Test
    void testDeleteDocument_NotFound() {
        // Arrange
        Long id = 999L;
        when(documentRepository.existsById(id)).thenReturn(false);

        // Act
        boolean result = documentService.deleteDocument(id);

        // Assert
        assertFalse(result);
        verify(documentRepository, never()).deleteById(anyLong());
    }

    @Test
    void testAnalyzeDocument() {
        // Arrange
        String content = "This is a test document with some content for analysis";

        // Act
        Map<String, Object> analysis = documentService.analyzeDocument(content);

        // Assert
        assertNotNull(analysis);
        assertTrue(analysis.containsKey("word_count"));
        assertTrue(analysis.containsKey("character_count"));
        assertTrue(analysis.containsKey("suggested_tags"));
        assertTrue((Integer) analysis.get("word_count") > 0);
        assertTrue((Integer) analysis.get("character_count") > 0);
    }

    @Test
    void testGetStatistics() {
        // Arrange
        when(documentRepository.count()).thenReturn(100L);
        when(documentRepository.countDocumentsWithEmbeddings()).thenReturn(75L);
        when(embeddingService.isAvailable()).thenReturn(true);

        // Act
        Map<String, Object> stats = documentService.getStatistics();

        // Assert
        assertNotNull(stats);
        assertEquals(100L, stats.get("total_documents"));
        assertEquals(75L, stats.get("documents_with_embeddings"));
        assertEquals(75.0, stats.get("embedding_coverage_percentage"));
        assertEquals(true, stats.get("embedding_service_available"));
    }

    @Test
    void testGenerateMissingEmbeddings() {
        // Arrange
        int batchSize = 10;
        Document doc1 = Document.builder().id(1L).content("Content 1").build();
        Document doc2 = Document.builder().id(2L).content("Content 2").build();
        List<Document> documentsWithoutEmbeddings = Arrays.asList(doc1, doc2);

        float[] mockEmbedding = new float[1536];
        Arrays.fill(mockEmbedding, 0.1f);

        when(embeddingService.isAvailable()).thenReturn(true);
        when(documentRepository.findDocumentsWithoutEmbeddings(batchSize))
                .thenReturn(documentsWithoutEmbeddings);
        when(embeddingService.embed(anyString())).thenReturn(mockEmbedding);
        when(documentRepository.save(any(Document.class))).thenReturn(doc1);

        // Act
        documentService.generateMissingEmbeddings(batchSize);

        // Assert
        verify(embeddingService, times(2)).embed(anyString());
        verify(documentRepository, times(2)).save(any(Document.class));
    }
}
