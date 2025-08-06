package io.sentrius.agent.services;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.sentrius.sso.core.dto.capabilities.EndpointDescriptor;
import io.sentrius.sso.core.dto.ztat.TokenDTO;
import io.sentrius.sso.core.embeddings.EmbeddingService;
import io.sentrius.sso.core.exceptions.ZtatException;
import io.sentrius.sso.core.services.agents.AgentClientService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class EndpointRegistry {
    Map<String, float[]> embeddingMap = new HashMap<>();
    Map<String, EndpointDescriptor> descriptorMap = new HashMap<>();

    private final AgentClientService agentClientService;
    private final EmbeddingService embeddingService;

    public void loadEndpoints(TokenDTO dto) throws ZtatException, JsonProcessingException {
        List<EndpointDescriptor> endpoints = agentClientService.getAvailableEndpoints(dto); // however you get them

        for (EndpointDescriptor ed : endpoints) {
            String key = buildKey(ed);
            String json = EndpointDescriptor.toEmbeddableJson(ed);
            float[] embedding = embeddingService.embed(dto, json);
            embeddingMap.put(key, embedding);
            descriptorMap.put(key, ed);
        }
    }

    public List<EndpointDescriptor> getAll() {
        return new ArrayList<>(descriptorMap.values());
    }

    public Optional<EndpointDescriptor> getDescriptor(String key) {
        return Optional.ofNullable(descriptorMap.get(key));
    }

    public Optional<float[]> getEmbedding(String key) {
        return Optional.ofNullable(embeddingMap.get(key));
    }

    public Optional<float[]> getEmbedding(EndpointDescriptor ed) {
        return Optional.ofNullable(embeddingMap.get(buildKey(ed)));
    }

    private String buildKey(EndpointDescriptor ed) {
        return ed.getHttpMethod() + "@" + ed.getPath();
    }
}
