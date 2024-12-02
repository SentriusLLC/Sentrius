package io.dataguardians.sso.integrations.openai;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.dataguardians.security.TokenProvider;
import io.dataguardians.sso.core.model.LLMResponse;
import io.dataguardians.sso.integrations.exceptions.HttpException;
import io.dataguardians.sso.integrations.openai.endpoints.ChatApiEndpointRequest;
import lombok.extern.slf4j.Slf4j;

/**
 * Query compliance scorer with user defined rules to be provided to OpenAI
 */
@Slf4j
public class OpenAITwoPartyMonitor extends DataGenerator<LLMResponse> {

    public OpenAITwoPartyMonitor(
        TokenProvider token, GenerativeAPI generator, GeneratorConfiguration config,
        TerminalLogConfiguration complianceConfig) {
        super(token, generator, config);
    }

    /**
     * Generates input for the generative AI endpoint.
     *
     * @return Question to be asked to the generative AI endpoint.
     */
    @Override
    public String generateInput(String on) {
        String queryStr = "This is a mission critical system with admins performing break glass activities through " +
            "ssh. You are monitoring as a two party system and acting as a second human monitoring a session. ";

        queryStr += ". Can you give me a confidence score from 0 to 1 on whether the next 10 terminal log output from" +
            " a single terminal session are exhibiting risky behavior, where 0 is normal and 1 would be a session you" +
            " acting as a human would ask to stop. Here are the last few commands:  "
            + on + ";  Please only provide the score followed by a colon and a short explanation of why you gave that score.";

        return queryStr;
    }

    public LLMResponse generate(String on) throws HttpException, JsonProcessingException {
        ChatApiEndpointRequest request = ChatApiEndpointRequest.builder().input(generateInput(on)).build();
        log.info("Generating compliance score for: " + on);
        request.setTemperature(0.5f);
        Response hello = api.sample(request, Response.class);
        try{
            var resp = hello.concatenateResponses();
            var splitResponse = resp.split(":");
            var dbl = Double.valueOf(splitResponse[0]);
            return LLMResponse.builder().score(dbl).response(splitResponse[1]).build();
        } catch (Exception e) {
            log.info("Error parsing compliance score: " + hello.concatenateResponses());
            return LLMResponse.builder().score(0.0).response("Error parsing compliance score").build();
        }
    }

}