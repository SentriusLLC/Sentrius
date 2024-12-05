package io.dataguardians.sso.integrations.openai.model;

import lombok.Builder;
import lombok.Data;
import lombok.experimental.SuperBuilder;

/**
 * This class represents an API endpoint request that can be used to get the endpoint and create an object.
 * It provides methods to get the endpoint as a string and create an object from the endpoint.
 */
@Data
@SuperBuilder(toBuilder = true)
public abstract class ApiEndPointRequest {

    protected String user;

    protected String userInput;

    protected String systemInput;

    @Builder.Default
    protected int maxTokens = 4096;

    /**
     * Returns the endpoint of the API request. The endpoint refers to the specific location of the resource being accessed by the request.
     * Examples of endpoints may include "/users" or "/products".
     *
     * @return a String representing the endpoint of the request.
     */
    public abstract String getEndpoint();

    /**
     * Creates an instance of the API endpoint request.
     *
     * @return An {@code Object} representing the API endpoint request. The actual
     *         type of the returned object depends on the implementation of this
     *         method in the subclass.
     *
     * @see ApiEndPointRequestBuilder
     *
     */
    public abstract Object create();
}
