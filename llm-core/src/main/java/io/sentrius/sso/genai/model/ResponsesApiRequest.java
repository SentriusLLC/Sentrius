package io.sentrius.sso.genai.model;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request format for OpenAI Responses API (/v1/responses)
 * This is the newer API that replaces the Chat Completions API.
 * 
 * Only includes parameters supported by the Responses API.
 * Note: stop, presence_penalty, frequency_penalty, and logit_bias are NOT supported.
 * 
 * @see <a href="https://platform.openai.com/docs/api-reference/responses">OpenAI Responses API</a>
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ResponsesApiRequest {
    
    /**
     * Required: The model identifier (e.g., "gpt-4o", "gpt-4", "gpt-3.5-turbo")
     */
    @JsonProperty(value = "model")
    private String model;
    
    /**
     * Optional: The input messages in Responses API format
     * Each item has a "role" and "content" array
     */
    @JsonProperty(value = "input")
    private List<ResponsesApiInputItem> input;
    
    /**
     * Optional: System/developer message inserted as context
     */
    @JsonProperty(value = "instructions")
    private String instructions;
    
    /**
     * Optional: Maximum number of tokens to generate
     */
    @JsonProperty(value = "max_output_tokens")
    private Integer maxOutputTokens;
    
    /**
     * Optional: Sampling temperature (0-2)
     */
    @JsonProperty(value = "temperature")
    private Float temperature;
    
    /**
     * Optional: Nucleus sampling (top_p)
     */
    @JsonProperty(value = "top_p")
    private Float topP;
    
    /**
     * Optional: Whether to stream the response
     */
    @JsonProperty(value = "stream")
    private Boolean stream;
    
    /**
     * Optional: Whether to store the conversation state
     */
    @JsonProperty(value = "store")
    private Boolean store;
    
    /**
     * Optional: Tool definitions for function calling
     */
    @JsonProperty(value = "tools")
    private List<Object> tools;
    
    /**
     * Optional: Maximum number of tool calls to allow
     */
    @JsonProperty(value = "max_tool_calls")
    private Integer maxToolCalls;
}

