package io.sentrius.agent.services;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.sentrius.sso.core.dto.capabilities.EndpointDescriptor;
import io.sentrius.sso.core.dto.ztat.TokenDTO;
import io.sentrius.sso.core.embeddings.EmbeddingService;
import io.sentrius.sso.core.exceptions.ZtatException;
import io.sentrius.sso.core.services.endpoints.CosineSimilarity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class EndpointSearcher {

    private final EmbeddingService embeddingService;
    private final EndpointRegistry endpointRegistry;

    public EndpointSearcher(EmbeddingService embeddingService,
                            EndpointRegistry endpointRegistry
    ) {

        this.embeddingService = embeddingService;
        this.endpointRegistry = endpointRegistry;
    }

    public List<EndpointDescriptor> getEndpointsLike(TokenDTO dto, String query)
        throws ZtatException, JsonProcessingException {
        float[] queryVector = embeddingService.embed(dto, query);

        List<EndpointDescriptor> endpoints = endpointRegistry.getAllEndpoints();
        return endpoints.stream()
            .map(ed -> {
                var embed = endpointRegistry.getEmbedding(ed);
                if (embed.isEmpty()) {
                    log.warn("No embedding found for endpoint: {}", ed.getName());
                    return Map.entry(ed, 0.0f);
                }

                    var arr = embed.get();
                log.info("Scoring {} | Query first5={} | Endpoint first5={}",
                    ed.getName(),
                    Arrays.toString(Arrays.copyOfRange(queryVector, 0, 5)),
                    Arrays.toString(Arrays.copyOfRange(arr, 0, 5)));
                var score = CosineSimilarity.score(queryVector,
                    embed.orElseThrow(() -> new RuntimeException("Embedding not found for " +
                        "endpoint: " + ed.getName())));
                    log.info("Calculating similarity for endpoint: {} and {} {} ", ed.getName(), embed.get().length,
                        score);
                return Map.entry(ed, score);
            }
            )
            .sorted((a, b) -> Float.compare(b.getValue(), a.getValue()))
            .filter(entry -> entry.getValue() > 0.75) // adjust threshold as needed
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
    }
}
