package io.sentrius.sso.core.services.agents;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.sentrius.sso.core.dto.ztat.TokenDTO;
import io.sentrius.sso.core.embeddings.EmbeddingServiceIfc;
import io.sentrius.sso.core.exceptions.ZtatException;
import io.sentrius.sso.core.utils.JsonUtil;
import io.sentrius.sso.genai.Message;
import io.sentrius.sso.genai.model.LLMRequest;
import io.sentrius.sso.genai.model.VisionContent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class LLMService implements EmbeddingServiceIfc {

    final ZeroTrustClientService zeroTrustClientService;

    @Value("${agent.open.ai.endpoint:http://localhost:8080}")
    private String openAiEndpoint;

    // Constants for vision API requests
    private static final String VISION_CONTENT_TYPE_TEXT = "text";
    private static final String VISION_CONTENT_TYPE_IMAGE_URL = "image_url";
    private static final String VISION_DETAIL_AUTO = "auto";
    private static final String VISION_MODEL = "gpt-4.1";
    private static final int VISION_MAX_TOKENS = 500;

    public LLMService(ZeroTrustClientService zeroTrustClientService) {
        this.zeroTrustClientService = zeroTrustClientService;
        log.info("LLMService initialized with OpenAI endpoint: {}", openAiEndpoint);
    }

    public void setLLMEndpoint(String endpoint) {
        this.openAiEndpoint = endpoint;
        log.info("LLMService OpenAI endpoint set to: {}", openAiEndpoint);
    }


    /**
     * Request a Zero Trust Access Token (ZTAT) using Keycloak JWT and `ZtatRequestDTO`
     */
    public <T> String askLLM(TokenDTO dto, T body) throws ZtatException {
        return zeroTrustClientService.callPostOnApi(dto, openAiEndpoint, "/chat/completions", body);
    }

    /**
     * Request a Zero Trust Access Token (ZTAT) using Keycloak JWT and `ZtatRequestDTO`
     */
    public <T> String askQuestion(TokenDTO dto, T body) throws ZtatException {
        return zeroTrustClientService.callPostOnApi(dto, openAiEndpoint, "/chat/completions", body);
    }

    public <T> String askQuestion(TokenDTO dto, String serverUrl, T body) throws ZtatException {
        return zeroTrustClientService.callPostOnApi(dto, serverUrl, "/chat/completions", body);
    }
    
    /**
     * Analyze an image using the Vision API via LLM proxy
     * @param dto Token for authentication
     * @param imageBase64 Base64 encoded image data (with data URI prefix like "data:image/png;base64,...")
     * @param prompt Text prompt describing what to analyze
     * @return AI analysis result
     */
    public String analyzeImage(TokenDTO dto, String imageBase64, String prompt) throws ZtatException, JsonProcessingException {
        // Build multimodal content with text and image
        List<VisionContent> content = new ArrayList<>();
        
        // Add text prompt
        content.add(VisionContent.builder()
            .type(VISION_CONTENT_TYPE_TEXT)
            .text(prompt)
            .build());
        
        // Add image
        content.add(VisionContent.builder()
            .type(VISION_CONTENT_TYPE_IMAGE_URL)
            .imageUrl(VisionContent.ImageUrl.builder()
                .url(imageBase64)
                .detail(VISION_DETAIL_AUTO)
                .build())
            .build());
        
        // Create message with multimodal content
        Message message = Message.builder()
            .role("user")
            .content(content)
            .build();
        
        // Build LLM request
        LLMRequest request = LLMRequest.builder()
            .model(VISION_MODEL)
            .messages(List.of(message))
            .maxTokens(VISION_MAX_TOKENS)
            .build();
        
        // Use the standard askQuestion method which goes through LLM proxy
        return askQuestion(dto, request);
    }
    
    /**
     * Analyze multiple images using the Vision API via LLM proxy
     * @param dto Token for authentication
     * @param imagesBase64 List of base64 encoded images (with data URI prefix)
     * @param prompt Text prompt describing what to analyze
     * @return AI analysis result
     */
    public String analyzeImages(
        TokenDTO dto,
        List<String> imagesBase64,
        String prompt
    ) throws ZtatException, JsonProcessingException {

        // Build multimodal content with text and multiple images
        List<VisionContent> content = new ArrayList<>();

        // Add text prompt first
        content.add(VisionContent.builder()
            .type(VISION_CONTENT_TYPE_TEXT)
            .text(prompt)
            .build());

        // Add all images
        for (String imageBase64 : imagesBase64) {
            content.add(VisionContent.builder()
                .type(VISION_CONTENT_TYPE_IMAGE_URL)
                .imageUrl(VisionContent.ImageUrl.builder()
                    .url(imageBase64)
                    .detail(VISION_DETAIL_AUTO)
                    .build())
                .build());
        }

        // Create message with multimodal content
        Message message = Message.builder()
            .role("user")
            .content(content)
            .build();

        // Build LLM request
        LLMRequest request = LLMRequest.builder()
            .model(VISION_MODEL)
            .messages(List.of(message))
            .maxTokens(VISION_MAX_TOKENS)
            .build();

        // Use the standard askQuestion method which goes through LLM proxy
        return askLLM(dto, request);
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
