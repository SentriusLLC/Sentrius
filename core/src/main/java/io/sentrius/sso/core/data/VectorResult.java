package io.sentrius.sso.core.data;

import com.fasterxml.jackson.databind.JsonNode;

public record VectorResult(String id, float score, JsonNode payload) {}