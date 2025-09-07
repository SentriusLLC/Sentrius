package io.sentrius.sso.core.services.agents;

import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.sentrius.sso.core.dto.ztat.TokenDTO;
import io.sentrius.sso.core.embeddings.EmbeddingService;
import io.sentrius.sso.core.exceptions.ZtatException;
import io.sentrius.sso.core.utils.JsonUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class LLMService implements EmbeddingService {

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
