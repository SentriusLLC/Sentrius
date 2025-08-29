package io.sentrius.sso.genai.model.endpoints;

import java.util.ArrayList;
import java.util.List;
import io.sentrius.sso.genai.Message;
import io.sentrius.sso.genai.model.ApiEndPointRequest;
import io.sentrius.sso.genai.model.EmbeddingRequest;
import io.sentrius.sso.genai.model.LLMRequest;
import io.sentrius.sso.genai.model.LLMResponse;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.SuperBuilder;

/**
 * Represents a request to the OpenAI Chat API endpoint.
 *
 * This class provides a convenient way to build a request to the OpenAI Chat API. It includes methods to set the input
 * text, the model to use, and the parameters for the request, among others. Once the request is built, it can be sent
 * using the {@link ChatApiEndpoint#send(EmbeddingApiRequest)} method.
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
public class EmbeddingApiRequest extends ApiEndPointRequest {

    public static final String API_ENDPOINT = "https://api.openai.com/v1/embeddings";


    @Override
    public String getEndpoint() {
        return API_ENDPOINT;
    }

    @Builder.Default
    private String input = "";

    @Builder.Default
    private String model = "text-embedding-3-small";

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
     *
     * @return A new instance of the ChatApiEndpoint.
     *
     * @throws IllegalArgumentException
     *             If the API key is null or empty.
     */
    @Override
    public Object create() {
        return EmbeddingRequest.builder().input(input).model(model).build();
    }

}
