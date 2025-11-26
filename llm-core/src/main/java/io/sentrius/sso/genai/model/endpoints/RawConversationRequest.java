package io.sentrius.sso.genai.model.endpoints;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import io.sentrius.sso.genai.Message;
import io.sentrius.sso.genai.model.ApiEndPointRequest;
import io.sentrius.sso.genai.model.LLMRequest;
import io.sentrius.sso.genai.model.LLMResponse;
import io.sentrius.sso.genai.model.ResponsesApiRequest;
import io.sentrius.sso.genai.model.ResponsesApiInputItem;
import io.sentrius.sso.genai.model.ResponsesApiContentItem;
import io.sentrius.sso.genai.model.ResponsesApiImageUrl;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.SuperBuilder;

/**
 * Represents a request to the OpenAI Chat API endpoint.
 *
 *
 * Example usage:
 *
 * <pre>{@code
 * ChatApiEndpointRequest request = new ChatApiEndpointRequest.builder().model("davinci").input("Hello, world!")
 *         .build();
 *
 * ChatApiEndpoint endpoint = new ChatApiEndpoint(apiKey);
 * ChatApiResponse response = endpoint.send(request);
 * }</pre>
 *
 */
@Data
@SuperBuilder
public class RawConversationRequest extends ApiEndPointRequest {

    public static final String API_ENDPOINT = "https://api.openai.com/v1/responses";

    @Builder.Default
    private Float temperature = 1.0F;

    @Override
    public String getEndpoint() {
        return API_ENDPOINT;
    }

    @Builder.Default
    private List<LLMResponse> chatWithHistory = new ArrayList<>();

    @Builder.Default
    private LLMRequest request = LLMRequest.builder().build();


    /**
     * Creates a new instance of the Responses API request by converting from Chat Completions format.
     *
     * This method converts the LLMRequest (Chat Completions format) to ResponsesApiRequest format.
     * The main differences:
     * - Endpoint changes from /v1/chat/completions to /v1/responses
     * - Messages are converted to input items with content arrays
     * - max_tokens becomes max_output_tokens
     *
     * @return A ResponsesApiRequest instance ready to be sent to OpenAI's Responses API.
     */
    @Override
    public Object create() {
        // Convert messages from Chat Completions format to Responses API format
        List<ResponsesApiInputItem> inputItems = new ArrayList<>();
        
        if (request.getMessages() != null) {
            inputItems = request.getMessages().stream()
                .map(this::convertMessageToInputItem)
                .collect(Collectors.toList());
        }
        
        // Build the ResponsesApiRequest with converted fields
        ResponsesApiRequest.ResponsesApiRequestBuilder builder = ResponsesApiRequest.builder()
            .model(request.getModel() != null ? request.getModel() : "gpt-4o")
            .input(inputItems);
        
        // Map optional parameters (only those supported by Responses API)
        if (request.getTemperature() != null) {
            builder.temperature(request.getTemperature());
        }
        
        if (request.getTopP() != null) {
            builder.topP(request.getTopP());
        }
        
        if (request.getMaxTokens() != null) {
            builder.maxOutputTokens(request.getMaxTokens());
        }
        
        if (request.getStream() != null) {
            builder.stream(request.getStream());
        }
        
        // Note: stop, presence_penalty, frequency_penalty, and logit_bias are not supported by Responses API
        
        return builder.build();
    }
    
    /**
     * Converts a Message (Chat Completions format) to ResponsesApiInputItem (Responses API format)
     */
    private ResponsesApiInputItem convertMessageToInputItem(Message message) {

        // Defensive null handling
        if (message == null) {
            return ResponsesApiInputItem.builder()
                .role("system")
                .content(List.of())
                .build();
        }

        String role = message.getRole() != null ? message.getRole() : "system";

        /*
         * RESPONSES API RULE:
         * Only USER or SYSTEM messages may use structured input blocks.
         * ASSISTANT / TOOL / HISTORY messages MUST be flattened.
         */
        if (!"user".equals(role) && !"system".equals(role)) {
            return ResponsesApiInputItem.builder()
                .role("system") // treat replayed model output as system facts
                .content(List.of(
                    ResponsesApiContentItem.builder()
                        .type("input_text")
                        .text(safeString(message.getContentAsString()))
                        .build()
                ))
                .build();
        }

        List<ResponsesApiContentItem> contentItems = new ArrayList<>();

        Object content = message.getContent();

        // ---------- SIMPLE STRING CONTENT ----------
        if (content instanceof String) {
            contentItems.add(
                ResponsesApiContentItem.builder()
                    .type("input_text")
                    .text(safeString((String) content))
                    .build()
            );
        }

        // ---------- MULTIMODAL / STRUCTURED CONTENT ----------
        else if (content instanceof List<?>) {
            for (Object item : (List<?>) content) {
                if (!(item instanceof Map<?, ?> map)) {
                    continue;
                }

                String type = (String) map.get("type");

                // TEXT
                if ("text".equals(type)) {
                    Object text = map.get("text");
                    if (text != null) {
                        contentItems.add(
                            ResponsesApiContentItem.builder()
                                .type("input_text")
                                .text(text.toString())
                                .build()
                        );
                    }
                }

                // IMAGE
                else if ("image_url".equals(type)) {
                    Object imageObj = map.get("image_url");
                    String url = null;
                    String detail = "auto";

                    if (imageObj instanceof String s) {
                        url = s;
                    } else if (imageObj instanceof Map<?, ?> imgMap) {
                        Object u = imgMap.get("url");
                        Object d = imgMap.get("detail");
                        if (u != null) url = u.toString();
                        if (d != null) detail = d.toString();
                    }

                    if (url != null) {
                        contentItems.add(
                            ResponsesApiContentItem.builder()
                                .type("input_image")
                                .imageUrl(
                                    ResponsesApiImageUrl.builder()
                                        .url(url)
                                        .detail(detail)
                                        .build()
                                )
                                .build()
                        );
                    }
                }
                else if ("image_base64".equals(type)) {
                    Object imageObj = map.get("image_base64");

                    if (imageObj instanceof String base64 && !base64.isBlank()) {
                        contentItems.add(
                            ResponsesApiContentItem.builder()
                                .type("input_image")
                                .imageBase64(base64)   // ✅ CORRECT
                                .build()
                        );
                    }
                }
            }
        }

        // ---------- GUARANTEE NON-EMPTY CONTENT ----------
        if (contentItems.isEmpty()) {
            contentItems.add(
                ResponsesApiContentItem.builder()
                    .type("input_text")
                    .text("")
                    .build()
            );
        }

        return ResponsesApiInputItem.builder()
            .role(role)
            .content(contentItems)
            .build();
    }

    private static String safeString(String s) {
        return s == null ? "" : s;
    }

}
