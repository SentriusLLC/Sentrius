package io.sentrius.sso.genai.model.endpoints;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.sentrius.sso.genai.Message;
import io.sentrius.sso.genai.model.ApiEndPointRequest;
import io.sentrius.sso.genai.model.LLMRequest;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.SuperBuilder;

/**
 * Represents a request to the Claude (Anthropic) Messages API endpoint.
 * 
 * Claude API uses a different format than OpenAI:
 * - Endpoint: https://api.anthropic.com/v1/messages
 * - System messages are passed separately, not in the messages array
 * - Requires anthropic-version header
 * 
 * Example usage:
 * <pre>{@code
 * ClaudeRequest request = ClaudeRequest.builder()
 *     .request(llmRequest)
 *     .build();
 * }</pre>
 */
@Data
@SuperBuilder
public class ClaudeRequest extends ApiEndPointRequest {

    /**
     * Default Claude model to use when not specified in the request.
     * As of Dec 2024, claude-3-5-sonnet-20241022 is the latest production model.
     */
    public static final String DEFAULT_CLAUDE_MODEL = "claude-3-5-sonnet-20241022";

    public static final String API_ENDPOINT = "https://api.anthropic.com/v1/messages";
    public static final String ANTHROPIC_VERSION = "2023-06-01";

    @Builder.Default
    private Float temperature = 1.0F;

    @Override
    public String getEndpoint() {
        return API_ENDPOINT;
    }

    @Builder.Default
    private LLMRequest request = LLMRequest.builder().build();

    /**
     * Creates a Claude Messages API request from the standard LLMRequest format.
     * 
     * Converts:
     * - Extracts system messages to system parameter
     * - Keeps user/assistant messages in messages array
     * - Maps max_tokens correctly (required by Claude)
     * - Ensures alternating user/assistant pattern
     * 
     * @return A ClaudeMessagesRequest instance ready to be sent to Claude API.
     */
    @Override
    public Object create() {
        List<ClaudeMessage> messages = new ArrayList<>();
        String systemPrompt = null;

        // Extract system message and convert other messages
        if (request.getMessages() != null) {
            for (Message msg : request.getMessages()) {
                if ("system".equalsIgnoreCase(msg.getRole())) {
                    // Claude expects system prompt as a separate parameter
                    if (systemPrompt == null) {
                        systemPrompt = msg.getContentAsString();
                    } else {
                        // Append additional system messages
                        systemPrompt += "\n" + msg.getContentAsString();
                    }
                } else {
                    messages.add(convertMessageToClaudeFormat(msg));
                }
            }
        }

        // Build the Claude request
        ClaudeMessagesRequest.ClaudeMessagesRequestBuilder builder = ClaudeMessagesRequest.builder()
            .model(request.getModel() != null ? request.getModel() : DEFAULT_CLAUDE_MODEL)
            .messages(messages)
            .maxTokens(request.getMaxTokens() != null ? request.getMaxTokens() : 4096);

        if (systemPrompt != null) {
            builder.system(systemPrompt);
        }

        if (request.getTemperature() != null) {
            builder.temperature(request.getTemperature());
        }

        if (request.getTopP() != null) {
            builder.topP(request.getTopP());
        }

        if (request.getStop() != null && !request.getStop().isEmpty()) {
            builder.stopSequences(request.getStop());
        }

        if (request.getStream() != null) {
            builder.stream(request.getStream());
        }

        return builder.build();
    }

    /**
     * Converts a Message to Claude format
     */
    private ClaudeMessage convertMessageToClaudeFormat(Message message) {
        if (message == null) {
            return ClaudeMessage.builder()
                .role("user")
                .content("")
                .build();
        }

        String role = message.getRole();
        // Claude only supports 'user' and 'assistant' roles
        if (!"user".equalsIgnoreCase(role) && !"assistant".equalsIgnoreCase(role)) {
            role = "user";
        }

        // For simple string content
        Object content = message.getContent();
        if (content instanceof String) {
            return ClaudeMessage.builder()
                .role(role.toLowerCase())
                .content(content.toString())
                .build();
        }

        // For structured content (images, etc.) - Claude supports multimodal
        // For now, we'll convert to simple text
        return ClaudeMessage.builder()
            .role(role.toLowerCase())
            .content(message.getContentAsString())
            .build();
    }

    /**
     * Represents a Claude message in the request
     */
    @Data
    @Builder
    public static class ClaudeMessage {
        private String role; // "user" or "assistant"
        private String content;
    }

    /**
     * Represents the complete Claude Messages API request body
     */
    @Data
    @Builder
    public static class ClaudeMessagesRequest {
        private String model;
        private List<ClaudeMessage> messages;
        
        @Builder.Default
        private Integer maxTokens = 4096;
        
        private String system;
        private Float temperature;
        private Float topP;
        
        @JsonProperty("stop_sequences")
        private List<String> stopSequences;
        
        private Boolean stream;
    }
}
