package io.sentrius.sso.core.services.documents.retrieval;

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
 * Unit tests for DocumentRetrievalManager
 */
@ExtendWith(MockitoExtension.class)
class DocumentRetrievalManagerTest {

    @Mock
    private DocumentRetrievalService httpService;

    @Mock
    private DocumentRetrievalService s3Service;

    private DocumentRetrievalManager manager;

    @BeforeEach
    void setUp() {
        // Use lenient stubbing to avoid UnnecessaryStubbing errors when not all stubs are used in every test
        lenient().when(httpService.supports("http")).thenReturn(true);
        lenient().when(httpService.supports("https")).thenReturn(true);
        lenient().when(httpService.supports(argThat(arg -> !arg.equals("http") && !arg.equals("https")))).thenReturn(false);
        lenient().when(httpService.getSourceType()).thenReturn("http");

        lenient().when(s3Service.supports("s3")).thenReturn(true);
        lenient().when(s3Service.supports(argThat(arg -> !arg.equals("s3")))).thenReturn(false);
        lenient().when(s3Service.getSourceType()).thenReturn("s3");

        List<DocumentRetrievalService> services = Arrays.asList(httpService, s3Service);
        manager = new DocumentRetrievalManager(services);
    }

    @Test
    void testIsSourceTypeSupported() {
        assertTrue(manager.isSourceTypeSupported("http"));
        assertTrue(manager.isSourceTypeSupported("https"));
        assertTrue(manager.isSourceTypeSupported("s3"));
        assertFalse(manager.isSourceTypeSupported("ftp"));
    }

    @Test
    void testGetSupportedSourceTypes() {
        List<String> types = manager.getSupportedSourceTypes();
        assertEquals(2, types.size());
        assertTrue(types.contains("http"));
        assertTrue(types.contains("s3"));
    }

    @Test
    void testRetrieveDocument_HttpUrl() throws Exception {
        String url = "https://example.com/document.txt";
        String content = "Test content";
        Map<String, String> options = new HashMap<>();

        when(httpService.retrieveDocument(eq(url), any())).thenReturn(content);

        String result = manager.retrieveDocument(url, options);

        assertEquals(content, result);
        verify(httpService).retrieveDocument(url, options);
        verify(s3Service, never()).retrieveDocument(anyString(), any());
    }

    @Test
    void testRetrieveDocument_S3Url() throws Exception {
        String url = "s3://bucket/document.txt";
        String content = "S3 content";
        Map<String, String> options = new HashMap<>();

        when(s3Service.retrieveDocument(eq(url), any())).thenReturn(content);

        String result = manager.retrieveDocument(url, options);

        assertEquals(content, result);
        verify(s3Service).retrieveDocument(url, options);
        verify(httpService, never()).retrieveDocument(anyString(), any());
    }

    @Test
    void testRetrieveDocument_UnsupportedType() {
        String url = "ftp://example.com/file.txt";
        Map<String, String> options = new HashMap<>();

        assertThrows(DocumentRetrievalException.class, () -> 
                manager.retrieveDocument(url, options));
    }

    @Test
    void testRetrieveDocumentWithMetadata() throws Exception {
        String url = "https://example.com/doc.txt";
        Map<String, String> options = new HashMap<>();
        
        DocumentRetrievalResult expectedResult = DocumentRetrievalResult.builder()
                .content("content")
                .contentType("text/plain")
                .sourceUrl(url)
                .build();

        when(httpService.retrieveDocumentWithMetadata(eq(url), any())).thenReturn(expectedResult);

        DocumentRetrievalResult result = manager.retrieveDocumentWithMetadata(url, options);

        assertNotNull(result);
        assertEquals("content", result.getContent());
        assertEquals("text/plain", result.getContentType());
        verify(httpService).retrieveDocumentWithMetadata(url, options);
    }
}
