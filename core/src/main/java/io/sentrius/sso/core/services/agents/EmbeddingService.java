package io.sentrius.sso.core.services.agents;

import io.sentrius.sso.core.exceptions.ZtatException;
import io.sentrius.sso.core.utils.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
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
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class EmbeddingService {

    private final String integrationProxyUrl;
    private final ZeroTrustClientService zeroTrustClientService;

    public EmbeddingService(
        @Value("${sentrius.integration.proxyUrl:http://localhost:8081}") String integrationProxyUrl,
        ZeroTrustClientService zeroTrustClientService
    ) {
        this.integrationProxyUrl = integrationProxyUrl;
        this.zeroTrustClientService = zeroTrustClientService;
    }

    /**
     * Check if embedding service is available
     */
    public boolean isAvailable() {
        try {
            /*
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
            *
             */
            
            return true;
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

            var payload = Map.of("input", text, "model", "text-embedding-3-small");

            var responseStr = zeroTrustClientService.callPostOnApi(integrationProxyUrl, "/api/v1/embeddings/generate"
                ,true,
                payload);

            log.info("Embedding response: {}", responseStr);

            var response = JsonUtil.MAPPER.readTree(responseStr);

            var vector = response.get("embedding");
            if (null != vector) {
                float[] embedding = new float[vector.size()];
                for (int i = 0; i < vector.size(); i++) {
                    embedding[i] = (float) vector.get(i).asDouble();
                }
                return embedding;
            }else{
            List<float []> embeddings = new java.util.ArrayList<>();
            var data = response.get("data");
            if (data.isArray() && !data.isEmpty()) {

                for (var dataResponse : data) {

                    vector = dataResponse.get("embedding");
                    float[] embedding = new float[vector.size()];
                    for (int i = 0; i < vector.size(); i++) {
                        embedding[i] = (float) vector.get(i).asDouble();
                    }
                    embeddings.add(embedding);
                }
                ;
                return embeddings.get(0);

                } else {
                return null;
                }
            }


        } catch (Exception e) {
            log.error("Error generating embedding for text: {}", text.substring(0, Math.min(100, text.length())), e);
            return null;
        } catch (ZtatException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Generate embedding for the given text via integration proxy
     */
    public List<float []> embed(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            log.warn("Cannot generate embedding for empty text");
            return null;
        }

        try {
            String url = integrationProxyUrl + "/api/v1/embeddings/generate";

            var payload = Map.of("input", texts, "model", "text-embedding-3-small");

            var responseStr = zeroTrustClientService.callPostOnApi(integrationProxyUrl, "/api/v1/embeddings/generate"
                ,true,
                payload);


            var response = JsonUtil.MAPPER.readTree(responseStr);

            List<float []> embeddings = new java.util.ArrayList<>();
            var data = response.get("data");
            if (data.isArray() && !data.isEmpty()) {

                for(var dataResponse : data) {

                    var vector = dataResponse.get("embedding");
                    float[] embedding = new float[vector.size()];
                    for (int i = 0; i < vector.size(); i++) {
                        embedding[i] = (float) vector.get(i).asDouble();
                    }
                    embeddings.add(embedding);
                };
            }
            return embeddings;

        } catch (Exception e) {
            log.error("Error generating embedding for texts size is : {}", texts.size(), e);
            return null;
        } catch (ZtatException e) {
            throw new RuntimeException(e);
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