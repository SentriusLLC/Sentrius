package io.sentrius.agent.analysis.agents.agents;

import io.sentrius.agent.services.ZeroTrustClientService;
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

    @Override
    public void onApplicationEvent(final ApplicationReadyEvent event) {

        // here your code ...

        UserDTO user = UserDTO.builder()
            .username("agent-1")
            .build();

        String command = "ssh connect host123";

        zeroTrustClientService.requestZtatToken(user, command);
        return;
    }

}
