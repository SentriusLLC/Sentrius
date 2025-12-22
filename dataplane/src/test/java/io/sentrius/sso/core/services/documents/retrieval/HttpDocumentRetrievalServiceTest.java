package io.sentrius.sso.core.services.documents.retrieval;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for HttpDocumentRetrievalService
 */
@ExtendWith(MockitoExtension.class)
class HttpDocumentRetrievalServiceTest {

    private HttpDocumentRetrievalService retrievalService;

    @BeforeEach
    void setUp() {
        retrievalService = new HttpDocumentRetrievalService();
    }

    @Test
    void testSupports_Http() {
        assertTrue(retrievalService.supports("http"));
        assertTrue(retrievalService.supports("HTTP"));
        assertTrue(retrievalService.supports("https"));
        assertTrue(retrievalService.supports("HTTPS"));
        assertFalse(retrievalService.supports("s3"));
        assertFalse(retrievalService.supports("ftp"));
    }

    @Test
    void testGetSourceType() {
        assertEquals("http", retrievalService.getSourceType());
    }

    @Test
    void testRetrieveDocument_Success() {
        // This is an integration-style test that would require mocking RestTemplate
        // For now, we just verify the service is constructed correctly
        assertNotNull(retrievalService);
        assertTrue(retrievalService.supports("http"));
    }
}
