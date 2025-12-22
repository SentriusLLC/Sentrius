package io.sentrius.sso.genai.model.endpoints;

import java.util.ArrayList;
import java.util.List;

import io.sentrius.sso.genai.model.ApiEndPointRequest;
import io.sentrius.sso.genai.model.ResponsesApiRequest;
import io.sentrius.sso.genai.model.ResponsesApiInputItem;
import io.sentrius.sso.genai.model.ResponsesApiContentItem;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.SuperBuilder;

/**
 * Represents a request to the OpenAI Chat API endpoint.
 *
 * This class provides a convenient way to build a request to the OpenAI Chat API. It includes methods to set the input
 * text, the model to use, and the parameters for the request, among others. Once the request is built, it can be sent
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
public class ChatApiEndpointRequest extends ApiEndPointRequest {

    public static final String API_ENDPOINT = "https://api.openai.com/v1/responses";

    @Builder.Default
    private Float temperature = 1.0F;

    @Override
    public String getEndpoint() {
        return API_ENDPOINT;
    }

    /**
     * Creates a new instance of the ChatApiEndpoint with the specified API key.
     *
     * This method is used to create a new instance of the ChatApiEndpoint with the specified API key. The API key is
     * required to send requests to the OpenAI Chat API endpoint. If the API key is invalid or not provided, an
     * IllegalArgumentException will be thrown.
     *
     * This method now uses the Responses API format instead of the deprecated Chat Completions format.
     * The main changes:
     * - messages → input (array of InputItems)
     * - max_tokens → max_output_tokens
     * - Each message is converted to an InputItem with content array
     *
     * Example usage:
     *
     * <pre>{@code
     * ChatApiEndpoint endpoint = ChatApiEndpoint.create("my-api-key");
     * }</pre>
     *
     *
     * @return A ResponsesApiRequest instance ready for the Responses API.
     *
     * @throws IllegalArgumentException
     *             If the API key is null or empty.
     */
    @Override
    public Object create() {
        List<ResponsesApiInputItem> input = new ArrayList<>();
        String role = null == user || user.isEmpty() ? "user" : user;
        
        // Add system message first if provided
        if (null != systemInput && !systemInput.isEmpty()) {
            input.add(ResponsesApiInputItem.builder()
                .role("system")
                .content(List.of(ResponsesApiContentItem.builder()
                    .type("input_text")
                    .text(systemInput)
                    .build()))
                .build());
        }
        
        // Add user message
        input.add(ResponsesApiInputItem.builder()
            .role(role)
            .content(List.of(ResponsesApiContentItem.builder()
                .type("input_text")
                .text(userInput != null ? userInput : "")
                .build()))
            .build());
        
        var requestBody = ResponsesApiRequest.builder().model("gpt-3.5-turbo").input(input);
        if (temperature != 1.0F) {
            requestBody.temperature(temperature);
        }
        if (maxTokens != 4096) {
            requestBody.maxOutputTokens(maxTokens);
        }
        return requestBody.build();
    }

}
