package io.sentrius.sso.genai.model;

import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.sentrius.sso.genai.Message;
import io.sentrius.sso.genai.api.BaseGenerativeRequest;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * LLM Request for chat completions
 * @see <a href="https://platform.openai.com/docs/api-reference/chat">OpenAI Chat API</a>
 */
@Data
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class LLMRequest extends BaseGenerativeRequest {
    /**
     * Required: The messages to generate chat completions for
     */
    @JsonProperty(value = "messages")
    private List<Message> messages;

    /**
     * Optional: Sampling temperature (0-2)
     */
    @JsonProperty(value = "temperature")
    private Float temperature;

    /**
     * Optional: Nucleus sampling parameter (0-1)
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
     * Optional: Presence penalty (-2.0 to 2.0)
     */
    @JsonProperty(value = "presence_penalty")
    private Float presencePenalty;

    /**
     * Optional: Frequency penalty (-2.0 to 2.0)
     */
    @JsonProperty(value = "frequency_penalty")
    private Float frequencyPenalty;

    /**
     * Optional: Logit bias
     */
    @JsonProperty(value = "logit_bias")
    private Map<Object, Object> logitBias;
}
