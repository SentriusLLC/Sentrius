package io.sentrius.sso.genai;

import java.io.IOException;
import java.util.Objects;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.sentrius.sso.security.TokenProvider;
import io.sentrius.sso.genai.model.ApiEndPointRequest;
import io.sentrius.sso.integrations.exceptions.HttpException;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * A GenerativeAPI class represents an API that provides methods for generating requests and samples.
 * It allows users to build a request body by passing an ApiEndPointRequest object,
 * and generate a sample using the same object.
 * Users can also specify the type of the sample they want to generate by passing a Class object.
 *
 * @author [Marc Parisi]
 * @version 1.0
 * @since 09/03/2021
 */
@Slf4j
public class GenerativeAPI {

    protected final TokenProvider authToken;

    protected OkHttpClient client;

    protected final ObjectMapper objectMapper = new ObjectMapper();

    public GenerativeAPI(TokenProvider authToken, OkHttpClient client) {
        Objects.requireNonNull(authToken);
        Objects.requireNonNull(client);
        this.client = client;
        this.authToken = authToken;
    }

    public GenerativeAPI(TokenProvider authToken) {
        this(authToken, new OkHttpClient());
    }

    /**
     * Builds the request body for an API endpoint request.
     *
     * @param request the API endpoint request
     * @return the request body as a string
     */
    String buildRequestBody(final ApiEndPointRequest request) {
        try {
            return objectMapper.writeValueAsString(request.create());
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * ask for response message
     *
     * @param apiRequest
     *            Api Request object
     *
     * @return ChatCompletionResponseBody
     */
    public String sample(final ApiEndPointRequest apiRequest) throws HttpException {
        log.info("is this null? {}", (apiRequest==null));
        Objects.requireNonNull(apiRequest);
        log.info("making request to {}", apiRequest.getEndpoint());
        RequestBody body = RequestBody.create(buildRequestBody(apiRequest), MediaType.get("application/json; charset=utf-8"));
        Request
            request = new Request.Builder().url(apiRequest.getEndpoint()).header("Authorization", "Bearer " + authToken.getToken()).post(body).build();
        log.info("making request to {}", apiRequest.getEndpoint());
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                if (response.body() == null) {
                    log.error("Request failed: {}, please try again", response.message());
                    throw new HttpException(response.code(), "Request failed");
                } else {
                    log.error("Request failed: {}, please try again", response.body().string());
                    throw new HttpException(response.code(), response.body().string());
                }
            } else {
                return response.body().string();
            }
        } catch (IOException e) {
            log.error("Request failed: {}", e.getMessage());
            throw new HttpException(500, e.getMessage());
        }
    }

    /**
     * Samples data from a specified API endpoint request using the provided class type for the data
     *
     * @param apiRequest  the API endpoint request to sample from
     * @param clazz       the class type to parse the data into
     * @return            the sampled data parsed into the provided class type
     * @throws HttpException if there is an issue with the HTTP request or response
     * @throws JsonProcessingException if there is an issue parsing the JSON data
     */
    public <T> T sample(final ApiEndPointRequest apiRequest, Class<T> clazz) throws HttpException, JsonProcessingException {
        return (T) objectMapper.readValue(sample(apiRequest), clazz);
    }
}
