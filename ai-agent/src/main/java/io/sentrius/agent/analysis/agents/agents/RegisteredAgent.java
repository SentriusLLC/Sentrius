package io.sentrius.agent.analysis.agents.agents;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ArrayNode;
import io.sentrius.agent.analysis.agents.verbs.AgentVerbs;
import io.sentrius.sso.core.dto.ztat.ZtatRequestDTO;
import io.sentrius.sso.core.exceptions.ZtatException;
import io.sentrius.sso.core.model.security.Ztat;
import io.sentrius.sso.core.services.agents.ZeroTrustClientService;
import io.sentrius.sso.core.dto.UserDTO;
import io.sentrius.sso.core.utils.JsonUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "agents.ai.registered.agent.enabled", havingValue = "true", matchIfMissing = false)
public class RegisteredAgent implements ApplicationListener<ApplicationReadyEvent> {


    final ZeroTrustClientService zeroTrustClientService;

    final VerbRegistry verbRegistry;
    final AgentVerbs agentVerbs;



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

        // Load agent config



        try {



            // get username from token
            UserDTO user = UserDTO.builder()
                .username(zeroTrustClientService.getUsername())
                .build();

            log.info(zeroTrustClientService.getUsername());

            log.info("Registering v1.0.2 agent...");

            // register

            var register = zeroTrustClientService.registerAgent(user);
            log.info("Registered agent is running {} ", register);

            var ztat = JsonUtil.MAPPER.readValue(register, Ztat.class);

            //while(true){
            // get ztat token
                try {



                    zeroTrustClientService.setZtat(ztat.getZtatToken());

                    // this phase is called "prompting"
                    var response = promptAgent(user);
                    for(var node : response){
                        if (node.get("verb") != null){
                            var verb =  node.get("verb").asText();
                            log.info("executing verb is {}", verb);
                            verbRegistry.execute(verb, null);
                        }
                        log.info("node {}", node);
                    }
                    log.info("got " + response);
                } catch (HttpClientErrorException e){
                    log.info("oh boy");
                }

            // execute command
//                var result = verbRegistry.execute(command, null);
                //log.info("Command executed: {}", result);



                // sleep for 5 seconds
                Thread.sleep(5000);
         //  }

        } catch (InterruptedException | JsonProcessingException e) {
            throw new RuntimeException(e);
        } catch (ZtatException e) {
            throw new RuntimeException(e);
        }
    }

}
