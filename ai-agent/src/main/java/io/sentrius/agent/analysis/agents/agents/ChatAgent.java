package io.sentrius.agent.analysis.agents.agents;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.sentrius.agent.analysis.agents.verbs.AgentVerbs;
import io.sentrius.agent.analysis.agents.verbs.ChatVerbs;
import io.sentrius.agent.analysis.api.AgentKeyService;
import io.sentrius.agent.analysis.api.UserCommunicationService;
import io.sentrius.agent.analysis.model.LLMResponse;
import io.sentrius.agent.config.AgentConfigOptions;
import io.sentrius.sso.core.dto.UserDTO;
import io.sentrius.sso.core.dto.agents.AgentExecution;
import io.sentrius.sso.core.dto.agents.AgentExecutionContextDTO;
import io.sentrius.sso.core.exceptions.ZtatException;
import io.sentrius.sso.core.model.security.Ztat;
import io.sentrius.sso.core.model.verbs.VerbResponse;
import io.sentrius.sso.core.services.agents.AgentClientService;
import io.sentrius.sso.core.services.agents.AgentExecutionService;
import io.sentrius.sso.core.services.agents.ZeroTrustClientService;
import io.sentrius.sso.core.services.security.KeycloakService;
import io.sentrius.sso.core.utils.JsonUtil;
import io.sentrius.sso.genai.Message;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(name = "agents.ai.chat.agent.enabled", havingValue = "true", matchIfMissing = false)
public class ChatAgent extends BaseEnterpriseAgent {


    final ZeroTrustClientService zeroTrustClientService;
    final AgentClientService agentClientService;
    final VerbRegistry verbRegistry;
    final AgentExecutionService agentExecutionService;
    final UserCommunicationService userCommunicationService;
    final AgentConfigOptions agentConfigOptions;
    final AgentKeyService agentKeyService;
    private final KeycloakService keycloakService;
    final ChatVerbs chatVerbs;

    private volatile boolean running = true;
    private Thread workerThread;

    private AgentExecution agentExecution;


    @Autowired
    public ChatAgent(
        AgentVerbs agentVerbs, ZeroTrustClientService zeroTrustClientService, AgentClientService agentClientService,
        VerbRegistry verbRegistry, AgentExecutionService agentExecutionService, UserCommunicationService userCommunicationService,
        AgentConfigOptions agentConfigOptions, AgentKeyService agentKeyService, KeycloakService keycloakService,
        ChatVerbs chatVerbs
    ) {
        super(agentVerbs, zeroTrustClientService, agentClientService, verbRegistry);
        this.zeroTrustClientService = zeroTrustClientService;
        this.agentClientService = agentClientService;
        this.verbRegistry = verbRegistry;
        this.agentExecutionService = agentExecutionService;
        this.userCommunicationService = userCommunicationService;
        this.agentConfigOptions = agentConfigOptions;
        this.agentKeyService = agentKeyService;
        this.keycloakService = keycloakService;
        this.chatVerbs = chatVerbs;
    }

    @Override
    public void onApplicationEvent(final ApplicationReadyEvent event) {

        verbRegistry.scanClasspath();


        var keyPair = agentKeyService.getKeyPair();

        try {
            var agentName = agentConfigOptions.getNamePrefix() + "-" + UUID.randomUUID().toString();
            var base64PublicKey = agentKeyService.getBase64PublicKey(keyPair.getPublic());
            var agentRegistrationDTO = agentClientService.bootstrap(agentConfigOptions.getClientId(), agentName,
                base64PublicKey
                , keyPair.getPublic().getAlgorithm());

            var encryptedSecret = agentRegistrationDTO.getClientSecret();
            var decryptedSecret = agentKeyService.
                decryptWithPrivateKey(encryptedSecret, keyPair.getPrivate());
            keycloakService.createKeycloakClient(agentName,
                decryptedSecret);


        } catch (ZtatException e) {
            throw new RuntimeException(e);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }


        final UserDTO user = UserDTO.builder()
            .username(zeroTrustClientService.getUsername())
            .build();
        agentExecution = agentExecutionService.getAgentExecution(user);

        try {
            agentClientService.heartbeat(agentExecution, agentExecution.getUser().getUsername());
        } catch (ZtatException e) {
            throw new RuntimeException(e);
        }

        while(running) {

            try {
                var register = zeroTrustClientService.registerAgent(agentExecution);
                log.info("Registered agent response: {}", register);

                var ztat = JsonUtil.MAPPER.readValue(register, Ztat.class);
                agentExecution.setZtatToken(ztat.getZtatToken());
                agentExecution.setCommunicationId(ztat.getCommunicationId());
                break;
            }catch (Exception | ZtatException e) {

                log.error(e.getMessage());
                log.info("Registering v1.0.2 agent failed. Retrying in 10 seconds...");
                try {
                    Thread.sleep(10_000);
                } catch (InterruptedException ex) {
                    throw new RuntimeException(ex);
                }
            }
        }

        try {
            verbRegistry.scanEndpoints(agentExecution);
        } catch (ZtatException e) {
            throw new RuntimeException(e);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        int allowedFailures = 20;
        log.info("Agent Registered...");
        AgentExecutionContextDTO agentExecutionContext = AgentExecutionContextDTO.builder().build();
        agentExecutionService.setExecutionContextDTO(agentExecution, agentExecutionContext);
        LLMResponse response = null;
        AgentConfig config = null;
        try {
            config = chatVerbs.getAgentConfig(agentExecution);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (ZtatException e) {
            throw new RuntimeException(e);
        }
        PromptBuilder promptBuilder = new PromptBuilder(verbRegistry, config);
        var prompt = promptBuilder.buildPrompt(false);
        try {
            if (agentConfigOptions.getType().equalsIgnoreCase("chat-autonomous")) {


                response = chatVerbs.promptAgent(agentExecution, agentExecutionContext, prompt);
            }
        } catch (ZtatException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }


        if (agentConfigOptions.getType().equalsIgnoreCase("chat-autonomous") && response == null) {
            log.error("Chat autonomous agent mode enabled but no response received from promptAgent, shutting down...");
            throw new RuntimeException("Chat autonomous agent mode enabled but no response received from promptAgent");
        }
        VerbResponse lastVerbResponse = null;
        LLMResponse nextResponse = null;
        List<VerbResponse> verbResponses = new ArrayList<>();
        while(running) {


                try {

                    Thread.sleep(5_000);
                    agentClientService.heartbeat(agentExecution, agentExecution.getUser().getUsername());
                    if (agentConfigOptions.getType().equalsIgnoreCase("chat-autonomous")) {
                        log.info("Chat autonomous agent mode enabled, executing workload...");
                        VerbResponse priorResponse = null;
                        Map<String, Object> args = new HashMap<>();

                        var arguments = response.getArguments();
                        if (null != response) {
                            if (response.getNextOperation() != null && !response.getNextOperation().isEmpty()) {
                                var executionResponse = verbRegistry.execute(
                                    agentExecution,
                                    agentExecutionContext,
                                    lastVerbResponse,
                                    response.getNextOperation(), arguments
                                );
                                verbResponses.add(executionResponse);
                                lastVerbResponse = executionResponse;


//                                        chatAgent.getAgentExecution().addMessages(Message.builder().role("System")
//                                        .content("System executed operation: " + response.getNextOperation()).build());
                                var responses = agentExecutionContext.getAgentDataList();
                                var planResponse =
                                    responses.isEmpty() ? "" :
                                        responses.get(responses.size() - 1).asText();
                                nextResponse = chatVerbs.interpret_plan_response(
                                    agentExecution,
                                    agentExecutionContext,
                                    verbRegistry.getVerbs().get(response.getNextOperation()),
                                    planResponse
                                );


                                response = nextResponse;
                            }

                        }else {
                            response = chatVerbs.promptAgent(agentExecution, agentExecutionContext, prompt);

                        }

                        continue;
                    }
                    allowedFailures = 20; // Reset allowed failures on successful heartbeat
                } catch (ZtatException | Exception ex) {
                    agentExecutionContext.addMessages(Message.builder().role("system").content(
                        "You caused the following error. Please re-validate you chose the right operations or " +
                            "endpoints for the context" + 
                        ex.getMessage()).build());


                    ex.printStackTrace();
                    if (allowedFailures-- <= 0) {
                        log.error("Failed to heartbeat agent after multiple attempts, shutting down...");
                        throw new RuntimeException(ex);
                    } else {
                        log.warn("Heartbeat failed, retrying... Remaining attempts: {}", allowedFailures);
                    }

                }

        }

    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down ChatAgent...");
        running = false;
        if (workerThread != null) {
            workerThread.interrupt();
        }
    }

    public AgentExecution getAgentExecution() {
        return agentExecution;
    }
}
