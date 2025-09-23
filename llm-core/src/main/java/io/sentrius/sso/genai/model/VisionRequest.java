package io.sentrius.sso.genai.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.sentrius.sso.genai.VisionMessage;
import io.sentrius.sso.genai.api.BaseGenerativeRequest;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;
import java.util.Map;

/**
 * Request model for OpenAI Vision API.
 * Similar to LLMRequest but uses VisionMessage to support multimodal content.
 */
@Data
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class VisionRequest extends BaseGenerativeRequest {
    
    /**
     * Required: The messages for vision completion, supporting both text and images
     */
    @JsonProperty(value = "messages")
    private List<VisionMessage> messages;
    
    /**
     * Optional: Sampling temperature (0-2)
     */
    @JsonProperty(value = "temperature")
    private Float temperature;
    
    /**
     * Optional: Nucleus sampling parameter
     */
    @JsonProperty(value = "top_p")
    private Float topP;
    
    /**
     * Optional: Number of completions to generate
     */
    @JsonProperty(value = "n")
    private Integer n;
    
    /**
     * Optional: Whether to stream responses
     */
    @JsonProperty(value = "stream")
    private Boolean stream;
    
    /**
     * Optional: Stop sequences
     */
    @JsonProperty(value = "stop")
    private List<String> stop;
    
    /**
     * Optional: Maximum tokens in response
     */
    @JsonProperty(value = "max_tokens")
    private Integer maxTokens;
    
    /**
     * Optional: Presence penalty
     */
    @JsonProperty(value = "presence_penalty")
    private Float presencePenalty;
    
    /**
     * Optional: Frequency penalty
     */
    @JsonProperty(value = "frequency_penalty")
    private Float frequencyPenalty;
    
    /**
     * Optional: Logit bias
     */
    @JsonProperty(value = "logit_bias")
    private Map<Object, Object> logitBias;
}
