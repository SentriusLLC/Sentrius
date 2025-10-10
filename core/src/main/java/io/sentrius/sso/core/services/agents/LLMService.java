package io.sentrius.sso.core.services.agents;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.sentrius.sso.core.dto.ztat.TokenDTO;
import io.sentrius.sso.core.embeddings.EmbeddingServiceIfc;
import io.sentrius.sso.core.exceptions.ZtatException;
import io.sentrius.sso.core.utils.JsonUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class LLMService implements EmbeddingServiceIfc {

    final ZeroTrustClientService zeroTrustClientService;

    @Value("${agent.open.ai.endpoint:http://localhost:8080}")
    private String openAiEndpoint;

    public LLMService(ZeroTrustClientService zeroTrustClientService) {
        this.zeroTrustClientService = zeroTrustClientService;
    }

    /**
     * Request a Zero Trust Access Token (ZTAT) using Keycloak JWT and `ZtatRequestDTO`
     */
    public <T> String askQuestion(TokenDTO dto, T body) throws ZtatException {
        return zeroTrustClientService.callPostOnApi(dto, openAiEndpoint, "/chat/completions", body);
    }
    
    /**
     * Analyze an image using the Vision API
     * @param dto Token for authentication
     * @param imageBase64 Base64 encoded image data (with data URI prefix like "data:image/png;base64,...")
     * @param prompt Text prompt describing what to analyze
     * @return AI analysis result
     */
    public String analyzeImage(TokenDTO dto, String imageBase64, String prompt) throws ZtatException, JsonProcessingException {
        Map<String, Object> imageUrl = Map.of(
            "url", imageBase64,
            "detail", "auto"
        );
        
        List<Map<String, Object>> content = new ArrayList<>();
        content.add(Map.of("type", "text", "text", prompt));
        content.add(Map.of("type", "image_url", "image_url", imageUrl));
        
        Map<String, Object> message = Map.of(
            "role", "user",
            "content", content
        );
        
        Map<String, Object> payload = Map.of(
            "model", "gpt-4o-mini",
            "messages", List.of(message),
            "max_tokens", 500
        );
        
        return zeroTrustClientService.callPostOnApi(dto, openAiEndpoint, "/chat/completions", payload);
    }
    
    /**
     * Analyze multiple images using the Vision API
     * @param dto Token for authentication
     * @param imagesBase64 List of base64 encoded images
     * @param prompt Text prompt describing what to analyze
     * @return AI analysis result
     */
    public String analyzeImages(TokenDTO dto, List<String> imagesBase64, String prompt) throws ZtatException, JsonProcessingException {
        List<Map<String, Object>> content = new ArrayList<>();
        content.add(Map.of("type", "text", "text", prompt));
        
        for (String imageBase64 : imagesBase64) {
            Map<String, Object> imageUrl = Map.of(
                "url", imageBase64,
                "detail", "auto"
            );
            content.add(Map.of("type", "image_url", "image_url", imageUrl));
        }
        
        Map<String, Object> message = Map.of(
            "role", "user",
            "content", content
        );
        
        // Adjust max_tokens based on number of images (more images = more tokens needed)
        int maxTokens = 300 + (imagesBase64.size() * 100);
        
        Map<String, Object> payload = Map.of(
            "model", "gpt-4o-mini",
            "messages", List.of(message),
            "max_tokens", maxTokens
        );
        
        return zeroTrustClientService.callPostOnApi(dto, openAiEndpoint, "/chat/completions", payload);
    }

    @Override
    public float[] embed(TokenDTO dto, String input) throws ZtatException, JsonProcessingException {

        var payload = Map.of("input", input, "model", "text-embedding-3-small");

        var textResponse = zeroTrustClientService.callPostOnApi(dto, openAiEndpoint, "/embeddings/generate", payload);

        var response = JsonUtil.MAPPER.readTree(textResponse);

        var dataArray = response.get("data");
        List<float[]> embeddings = new java.util.ArrayList<>();
        for (var item : dataArray) {
            var vector = item.get("embedding");
            float[] embedding = new float[vector.size()];

            for (int i = 0; i < vector.size(); i++) {
                embedding[i] = (float) vector.get(i).asDouble();
            }
            embeddings.add(embedding);
        }
        return embeddings.get(0);
    }

    @Override
    public List<float[]> embed(TokenDTO dto, List<String> texts) throws ZtatException, JsonProcessingException {
        var payload = Map.of("input", texts, "model", "text-embedding-3-small");

        var textResponse = zeroTrustClientService.callPostOnApi(dto, openAiEndpoint, "/embeddings/generate", payload);

        var response = JsonUtil.MAPPER.readTree(textResponse);

        var dataArray = response.get("data");
        List<float[]> embeddings = new java.util.ArrayList<>();
        for (var item : dataArray) {
            var vector = item.get("embedding");
            float[] embedding = new float[vector.size()];

            for (int i = 0; i < vector.size(); i++) {
                embedding[i] = (float) vector.get(i).asDouble();
            }
            embeddings.add(embedding);
        }
        return embeddings;
    }
}
