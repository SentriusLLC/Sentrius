package io.sentrius.sso.controllers.api.documents;

import io.sentrius.sso.core.config.SystemOptions;
import io.sentrius.sso.core.dto.documents.DocumentDTO;
import io.sentrius.sso.core.dto.documents.DocumentSearchDTO;
import io.sentrius.sso.core.model.documents.Document;
import io.sentrius.sso.core.model.users.User;
import io.sentrius.sso.core.services.ErrorOutputService;
import io.sentrius.sso.core.services.UserService;
import io.sentrius.sso.core.services.documents.DocumentService;
import io.sentrius.sso.core.utils.UIMessaging;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for DocumentController.
 */
@ExtendWith(MockitoExtension.class)
class DocumentControllerTest {

    @Mock
    private DocumentService documentService;

    @Mock
    private UserService userService;

    @Mock
    private SystemOptions systemOptions;

    @Mock
    private ErrorOutputService errorOutputService;

    private DocumentController documentController;

    @BeforeEach
    void setUp() {
        documentController = new DocumentController(documentService, userService, 
                systemOptions, errorOutputService);
        
        // Mock the user service to return a valid user
        User mockUser = new User();
        mockUser.setUserId("test-user");
        lenient().when(userService.getOperatingUser(any(), any(), any()))
                .thenReturn(mockUser);
    }

    @Test
    void testSearchDocuments_ReturnsResults() {
        // Arrange
        DocumentSearchDTO searchDTO = DocumentSearchDTO.builder()
                .query("test query")
                .limit(10)
                .build();

        Document doc1 = Document.builder()
                .id(1L)
                .documentName("Test Document 1")
                .documentType("TSG")
                .content("Test content 1")
                .build();

        Document doc2 = Document.builder()
                .id(2L)
                .documentName("Test Document 2")
                .documentType("MANUAL")
                .content("Test content 2")
                .build();

        List<Document> documents = Arrays.asList(doc1, doc2);
        when(documentService.searchDocuments(any(DocumentSearchDTO.class))).thenReturn(documents);

        // Act
        ResponseEntity<List<DocumentDTO>> response = documentController.searchDocuments(
                searchDTO, null, null);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(2, response.getBody().size());
        assertEquals("Test Document 1", response.getBody().get(0).getDocumentName());
        verify(documentService).searchDocuments(any(DocumentSearchDTO.class));
    }

    @Test
    void testGetDocument_Found() {
        // Arrange
        Long id = 1L;
        Document document = Document.builder()
                .id(id)
                .documentName("Test Document")
                .documentType("TSG")
                .content("Test content")
                .build();

        when(documentService.getDocument(id)).thenReturn(Optional.of(document));

        // Act
        ResponseEntity<DocumentDTO> response = documentController.getDocument(id, null, null);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(id, response.getBody().getId());
        assertEquals("Test Document", response.getBody().getDocumentName());
        verify(documentService).getDocument(id);
    }

    @Test
    void testGetDocument_NotFound() {
        // Arrange
        Long id = 999L;
        when(documentService.getDocument(id)).thenReturn(Optional.empty());

        // Act
        ResponseEntity<DocumentDTO> response = documentController.getDocument(id, null, null);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        verify(documentService).getDocument(id);
    }

    @Test
    void testGetDocumentsByType_ReturnsResults() {
        // Arrange
        String documentType = "TSG";
        Document doc1 = Document.builder()
                .id(1L)
                .documentName("TSG 1")
                .documentType(documentType)
                .build();

        when(documentService.getDocumentsByType(documentType))
                .thenReturn(Collections.singletonList(doc1));

        // Act
        ResponseEntity<List<DocumentDTO>> response = documentController.getDocumentsByType(
                documentType, null, null);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        assertEquals(documentType, response.getBody().get(0).getDocumentType());
        verify(documentService).getDocumentsByType(documentType);
    }

    @Test
    void testGetDocumentsByTag_ReturnsResults() {
        // Arrange
        String tag = "troubleshooting";
        Document doc1 = Document.builder()
                .id(1L)
                .documentName("Doc with tag")
                .tags("ssh,troubleshooting")
                .build();

        when(documentService.getDocumentsByTag(tag))
                .thenReturn(Collections.singletonList(doc1));

        // Act
        ResponseEntity<List<DocumentDTO>> response = documentController.getDocumentsByTag(
                tag, null, null);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        verify(documentService).getDocumentsByTag(tag);
    }

    @Test
    void testDeleteDocument_Success() {
        // Arrange
        Long id = 1L;
        when(documentService.deleteDocument(id)).thenReturn(true);

        // Act
        ResponseEntity<Map<String, Object>> response = documentController.deleteDocument(
                id, null, null);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue((Boolean) response.getBody().get("success"));
        verify(documentService).deleteDocument(id);
    }

    @Test
    void testDeleteDocument_NotFound() {
        // Arrange
        Long id = 999L;
        when(documentService.deleteDocument(id)).thenReturn(false);

        // Act
        ResponseEntity<Map<String, Object>> response = documentController.deleteDocument(
                id, null, null);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        verify(documentService).deleteDocument(id);
    }

    @Test
    void testGetStatistics_ReturnsStats() {
        // Arrange
        Map<String, Object> stats = new HashMap<>();
        stats.put("total_documents", 100L);
        stats.put("documents_with_embeddings", 75L);
        stats.put("embedding_coverage_percentage", 75.0);
        stats.put("embedding_service_available", true);

        when(documentService.getStatistics()).thenReturn(stats);

        // Act
        ResponseEntity<Map<String, Object>> response = documentController.getStatistics(null, null);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(100L, response.getBody().get("total_documents"));
        assertEquals(75L, response.getBody().get("documents_with_embeddings"));
        verify(documentService).getStatistics();
    }

    @Test
    void testAnalyzeDocument_ReturnsAnalysis() {
        // Arrange
        Map<String, String> request = Map.of("content", "Test content for analysis");
        Map<String, Object> analysis = new HashMap<>();
        analysis.put("word_count", 4);
        analysis.put("character_count", 26);
        analysis.put("suggested_tags", new String[]{"test", "content"});

        when(documentService.analyzeDocument(anyString())).thenReturn(analysis);

        // Act
        ResponseEntity<Map<String, Object>> response = documentController.analyzeDocument(
                request, null, null);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(4, response.getBody().get("word_count"));
        verify(documentService).analyzeDocument(anyString());
    }

    @Test
    void testAnalyzeDocument_EmptyContent() {
        // Arrange
        Map<String, String> request = Map.of("content", "");

        // Act
        ResponseEntity<Map<String, Object>> response = documentController.analyzeDocument(
                request, null, null);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(documentService, never()).analyzeDocument(anyString());
    }
}
