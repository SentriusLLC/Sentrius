package io.dataguardians.sso.integrations.openai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.dataguardians.security.TokenProvider;
import io.dataguardians.sso.core.model.LLMResponse;
import io.dataguardians.sso.core.utils.JsonUtil;
import io.dataguardians.sso.integrations.exceptions.HttpException;
import io.dataguardians.sso.integrations.openai.model.TwoPartyRequest;
import io.dataguardians.sso.integrations.openai.model.endpoints.ChatApiEndpointRequest;
import lombok.extern.slf4j.Slf4j;
import org.thymeleaf.util.StringUtils;

/**
 * Query compliance scorer with user defined rules to be provided to OpenAI
 */
@Slf4j
public class OpenAITwoPartyMonitor extends DataGenerator<TwoPartyRequest, LLMResponse> {

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
    public String generateInput(TwoPartyRequest on) {
        String queryStr = on.getSystemInput(); //" Here is the user's objective: " + on.getUserObjective() + ".";


        return queryStr;
    }

    public LLMResponse generate(TwoPartyRequest on) throws HttpException, JsonProcessingException {
        ChatApiEndpointRequest request =
            ChatApiEndpointRequest.builder().userInput(generateUserInput( on)).systemInput(generateInput(on)).build();
        log.info("Generating compliance score for: " + on);
        request.setTemperature(0.8f);
        Response hello = api.sample(request, Response.class);
        try{
            var resp = hello.concatenateResponses();
            var objectNode = JsonUtil.MAPPER.readValue(resp, ObjectNode.class);
            var dbl = Double.valueOf(objectNode.get("score").asText());
            log.info("score would be {}", resp);
            log.info("question would be {}", objectNode.get("question").asText());
            var bldr = LLMResponse.builder();
            if (objectNode.has("question")){
                bldr.question(objectNode.get("question").asText());
            }
            return bldr.score(dbl).response(objectNode.get("explanation").asText()).build();
        } catch (Exception e) {
            log.info("Error parsing compliance score: " + hello.concatenateResponses());
            return LLMResponse.builder().score(0.0).response("Error parsing compliance score").build();
        }
    }

    private String generateUserInput(TwoPartyRequest userInput) {
        String queryStr = "";
        if (!StringUtils.isEmpty(userInput.getPreviousPrompt()) && !StringUtils.isEmpty(userInput.getPromptResponse())) {
            queryStr += "You previously asked the user of their session: " + userInput.getPreviousPrompt() + ". This" +
            " " +
            "was their response: " + userInput.getPromptResponse() + ". Can you incorporate this information into " +
            "your risk assessment and confidence score? If they are avoiding your question don't re-ask, just raise " +
                "your risk score.";
        }
        queryStr +=  "Can you give me a confidence score from 0 to 1, to two decimal places, on whether the next 10 " +
            "terminal log " +
            "output" +
            " from" +
        " a single terminal session are exhibiting risky behavior, where 0 is normal and 1 would be a session you" +
            " acting as a human would ask to stop. Provide the score, an explanation of why, and a question to ask " +
            "the user, but only if needed, in JSON with the fields score, explanation, and question. Here is the " +
            "user's last ten " +
            "commands: " + userInput.getUserInput() + ".";
        return queryStr;
    }

}