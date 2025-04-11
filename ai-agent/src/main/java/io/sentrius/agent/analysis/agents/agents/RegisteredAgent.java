package io.sentrius.agent.analysis.agents.agents;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import com.fasterxml.jackson.databind.node.ArrayNode;
import io.sentrius.agent.analysis.agents.verbs.AgentVerbs;
import io.sentrius.sso.core.dto.ztat.ZtatRequestDTO;
import io.sentrius.sso.core.exceptions.ZtatException;
import io.sentrius.sso.core.model.security.Ztat;
import io.sentrius.sso.core.model.verbs.VerbResponse;
import io.sentrius.sso.core.services.agents.AgentClientService;
import io.sentrius.sso.core.services.agents.ZeroTrustClientService;
import io.sentrius.sso.core.dto.UserDTO;
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

    private volatile boolean running = true;
    private Thread workerThread;

    public ArrayNode promptAgent(UserDTO user) {
        ArrayNode response = null;
        while(null== response || response.isEmpty()){
            try {
                log.info("Prompting agent...");
                response = agentVerbs.promptAgent(null);
                return response;
            } catch (ZtatException e) {
                log.info("Mechanisms {}" , e.getMechanisms());
                var endpoint = zeroTrustClientService.createEndPoingRequest("prompt_agent", e.getEndpoint());
                ZtatRequestDTO ztatRequestDTO = ZtatRequestDTO.builder()
                    .user(user)
                    .command(endpoint.toString())
                    .justification("Registered Agent requires ability to prompt LLM endpoints to begin operations")
                    .summary("Registered Agent requires ability to prompt LLM endpoints to begin operations")
                    .build();
                var request = zeroTrustClientService.requestZtatToken(user,ztatRequestDTO);

                var token = zeroTrustClientService.awaitZtatToken(user, request, 60, TimeUnit.MINUTES);
                zeroTrustClientService.setZtat(token);
            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }
        return response;
    }

    @Override
    public void onApplicationEvent(final ApplicationReadyEvent event) {

        verbRegistry.scanClasspath();

        final UserDTO user = UserDTO.builder()
            .username(zeroTrustClientService.getUsername())
            .build();
        try {
            agentClientService.heartbeat(zeroTrustClientService.getUsername());
        } catch (ZtatException e) {
            throw new RuntimeException(e);
        }
        while(running) {
            try {
                var register = zeroTrustClientService.registerAgent(user);
                log.info("Registered agent response: {}", register);

                var ztat = JsonUtil.MAPPER.readValue(register, Ztat.class);
                zeroTrustClientService.setZtat(ztat.getZtatToken());
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

        workerThread = new Thread(() -> {
            try {

                log.info("Username: {}", user.getUsername());
                log.info("Registering v1.0.2 agent...");



                while (running) {
                    try {
                        var response = promptAgent(user);
                        log.info("Got response: {}", response);

                        VerbResponse priorResponse = null;
                        Map<String, Object> args = new HashMap<>();

                        for (var node : response) {
                            if (node.get("verb") != null) {
                                var verb = node.get("verb").asText();
                                log.info("Executing verb: {}", verb);
                                priorResponse = verbRegistry.execute(user, priorResponse, verb, args);
                            }
                            log.info("Node: {}", node);
                        }

                    } catch (Exception e) {
                        log.error("Exception in agent loop", e);
                    }

                    // Sleep between prompts
                    log.info("Sleeping for 60 seconds");
                    Thread.sleep(10_000);
                }

            } catch (Exception e) {
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
