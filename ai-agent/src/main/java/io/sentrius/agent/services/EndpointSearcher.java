package io.sentrius.agent.services;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.sentrius.sso.core.dto.capabilities.EndpointDescriptor;
import io.sentrius.sso.core.dto.ztat.TokenDTO;
import io.sentrius.sso.core.embeddings.EmbeddingService;
import io.sentrius.sso.core.exceptions.ZtatException;
import io.sentrius.sso.core.services.endpoints.CosineSimilarity;
import org.springframework.stereotype.Service;

@Service
public class EndpointSearcher {

    private final List<EndpointDescriptor> endpoints;
    private final EmbeddingService embeddingService;
    private final EndpointRegistry endpointRegistry;

    public EndpointSearcher(List<EndpointDescriptor> endpoints, EmbeddingService embeddingService,
                            EndpointRegistry endpointRegistry
    ) {
        this.endpoints = endpoints;
        this.embeddingService = embeddingService;
        this.endpointRegistry = endpointRegistry;
    }

    public List<EndpointDescriptor> getEndpointsLike(TokenDTO dto, String query)
        throws ZtatException, JsonProcessingException {
        float[] queryVector = embeddingService.embed(dto, query);

        return endpoints.stream()
            .map(ed -> Map.entry(ed, CosineSimilarity.score(queryVector,
                    endpointRegistry.getEmbedding(ed).orElseThrow(() -> new RuntimeException("Embedding not found for " +
                        "endpoint: " + ed.getName())))))
            .sorted((a, b) -> Float.compare(b.getValue(), a.getValue()))
            .filter(entry -> entry.getValue() > 0.75) // adjust threshold as needed
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
    }
}
