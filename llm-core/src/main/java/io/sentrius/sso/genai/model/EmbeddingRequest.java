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
 * <p>
 * Inspired by LiLittleCat's ChatCopmletionRequestBody
 * </p>
 * see:
 * <a href="https://platform.openai.com/docs/api-reference/chat">https://platform.openai.com/docs/api-reference/chat</a>
 *
 * borrowed from <a href="https://github.com/LiLittleCat">LiLittleCat</a>
 *
 * @since 2023/3/2
 */
@Data
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class EmbeddingRequest extends BaseGenerativeRequest {
    /**
     * Required
     * <p>
     * The messages to generate chat completions for, in the <a
     * href=https://platform.openai.com/docs/guides/embeddings</a>.
     */
    @JsonProperty(value = "input")
    private String input;
}
