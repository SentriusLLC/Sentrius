package io.sentrius.sso.core.services.agents;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmbeddingServiceTest {

    @Mock
    private RestTemplate restTemplate;
    
    @Mock
    private Authentication authentication;
    
    @Mock
    private SecurityContext securityContext;

    private EmbeddingService embeddingService;
    private static final String INTEGRATION_PROXY_URL = "http://localhost:8081";

    @BeforeEach
    void setUp() {
        embeddingService = new EmbeddingService(restTemplate, INTEGRATION_PROXY_URL);
    }
    
    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void setupAuthenticationContext() {
        lenient().when(securityContext.getAuthentication()).thenReturn(authentication);
        lenient().when(authentication.getCredentials()).thenReturn("test-jwt-token");
        SecurityContextHolder.setContext(securityContext);
    }

    @Test
    void testIsAvailable_Success() {
        // Arrange
        setupAuthenticationContext();
        Map<String, Object> statusResponse = new HashMap<>();
        statusResponse.put("available", true);
        ResponseEntity<Map> mockResponse = new ResponseEntity<>(statusResponse, HttpStatus.OK);
        
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(mockResponse);

        // Act
        boolean result = embeddingService.isAvailable();

        // Assert
        assertTrue(result);
        verify(restTemplate).exchange(contains("/api/v1/embeddings/status"), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class));
    }

    @Test
    void testIsAvailable_NotAvailable() {
        // Arrange
        setupAuthenticationContext();
        Map<String, Object> statusResponse = new HashMap<>();
        statusResponse.put("available", false);
        ResponseEntity<Map> mockResponse = new ResponseEntity<>(statusResponse, HttpStatus.OK);
        
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(mockResponse);

        // Act
        boolean result = embeddingService.isAvailable();

        // Assert
        assertFalse(result);
    }

    @Test
    void testIsAvailable_NoAuthentication() {
        // Arrange - no authentication setup

        // Act
        boolean result = embeddingService.isAvailable();

        // Assert
        assertFalse(result);
        verify(restTemplate, never()).exchange(anyString(), any(), any(), any(Class.class));
    }

    @Test
    void testEmbed_Success() {
        // Arrange
        setupAuthenticationContext();
        String inputText = "test text for embedding";
        float[] mockEmbedding = {0.1f, 0.2f, 0.3f, 0.4f, 0.5f};
        
        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("embedding", mockEmbedding);
        
        ResponseEntity<Map> mockResponse = new ResponseEntity<>(responseBody, HttpStatus.OK);
        
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(mockResponse);

        // Act
        float[] result = embeddingService.embed(inputText);

        // Assert
        assertNotNull(result);
        assertEquals(5, result.length);
        assertEquals(0.1f, result[0], 0.001f);
        assertEquals(0.2f, result[1], 0.001f);
        assertEquals(0.3f, result[2], 0.001f);
        assertEquals(0.4f, result[3], 0.001f);
        assertEquals(0.5f, result[4], 0.001f);
        
        verify(restTemplate).exchange(contains("/api/v1/embeddings/generate"), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class));
    }

    @Test
    void testEmbed_NoAuthentication() {
        // Arrange - no authentication setup
        
        // Act
        float[] result = embeddingService.embed("test text");
        
        // Assert
        assertNull(result);
        verify(restTemplate, never()).exchange(anyString(), any(), any(), any(Class.class));
    }

    @Test
    void testEmbed_EmptyText() {
        // Arrange
        setupAuthenticationContext();
        
        // Act
        float[] result1 = embeddingService.embed("");
        float[] result2 = embeddingService.embed(null);
        
        // Assert
        assertNull(result1);
        assertNull(result2);
        verify(restTemplate, never()).exchange(anyString(), any(), any(), any(Class.class));
    }

    @Test
    void testEmbed_ApiError() {
        // Arrange
        setupAuthenticationContext();
        String inputText = "test text";
        
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(new RuntimeException("API Error"));

        // Act
        float[] result = embeddingService.embed(inputText);

        // Assert
        assertNull(result);
        verify(restTemplate).exchange(contains("/api/v1/embeddings/generate"), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class));
    }

    @Test
    void testCalculateCosineSimilarity_ValidEmbeddings() {
        // Arrange
        float[] embedding1 = {1.0f, 0.0f, 0.0f};
        float[] embedding2 = {0.0f, 1.0f, 0.0f};
        float[] embedding3 = {1.0f, 0.0f, 0.0f}; // Same as embedding1

        // Act & Assert
        double similarity1 = EmbeddingService.calculateCosineSimilarity(embedding1, embedding2);
        assertEquals(0.0, similarity1, 0.001); // Orthogonal vectors

        double similarity2 = EmbeddingService.calculateCosineSimilarity(embedding1, embedding3);
        assertEquals(1.0, similarity2, 0.001); // Identical vectors
    }

    @Test
    void testCalculateCosineSimilarity_NullEmbeddings() {
        // Arrange
        float[] embedding1 = {1.0f, 0.0f, 0.0f};

        // Act & Assert
        double similarity1 = EmbeddingService.calculateCosineSimilarity(null, embedding1);
        assertEquals(0.0, similarity1);

        double similarity2 = EmbeddingService.calculateCosineSimilarity(embedding1, null);
        assertEquals(0.0, similarity2);

        double similarity3 = EmbeddingService.calculateCosineSimilarity(null, null);
        assertEquals(0.0, similarity3);
    }

    @Test
    void testCalculateCosineSimilarity_DifferentLengths() {
        // Arrange
        float[] embedding1 = {1.0f, 0.0f, 0.0f};
        float[] embedding2 = {1.0f, 0.0f}; // Different length

        // Act
        double similarity = EmbeddingService.calculateCosineSimilarity(embedding1, embedding2);

        // Assert
        assertEquals(0.0, similarity);
    }

    @Test
    void testEmbedBatch_Success() {
        // Arrange
        setupAuthenticationContext();
        List<String> texts = Arrays.asList("text1", "text2");
        Map<String, float[]> mockEmbeddings = new HashMap<>();
        mockEmbeddings.put("text1", new float[]{0.1f, 0.2f, 0.3f});
        mockEmbeddings.put("text2", new float[]{0.4f, 0.5f, 0.6f});
        
        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("embeddings", mockEmbeddings);
        
        ResponseEntity<Map> mockResponse = new ResponseEntity<>(responseBody, HttpStatus.OK);
        
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(mockResponse);

        // Act
        Map<String, float[]> results = embeddingService.embedBatch(texts);

        // Assert
        assertNotNull(results);
        assertEquals(2, results.size());
        assertTrue(results.containsKey("text1"));
        assertTrue(results.containsKey("text2"));
        
        verify(restTemplate).exchange(contains("/api/v1/embeddings/generate/batch"), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class));
    }

    @Test
    void testEmbedBatch_EmptyList() {
        // Act
        Map<String, float[]> result1 = embeddingService.embedBatch(Arrays.asList());
        Map<String, float[]> result2 = embeddingService.embedBatch(null);

        // Assert
        assertNotNull(result1);
        assertTrue(result1.isEmpty());
        assertNotNull(result2);
        assertTrue(result2.isEmpty());
        
        verify(restTemplate, never()).exchange(anyString(), any(), any(), any(Class.class));
    }
}