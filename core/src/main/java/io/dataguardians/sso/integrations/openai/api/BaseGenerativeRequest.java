package io.dataguardians.sso.integrations.openai.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.experimental.SuperBuilder;

/**
 * <p>
 * Inspired by liLittleCat's ChatGPT package.
 *
 * Codifies the Message request
 * </p>
 *
 * original @author <a href="https://github.com/LiLittleCat">LiLittleCat</a>
 *
 * @since 2023/3/2
 */
/**
 * Base class for generative requests, defining the necessary methods required to generate a request.
 */
@Data
@SuperBuilder(toBuilder = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public abstract class BaseGenerativeRequest {

    /**
     * Defined end user id : https://platform.openai.com/docs/guides/safety-best-practices/end-user-ids
     */
    protected String user;

    /**
     * Model name, if applicable of the request"
     */
    @JsonProperty(value = "model")
    protected String model;
}
