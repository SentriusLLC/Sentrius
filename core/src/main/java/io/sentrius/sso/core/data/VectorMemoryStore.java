package io.sentrius.sso.core.data;

import java.util.List;
import java.util.Map;

public interface VectorMemoryStore {
    void upsert(String collection, String id, float[] vector, Map<String, Object> payload);
    List<VectorResult> search(String collection, float[] queryVector, int topK, Map<String, Object> filter);
}