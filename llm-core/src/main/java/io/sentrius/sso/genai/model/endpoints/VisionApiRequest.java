package io.sentrius.sso.genai.model.endpoints;

import io.sentrius.sso.genai.model.ApiEndPointRequest;
import io.sentrius.sso.genai.model.VisionRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

/**
 * API endpoint request for OpenAI Vision API.
 * Wraps VisionRequest and provides the endpoint URL.
 */
@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class VisionApiRequest extends ApiEndPointRequest {
    
    private VisionRequest request;
    
    @Override
    public String getEndpoint() {
        return "https://api.openai.com/v1/responses";
    }
    
    @Override
    public Object create() {
        return request;
    }
}
