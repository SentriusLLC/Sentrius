package io.sentrius.sso.genai.model.endpoints;

import java.util.ArrayList;
import java.util.List;
import io.sentrius.sso.genai.Message;
import io.sentrius.sso.genai.model.ApiEndPointRequest;
import io.sentrius.sso.genai.model.ChatRequest;
import io.sentrius.sso.genai.model.ChatResponse;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.SuperBuilder;
import org.apache.commons.lang3.StringUtils;

/**
 * Represents a request to the OpenAI Chat API endpoint.
 *
 * This class provides a convenient way to build a request to the OpenAI Chat API. It includes methods to set the input
 * text, the model to use, and the parameters for the request, among others. Once the request is built, it can be sent
 * using the {@link ChatApiEndpoint#send(io.sentrius.sso.genai.model.endpoints.RawConversationRequest)} method.
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

    public static final String API_ENDPOINT = "https://api.openai.com/v1/chat/completions";

    @Builder.Default
    private Float temperature = 1.0F;

    @Override
    public String getEndpoint() {
        return API_ENDPOINT;
    }

    @Builder.Default
    private List<ChatResponse> chatWithHistory = new ArrayList<>();

    @Builder.Default
    private ChatRequest request = ChatRequest.builder().build();


    /**
     * Creates a new instance of the ChatApiEndpoint with the specified API key.
     *
     * This method is used to create a new instance of the ChatApiEndpoint with the specified API key. The API key is
     * required to send requests to the OpenAI Chat API endpoint. If the API key is invalid or not provided, an
     * IllegalArgumentException will be thrown.
     *
     * Example usage:
     *
     * <pre>{@code
     * ChatApiEndpoint endpoint = ChatApiEndpoint.create("my-api-key");
     * }</pre>
     *
     * @param apiKey
     *            The API key to use for requests to the OpenAI Chat API endpoint.
     *
     * @return A new instance of the ChatApiEndpoint.
     *
     * @throws IllegalArgumentException
     *             If the API key is null or empty.
     */
    @Override
    public Object create() {
        return request;
    }

}
