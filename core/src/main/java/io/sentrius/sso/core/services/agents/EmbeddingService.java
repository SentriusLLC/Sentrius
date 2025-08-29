package io.sentrius.sso.core.services.agents;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Embedding service that delegates to the integration proxy for proper security handling.
 * This service acts as a facade to the integration proxy's embedding capabilities.
 */
@Slf4j
@Service
public class EmbeddingService {

    private final RestTemplate restTemplate;
    private final String integrationProxyUrl;

    public EmbeddingService(
            RestTemplate restTemplate,
            @Value("${sentrius.integration-proxy.url:http://localhost:8081}") String integrationProxyUrl) {
        this.restTemplate = restTemplate;
        this.integrationProxyUrl = integrationProxyUrl;
    }

    /**
     * Check if embedding service is available
     */
    public boolean isAvailable() {
        try {
            String url = integrationProxyUrl + "/api/v1/embeddings/status";
            HttpHeaders headers = createAuthHeaders();
            if (headers == null) {
                return false;
            }
            
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> status = response.getBody();
                return Boolean.TRUE.equals(status.get("available"));
            }
            
            return false;
        } catch (Exception e) {
            log.warn("Failed to check embedding service availability: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Generate embedding for the given text via integration proxy
     */
    public float[] embed(String text) {
        if (text == null || text.trim().isEmpty()) {
            log.warn("Cannot generate embedding for empty text");
            return null;
        }

        try {
            String url = integrationProxyUrl + "/api/v1/embeddings/generate";
            HttpHeaders headers = createAuthHeaders();
            if (headers == null) {
                log.warn("No authentication context available for embedding generation");
                return null;
            }
            
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("text", text);
            
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                
                if (responseBody.containsKey("embedding")) {
                    Object embeddingObj = responseBody.get("embedding");
                    
                    if (embeddingObj instanceof float[]) {
                        return (float[]) embeddingObj;
                    } else if (embeddingObj instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<Number> embeddingList = (List<Number>) embeddingObj;
                        float[] result = new float[embeddingList.size()];
                        for (int i = 0; i < embeddingList.size(); i++) {
                            result[i] = embeddingList.get(i).floatValue();
                        }
                        
                        log.debug("Generated embedding with {} dimensions for text length: {}", 
                                result.length, text.length());
                        return result;
                    }
                }
            }
            
            log.warn("Failed to generate embedding - unexpected response format");
            return null;
            
        } catch (Exception e) {
            log.error("Error generating embedding for text: {}", text.substring(0, Math.min(100, text.length())), e);
            return null;
        }
    }

    /**
     * Generate embeddings for multiple texts in batch via integration proxy
     */
    public Map<String, float[]> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return new HashMap<>();
        }

        try {
            String url = integrationProxyUrl + "/api/v1/embeddings/generate/batch";
            HttpHeaders headers = createAuthHeaders();
            if (headers == null) {
                log.warn("No authentication context available for batch embedding generation");
                return new HashMap<>();
            }
            
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("texts", texts);
            
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                
                if (responseBody.containsKey("embeddings")) {
                    @SuppressWarnings("unchecked")
                    Map<String, float[]> embeddings = (Map<String, float[]>) responseBody.get("embeddings");
                    
                    log.debug("Generated batch embeddings for {} texts", embeddings.size());
                    return embeddings;
                }
            }
            
            log.warn("Failed to generate batch embeddings - unexpected response format");
            return new HashMap<>();
            
        } catch (Exception e) {
            log.error("Error generating batch embeddings for {} texts", texts.size(), e);
            return new HashMap<>();
        }
    }

    /**
     * Calculate cosine similarity between two embeddings
     */
    public static double calculateCosineSimilarity(float[] embedding1, float[] embedding2) {
        if (embedding1 == null || embedding2 == null || embedding1.length != embedding2.length) {
            return 0.0;
        }

        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < embedding1.length; i++) {
            dotProduct += embedding1[i] * embedding2[i];
            normA += Math.pow(embedding1[i], 2);
            normB += Math.pow(embedding2[i], 2);
        }

        if (normA == 0.0 || normB == 0.0) {
            return 0.0;
        }

        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    /**
     * Create authentication headers for integration proxy calls
     */
    private HttpHeaders createAuthHeaders() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return null;
        }
        
        // For now, assume we have a Bearer token available
        // In a real implementation, this would extract the JWT token from the security context
        String token = extractTokenFromAuthentication(authentication);
        if (token == null) {
            return null;
        }
        
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);
        headers.set("Content-Type", "application/json");
        
        return headers;
    }

    /**
     * Extract JWT token from authentication context
     * This is a placeholder implementation - in practice, you'd extract the actual JWT
     */
    private String extractTokenFromAuthentication(Authentication authentication) {
        // This is a simplified implementation
        // In practice, you'd extract the actual JWT token from the authentication object
        if (authentication.getCredentials() instanceof String) {
            return (String) authentication.getCredentials();
        }
        
        // For now, return null to indicate no token available
        // This would need to be implemented based on your specific authentication setup
        return null;
    }
}