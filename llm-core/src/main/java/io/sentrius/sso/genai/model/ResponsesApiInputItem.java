package io.sentrius.sso.genai.model;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Input item for OpenAI Responses API
 * Represents a single message in the conversation
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ResponsesApiInputItem {
    
    /**
     * Role of the message sender (e.g., "user", "assistant", "system")
     */
    @JsonProperty(value = "role")
    private String role;
    
    /**
     * Content array containing the message content
     */
    @JsonProperty(value = "content")
    private List<ResponsesApiContentItem> content;
}
