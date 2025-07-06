package io.sentrius.agent.analysis.agents.agents;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import com.fasterxml.jackson.databind.node.ArrayNode;
import io.sentrius.agent.analysis.agents.verbs.AgentVerbs;
import io.sentrius.agent.analysis.api.AgentKeyService;
import io.sentrius.agent.config.AgentConfigOptions;
import io.sentrius.sso.core.dto.ztat.AgentExecution;
import io.sentrius.sso.core.dto.ztat.ZtatRequestDTO;
import io.sentrius.sso.core.exceptions.ZtatException;
import io.sentrius.sso.core.model.security.Ztat;
import io.sentrius.sso.core.model.verbs.VerbResponse;
import io.sentrius.sso.core.services.agents.AgentClientService;
import io.sentrius.sso.core.services.agents.AgentExecutionService;
import io.sentrius.sso.core.services.agents.ZeroTrustClientService;
import io.sentrius.sso.core.dto.UserDTO;
import io.sentrius.sso.core.services.security.KeycloakService;
import io.sentrius.sso.core.utils.JsonUtil;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "agents.ai.registered.agent.enabled", havingValue = "true", matchIfMissing = false)
public class RegisteredAgent implements ApplicationListener<ApplicationReadyEvent> {


    final ZeroTrustClientService zeroTrustClientService;
    final AgentClientService agentClientService;
    final VerbRegistry verbRegistry;
    final AgentVerbs agentVerbs;
    final AgentExecutionService agentExecutionService;
    final AgentConfigOptions agentConfigOptions;
    final AgentKeyService agentKeyService;
    private final KeycloakService keycloakService;

    private volatile boolean running = true;
    private Thread workerThread;

    public ArrayNode promptAgent(AgentExecution execution) throws ZtatException {
        while(true){
            try {
                log.info("Prompting agent...");
                return agentVerbs.promptAgent(execution,null);
            } catch (ZtatException e) {
                log.info("Mechanisms {}" , e.getMechanisms());
                var endpoint = zeroTrustClientService.createEndPointRequest("prompt_agent", e.getEndpoint());
                ZtatRequestDTO ztatRequestDTO = ZtatRequestDTO.builder()
                    .user(execution.getUser())
                    .command(endpoint.toString())
                    .justification("Registered Agent requires ability to prompt LLM endpoints to begin operations")
                    .summary("Registered Agent requires ability to prompt LLM endpoints to begin operations")
                    .build();
                var request = zeroTrustClientService.requestZtatToken(execution, execution.getUser(),ztatRequestDTO);

                var token = zeroTrustClientService.awaitZtatToken(execution, execution.getUser(), request, 60,
                    TimeUnit.MINUTES);
                execution.setZtatToken(token);
            } catch (Exception e) {
                log.error(e.getMessage());
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public void onApplicationEvent(final ApplicationReadyEvent event) {

        verbRegistry.scanClasspath();

        final UserDTO user = UserDTO.builder()
            .username(zeroTrustClientService.getUsername())
            .build();
        var execution = agentExecutionService.getAgentExecution(user);
        
        var keyPair = agentKeyService.getKeyPair();
        try {
            agentClientService.heartbeat(execution, execution.getUser().getUsername());
        } catch (ZtatException e) {
            throw new RuntimeException(e);
        }
        while(running) {

            try {
                var register = zeroTrustClientService.registerAgent(execution);
                log.info("Registered agent response: {}", register);

                var ztat = JsonUtil.MAPPER.readValue(register, Ztat.class);
                execution.setZtatToken(ztat.getZtatToken());
                execution.setCommunicationId(ztat.getCommunicationId());
                break;
            }catch (Exception | ZtatException e) {

                log.error(e.getMessage());
                log.info("Registering v1.0.2 agent failed. Retrying in 10 seconds...");

                try {
                    var agentName = agentConfigOptions.getNamePrefix() + "-" + UUID.randomUUID().toString();
                    var base64PublicKey = agentKeyService.getBase64PublicKey(keyPair.getPublic());
                    var agentRegistrationDTO = agentClientService.bootstrap(
                        agentName, base64PublicKey
                        , keyPair.getPublic().getAlgorithm()
                    );

                    var encryptedSecret = agentRegistrationDTO.getClientSecret();
                    var decryptedSecret = agentKeyService.
                        decryptWithPrivateKey(encryptedSecret, keyPair.getPrivate());
                    keycloakService.createKeycloakClient(
                        agentName,
                        decryptedSecret
                    );

                    final UserDTO newUserDTO = UserDTO.builder()
                        .username(zeroTrustClientService.getUsername())
                        .build();
                    execution = agentExecutionService.getAgentExecution(newUserDTO);
                } catch (Exception e1) {
                    log.error("Failed to bootstrap agent", e1);
                } catch (ZtatException ex) {
                    log.error("Failed to bootstrap agent", ex);
                }
                try {
                    Thread.sleep(10_000);
                } catch (InterruptedException ex) {
                    throw new RuntimeException(ex);
                }
            }
        }

        workerThread = new Thread(() -> {
            try {

                log.info("Username: {}", user.getUsername());
                log.info("Registering v1.0.2 agent...");



                var agentExecution = agentExecutionService.getAgentExecution(user);
                var response = promptAgent(agentExecution);
                while (running) {
                    try {
                        log.info("Got response: {}", response);

                        VerbResponse priorResponse = null;
                        Map<String, Object> args = new HashMap<>();

                        for (var node : response) {
                            if (node.get("verb") != null) {
                                var verb = node.get("verb").asText();
                                log.info("Executing verb: {}", verb);
                                priorResponse = verbRegistry.execute(agentExecution, priorResponse, verb, args);
                            }
                            log.info("Node: {}", node);
                        }

                    } catch (Exception e) {
                        log.error("Exception in agent loop", e);
                    }

                    // Sleep between prompts
                    log.info("Sleeping for 5 seconds");
                    Thread.sleep(5_000);
                }

            } catch (Exception | ZtatException e) {
                log.error("Fatal error in RegisteredAgent", e);
            }
        });

        workerThread.setName("RegisteredAgent-Worker");
        workerThread.start();
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down RegisteredAgent...");
        running = false;
        if (workerThread != null) {
            workerThread.interrupt();
        }
    }

}
