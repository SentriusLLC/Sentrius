package io.sentrius.agent.analysis.agents.agents;

import java.io.IOException;
import java.util.List;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.sentrius.agent.analysis.agents.verbs.AgentVerbs;
import io.sentrius.sso.core.exceptions.ZtatException;
import io.sentrius.sso.core.services.agents.LLMService;
import io.sentrius.sso.core.services.agents.ZeroTrustClientService;
import io.sentrius.sso.core.dto.UserDTO;
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

    final VerbRegistry verbRegistry;
    final AgentVerbs agentVerbs;





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


            //while(true){

                try {
                    // this phase is called "prompting"
                    var response = agentVerbs.promptAgent(null);
                    log.info("got " + response);
                } catch (ZtatException e) {
                    e.printStackTrace();
                    //zeroTrustClientService.requestZtatToken()
                    // we have been requested to get a ztat
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            // execute command
//                var result = verbRegistry.execute(command, null);
                //log.info("Command executed: {}", result);



                // sleep for 5 seconds
                Thread.sleep(5000);
         //  }

        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

}
