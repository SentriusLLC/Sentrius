package io.sentrius.agent.services;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    private List<String> tokenize(String text) {
        if (text == null) return List.of();
        return Arrays.stream(text.toLowerCase()
                .replaceAll("[^a-z0-9 ]", " ") // keep alphanumeric
                .split("\\s+"))
            .filter(s -> !s.isBlank())
            .toList();
    }

    private double jaccard(List<String> a, List<String> b) {
        if (a.isEmpty() || b.isEmpty()) return 0.0;

        Set<String> setA = new HashSet<>(a);
        Set<String> setB = new HashSet<>(b);

        Set<String> intersection = new HashSet<>(setA);
        intersection.retainAll(setB);

        Set<String> union = new HashSet<>(setA);
        union.addAll(setB);

        return (double) intersection.size() / union.size();
    }

    public List<EndpointDescriptor> getEndpointsLike(TokenDTO dto, String query) throws Exception, ZtatException {
        float[] queryVector = embeddingService.embed(dto, query);
        List<String> queryTokens = tokenize(query);

        return endpointRegistry.getAllEndpoints().stream()
            .map(ed -> {
                var embedOpt = endpointRegistry.getEmbedding(ed);
                if (embedOpt.isEmpty()) return Map.entry(ed, 0.0);

                float cosine = CosineSimilarity.score(queryVector, embedOpt.get());

                double lexical = jaccard(queryTokens, tokenize(ed.getDescription()));
                double hybridScore = (0.7 * cosine) + (0.3 * lexical);

                return Map.entry(ed, hybridScore);
            })
            .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
            .limit(10) // keep top 10
            .map(Map.Entry::getKey)
            .toList();
    }

}
