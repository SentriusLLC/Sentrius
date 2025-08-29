package io.sentrius.sso.services;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.JsonNode;
import io.sentrius.sso.core.data.VectorMemoryStore;
import io.sentrius.sso.core.data.VectorResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

public class QdrantMemoryStore implements VectorMemoryStore {

    private final RestTemplate restTemplate = new RestTemplate();
    private final String baseUrl;

    public QdrantMemoryStore(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    @Override
    public void upsert(String collection, String id, float[] vector, Map<String, Object> payload) {
        var request = Map.of("points", List.of(Map.of(
            "id", id,
            "vector", vector,
            "payload", payload
        )));
        restTemplate.postForEntity(baseUrl + "/collections/" + collection + "/points", request, Void.class);
    }

    @Override
    public List<VectorResult> search(String collection, float[] queryVector, int topK, Map<String, Object> filter) {
        Map<String, Object> body = new HashMap<>();
        body.put("vector", queryVector);
        body.put("top", topK);
        if (filter != null && !filter.isEmpty()) {
            body.put("filter", Map.of("must", filter.entrySet().stream()
                .map(e -> Map.of("key", e.getKey(), "match", Map.of("value", e.getValue())))
                .toList()));
        }

        ResponseEntity<JsonNode> resp = restTemplate.postForEntity(
            baseUrl + "/collections/" + collection + "/points/search",
            body,
            JsonNode.class
        );

        JsonNode results = resp.getBody();
        List<VectorResult> vectorResults = new ArrayList<>();
        for (JsonNode r : results.get("result")) {
            vectorResults.add(new VectorResult(
                r.get("id").asText(),
                (float) r.get("score").asDouble(),
                r.get("payload")
            ));
        }
        return vectorResults;
    }
}
