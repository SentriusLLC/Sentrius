package io.sentrius.sso.genai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.sentrius.sso.core.utils.JsonUtil;
import io.sentrius.sso.genai.model.ChatResponse;
import io.sentrius.sso.genai.model.Conversation;
import io.sentrius.sso.genai.model.endpoints.ConversationRequest;
import io.sentrius.sso.integrations.exceptions.HttpException;
import io.sentrius.sso.security.TokenProvider;
import lombok.extern.slf4j.Slf4j;

/**
 * Query compliance scorer with user defined rules to be provided to OpenAI
 */
@Slf4j
public class ChatConversation extends DataGenerator<Conversation, ChatResponse> {

    public ChatConversation(
        TokenProvider token, GenerativeAPI generator, GeneratorConfiguration config) {
        super(token, generator, config);
    }

    @Override
    protected String generateInput(Conversation on) {
        return on.getSystemConfines();
    }


    @Override
    public ChatResponse generate(Conversation on) throws HttpException, JsonProcessingException {

        ConversationRequest request =
            ConversationRequest.builder().systemInput(generateInput(on)).newMessage(
                on.getNewUserMessage()).user(
                "user").chatWithHistory(on.getPreviousMessages()).build();
        log.info("Generating compliance score for: " + on);
        request.setTemperature(0.8f);
        Response hello = api.sample(request, Response.class);
        try{
            var resp = hello.concatenateResponses();
            var objectNode = JsonUtil.MAPPER.readValue(resp, ObjectNode.class);
            var message = objectNode.get("message") != null ? objectNode.get("message").asText() : "";
            var alert = objectNode.get("alert") != null ? objectNode.get("alert").asBoolean() : false;

            var terminalMessage = objectNode.get("terminal");
            if (null != terminalMessage) {
                return ChatResponse.builder().role("system").content(message).terminalMessage(terminalMessage.asText()).alert(alert).build();
            }
            return ChatResponse.builder().role("system").content(message).alert(alert).build();
        } catch (Exception e) {
            log.info("Error parsing compliance score: " + hello.concatenateResponses());
            return ChatResponse.builder().role("system").content("Error parsing response").build();
        }
    }

}