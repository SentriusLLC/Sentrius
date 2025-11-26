package io.sentrius.sso.genai;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonParser;
import io.sentrius.sso.core.utils.JsonUtil;
import io.sentrius.sso.genai.model.LLMResponse;
import lombok.*;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * OpenAI Responses API response model.
 *
 * This replaces legacy Chat Completions (choices[]) handling.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Response {

    // =========================
    // Top-level fields
    // =========================

    private String id;
    private String object;

    @JsonProperty("created")
    @JsonAlias("created_at")
    private Long created;

    private String model;

    /** Core response output (assistant messages, tool calls, etc.) */
    @JsonProperty("output")
    @JsonAlias("output_items")
    private List<OutputItem> outputItems;

    private Usage usage;

    @JsonProperty("service_tier")
    private String serviceTier;

    @JsonProperty("system_fingerprint")
    private String systemFingerprint;

    // =========================
    // Nested models
    // =========================

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OutputItem {

        private String id;
        private String type;
        private String status;
        private String role;
        private List<ContentItem> content;

        /** Safe accessor */
        public List<ContentItem> safeContent() {
            return content == null ? Collections.emptyList() : content;
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ContentItem {

        private String type;
        private String text;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Usage {

        @JsonProperty("input_tokens")
        private Integer inputTokens;

        @JsonProperty("output_tokens")
        private Integer outputTokens;

        @JsonProperty("total_tokens")
        private Integer totalTokens;
    }

    // =========================
    // Convenience helpers
    // =========================

    /** Defensive accessor */
    public List<OutputItem> safeOutputItems() {
        return outputItems == null ? Collections.emptyList() : outputItems;
    }

    /**
     * Concatenates all assistant output text into one string.
     * Safe for empty responses.
     */
    public String concatenateResponses() {
        return safeOutputItems().stream()
            .filter(o -> "assistant".equals(o.getRole()))
            .flatMap(o -> o.safeContent().stream())
            .filter(c -> "output_text".equals(c.getType()) || "text".equals(c.getType()))
            .map(ContentItem::getText)
            .collect(Collectors.joining());
    }

    /**
     * Returns the first assistant message text (most common use case).
     */
    public String getFirstAssistantText() {
        return safeOutputItems().stream()
            .filter(o -> "assistant".equals(o.getRole()))
            .flatMap(o -> o.safeContent().stream())
            .filter(c -> "output_text".equals(c.getType()) || "text".equals(c.getType()))
            .map(ContentItem::getText)
            .findFirst()
            .orElse("");
    }
}
