package io.sentrius.sentrius.analysis.agents.verbs;

import com.fasterxml.jackson.core.type.TypeReference;
import io.sentrius.agent.analysis.agents.verbs.DocumentVerbs;
import io.sentrius.sso.core.dto.agents.AgentExecutionContextDTO;
import io.sentrius.sso.core.dto.documents.DocumentDTO;
import io.sentrius.sso.core.dto.ztat.TokenDTO;
import io.sentrius.sso.core.exceptions.ZtatException;
import io.sentrius.sso.core.services.agents.ZeroTrustClientService;
import io.sentrius.sso.core.utils.JsonUtil;
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
 * Unit tests for DocumentVerbs.
 */
@ExtendWith(MockitoExtension.class)
class DocumentVerbsTest {

    @Mock
    private ZeroTrustClientService zeroTrustClientService;

    private DocumentVerbs documentVerbs;

    @BeforeEach
    void setUp() {
        documentVerbs = new DocumentVerbs(zeroTrustClientService);
    }

    @Test
    void testSearchDocuments_Success() throws Exception, ZtatException {
        // Arrange
        TokenDTO token = TokenDTO.builder().build();
        AgentExecutionContextDTO contextDTO = mock(AgentExecutionContextDTO.class);
        
        when(contextDTO.getExecutionArgumentScoped("query", String.class))
                .thenReturn(Optional.of("SSH troubleshooting"));
        when(contextDTO.getExecutionArgumentScoped("documentType", String.class))
                .thenReturn(Optional.empty());
        when(contextDTO.getExecutionArgumentScoped("tags", List.class))
                .thenReturn(Optional.empty());
        when(contextDTO.getExecutionArgumentScoped("limit", Integer.class))
                .thenReturn(Optional.of(20));

        DocumentDTO doc1 = DocumentDTO.builder()
                .id(1L)
                .documentName("SSH TSG")
                .documentType("TSG")
                .content("SSH troubleshooting guide content")
                .build();

        List<DocumentDTO> expectedDocs = Collections.singletonList(doc1);
        String jsonResponse = JsonUtil.MAPPER.writeValueAsString(expectedDocs);

        when(zeroTrustClientService.callPostOnApi(eq(token), eq("/api/v1/documents/search"), anyString()))
                .thenReturn(jsonResponse);

        // Act
        List<DocumentDTO> result = documentVerbs.searchDocuments(token, contextDTO);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("SSH TSG", result.get(0).getDocumentName());
        verify(zeroTrustClientService).callPostOnApi(eq(token), eq("/api/v1/documents/search"), anyString());
    }

    @Test
    void testSearchDocuments_NoQuery() {
        // Arrange
        TokenDTO token = TokenDTO.builder().build();
        AgentExecutionContextDTO contextDTO = mock(AgentExecutionContextDTO.class);
        
        when(contextDTO.getExecutionArgumentScoped("query", String.class))
                .thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> 
                documentVerbs.searchDocuments(token, contextDTO));
    }

    @Test
    void testSearchDocuments_NoResults() throws Exception, ZtatException {
        // Arrange
        TokenDTO token = TokenDTO.builder().build();
        AgentExecutionContextDTO contextDTO = mock(AgentExecutionContextDTO.class);
        
        when(contextDTO.getExecutionArgumentScoped("query", String.class))
                .thenReturn(Optional.of("nonexistent"));
        when(contextDTO.getExecutionArgumentScoped("documentType", String.class))
                .thenReturn(Optional.empty());
        when(contextDTO.getExecutionArgumentScoped("tags", List.class))
                .thenReturn(Optional.empty());
        when(contextDTO.getExecutionArgumentScoped("limit", Integer.class))
                .thenReturn(Optional.of(20));

        when(zeroTrustClientService.callPostOnApi(eq(token), eq("/api/v1/documents/search"), anyString()))
                .thenReturn(null);

        // Act
        List<DocumentDTO> result = documentVerbs.searchDocuments(token, contextDTO);

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testGetDocument_Success() throws Exception, ZtatException {
        // Arrange
        TokenDTO token = TokenDTO.builder().build();
        AgentExecutionContextDTO contextDTO = mock(AgentExecutionContextDTO.class);
        
        when(contextDTO.getExecutionArgumentScoped("documentId", Long.class))
                .thenReturn(Optional.of(1L));

        DocumentDTO expectedDoc = DocumentDTO.builder()
                .id(1L)
                .documentName("Test Document")
                .documentType("TSG")
                .build();

        String jsonResponse = JsonUtil.MAPPER.writeValueAsString(expectedDoc);

        when(zeroTrustClientService.callGetOnApi(eq(token), eq("/api/v1/documents/1")))
                .thenReturn(jsonResponse);

        // Act
        DocumentDTO result = documentVerbs.getDocument(token, contextDTO);

        // Assert
        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("Test Document", result.getDocumentName());
        verify(zeroTrustClientService).callGetOnApi(eq(token), eq("/api/v1/documents/1"));
    }

    @Test
    void testGetDocument_NoDocumentId() {
        // Arrange
        TokenDTO token = TokenDTO.builder().build();
        AgentExecutionContextDTO contextDTO = mock(AgentExecutionContextDTO.class);
        
        when(contextDTO.getExecutionArgumentScoped("documentId", Long.class))
                .thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> 
                documentVerbs.getDocument(token, contextDTO));
    }

    @Test
    void testGetDocumentsByType_Success() throws Exception, ZtatException {
        // Arrange
        TokenDTO token = TokenDTO.builder().build();
        AgentExecutionContextDTO contextDTO = mock(AgentExecutionContextDTO.class);
        
        when(contextDTO.getExecutionArgumentScoped("documentType", String.class))
                .thenReturn(Optional.of("TSG"));

        DocumentDTO doc1 = DocumentDTO.builder()
                .id(1L)
                .documentName("TSG 1")
                .documentType("TSG")
                .build();

        DocumentDTO doc2 = DocumentDTO.builder()
                .id(2L)
                .documentName("TSG 2")
                .documentType("TSG")
                .build();

        List<DocumentDTO> expectedDocs = Arrays.asList(doc1, doc2);
        String jsonResponse = JsonUtil.MAPPER.writeValueAsString(expectedDocs);

        when(zeroTrustClientService.callGetOnApi(eq(token), eq("/api/v1/documents/type/TSG")))
                .thenReturn(jsonResponse);

        // Act
        List<DocumentDTO> result = documentVerbs.getDocumentsByType(token, contextDTO);

        // Assert
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("TSG", result.get(0).getDocumentType());
        assertEquals("TSG", result.get(1).getDocumentType());
        verify(zeroTrustClientService).callGetOnApi(eq(token), eq("/api/v1/documents/type/TSG"));
    }

    @Test
    void testGetDocumentsByTag_Success() throws Exception, ZtatException {
        // Arrange
        TokenDTO token = TokenDTO.builder().build();
        AgentExecutionContextDTO contextDTO = mock(AgentExecutionContextDTO.class);
        
        when(contextDTO.getExecutionArgumentScoped("tag", String.class))
                .thenReturn(Optional.of("troubleshooting"));

        DocumentDTO doc1 = DocumentDTO.builder()
                .id(1L)
                .documentName("Troubleshooting Guide")
                .tags(new String[]{"troubleshooting", "ssh"})
                .build();

        List<DocumentDTO> expectedDocs = Collections.singletonList(doc1);
        String jsonResponse = JsonUtil.MAPPER.writeValueAsString(expectedDocs);

        when(zeroTrustClientService.callGetOnApi(eq(token), eq("/api/v1/documents/tag/troubleshooting")))
                .thenReturn(jsonResponse);

        // Act
        List<DocumentDTO> result = documentVerbs.getDocumentsByTag(token, contextDTO);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertTrue(Arrays.asList(result.get(0).getTags()).contains("troubleshooting"));
        verify(zeroTrustClientService).callGetOnApi(eq(token), eq("/api/v1/documents/tag/troubleshooting"));
    }

    @Test
    void testAnalyzeDocument_Success() throws Exception, ZtatException {
        // Arrange
        TokenDTO token = TokenDTO.builder().build();
        AgentExecutionContextDTO contextDTO = mock(AgentExecutionContextDTO.class);
        
        when(contextDTO.getExecutionArgumentScoped("content", String.class))
                .thenReturn(Optional.of("Test document content for analysis"));

        Map<String, Object> expectedAnalysis = new HashMap<>();
        expectedAnalysis.put("word_count", 5);
        expectedAnalysis.put("character_count", 37);
        expectedAnalysis.put("suggested_tags", new String[]{"test", "document"});

        String jsonResponse = JsonUtil.MAPPER.writeValueAsString(expectedAnalysis);

        when(zeroTrustClientService.callPostOnApi(eq(token), eq("/api/v1/documents/analyze"), anyString()))
                .thenReturn(jsonResponse);

        // Act
        Map<String, Object> result = documentVerbs.analyzeDocument(token, contextDTO);

        // Assert
        assertNotNull(result);
        assertEquals(5, result.get("word_count"));
        assertEquals(37, result.get("character_count"));
        verify(zeroTrustClientService).callPostOnApi(eq(token), eq("/api/v1/documents/analyze"), anyString());
    }

    @Test
    void testAnalyzeDocument_NoContent() {
        // Arrange
        TokenDTO token = TokenDTO.builder().build();
        AgentExecutionContextDTO contextDTO = mock(AgentExecutionContextDTO.class);
        
        when(contextDTO.getExecutionArgumentScoped("content", String.class))
                .thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> 
                documentVerbs.analyzeDocument(token, contextDTO));
    }
}
