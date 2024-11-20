package io.sentrius.sso.genai;

import java.util.List;
import java.util.stream.Collectors;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response class is responsible for collecting and storing user responses. It provides a string
 * concatenation method to combine all responses. Use the 'addResponse' method to collect user
 * responses and the 'concatenateResponses' method to combine them into a single string.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Response {

    @JsonProperty(value = "id")
    public String id;

    @JsonProperty(value = "object")
    public String object;

    @JsonProperty(value = "created")
    public Long created;

    @JsonProperty(value = "model")
    public String model;

    @JsonProperty(value = "choices")
    public List<Choice> choices;

    @JsonProperty(value = "usage")
    public Usage usage;

    @JsonProperty(value = "service_tier", required = false)
    public String serviceTier;

    /**
     * Response class is responsible for collecting and storing user responses. It provides a string
     * concatenation method to combine all responses. Use the 'addResponse' method to collect user
     * responses and the 'concatenateResponses' method to combine them into a single string.
     */
    @Data
    public static class Choice {

        @JsonProperty(value = "index")
        public Integer index;

        @JsonProperty(value = "message")
        public Message message;

        @JsonProperty(value = "finish_reason")
        public String finishReason;

        @JsonProperty(value = "logprobs")
        public Integer logprobs;
    }

    /**
     * Response class is responsible for collecting and storing user responses. It provides a string
     * concatenation method to combine all responses. Use the 'addResponse' method to collect user
     * responses and the 'concatenateResponses' method to combine them into a single string.
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Usage {

        @JsonProperty(value = "prompt_tokens")
        public Integer promptTokens;

        @JsonProperty(value = "completion_tokens")
        public Integer completionTokens;

        @JsonProperty(value = "total_tokens")
        public Integer totalTokens;
    }

    @JsonProperty(value = "system_fingerprint")
    String systemFingerprint;

    /**
     * This method concatenates all responses in a Response object and returns a string.
     *
     * @return      a string that contains all responses in a Response object
     */
    public String concatenateResponses() {
        return getChoices().stream().map(x -> x.getMessage().getContent()).collect(Collectors.joining());
    }
}
