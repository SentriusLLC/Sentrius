package io.sentrius.agent.analysis.agents.verbs;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.sentrius.agent.analysis.agents.agents.AgentConfig;
import io.sentrius.agent.analysis.agents.agents.PromptBuilder;
import io.sentrius.agent.analysis.agents.agents.VerbRegistry;
import io.sentrius.sso.core.exceptions.ZtatException;
import io.sentrius.sso.core.model.verbs.Verb;
import io.sentrius.sso.core.services.agents.LLMService;
import io.sentrius.sso.core.services.agents.ZeroTrustClientService;
import io.sentrius.sso.core.utils.JsonUtil;
import io.sentrius.sso.genai.Message;
import io.sentrius.sso.genai.Response;
import io.sentrius.sso.genai.model.ChatRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class AgentVerbs {


    final ZeroTrustClientService zeroTrustClientService;
    final LLMService llmService;
    final VerbRegistry verbRegistry;
    @Value("${agent.ai.config:agent-config.yaml}")
    private String agentConfigFile;

    final ObjectMapper mapper = new ObjectMapper(new YAMLFactory()); // jackson databind

    public AgentVerbs(ZeroTrustClientService zeroTrustClientService, LLMService llmService, VerbRegistry verbRegistry) throws JsonProcessingException {
        this.zeroTrustClientService = zeroTrustClientService;
        this.llmService = llmService;
        this.verbRegistry = verbRegistry;

        log.info("Loading agent config from {}", agentConfigFile);



    }


    @Verb(name = "prompt_agent", description = "Prompts for agent workload.", isAiCallable = false)
    public ArrayNode promptAgent(Map<String, Object> args) throws ZtatException, IOException {
        InputStream is = getClass().getClassLoader().getResourceAsStream("agent-config.yaml");
        if (is == null) {
            throw new RuntimeException("agent-config.yaml not found on classpath");
        }
        AgentConfig config = new ObjectMapper(new YAMLFactory()).readValue(is, AgentConfig.class);

        log.info("Agent config loaded: {}", config);
            PromptBuilder promptBuilder = new PromptBuilder(verbRegistry, config);
            var prompt = promptBuilder.buildPrompt();
            List<Message> messages = new ArrayList<>();

            messages.add(Message.builder().role("system").content(prompt).build());

            ChatRequest chatRequest = ChatRequest.builder().model("gpt-3.5-turbo").messages(messages).build();
            var resp = llmService.askQuestion(chatRequest);
            Response response = JsonUtil.MAPPER.readValue(resp, Response.class);
            log.info("Response is {}", resp);
            for (Response.Choice choice : response.getChoices()) {
                var content = choice.getMessage().getContent();
                log.info("content is {}", content);
                if (null != content && !content.isEmpty()){
                    JsonNode node = JsonUtil.MAPPER.readTree(content);
                    log.info("Node is {}", node);
                    if (node.get("plan") != null){
                        ArrayNode plan = (ArrayNode) node.get("plan");
                        log.info("Plan is {}", plan);
                        return plan;
                    }
                }
            }
            log.info("ahhh");
            return JsonUtil.MAPPER.createArrayNode();
    }

    @Verb(name = "justify_operations", description = "Chats with an agent to justify operations.", isAiCallable = false)
    public String justifyAgent(Map<String, Object> args) throws ZtatException, IOException {
        InputStream is = getClass().getClassLoader().getResourceAsStream("agent-config.yaml");
        if (is == null) {
            throw new RuntimeException("agent-config.yaml not found on classpath");
        }
        AgentConfig config = new ObjectMapper(new YAMLFactory()).readValue(is, AgentConfig.class);

        log.info("Agent config loaded: {}", config);
        PromptBuilder promptBuilder = new PromptBuilder(verbRegistry, config);
        var prompt = promptBuilder.buildPrompt();
        List<Message> messages = new ArrayList<>();

        messages.add(Message.builder().role("system").content(prompt).build());

        ChatRequest chatRequest = ChatRequest.builder().model("gpt-3.5-turbo").messages(messages).build();

        return llmService.askQuestion(chatRequest);
    }
}
