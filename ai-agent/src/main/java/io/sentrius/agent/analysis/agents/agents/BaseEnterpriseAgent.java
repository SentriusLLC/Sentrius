package io.sentrius.agent.analysis.agents.agents;

import java.util.concurrent.TimeUnit;
import com.fasterxml.jackson.databind.node.ArrayNode;
import io.sentrius.agent.analysis.agents.verbs.AgentVerbs;
import io.sentrius.sso.core.dto.agents.AgentExecution;
import io.sentrius.sso.core.dto.agents.AgentExecutionContextDTO;
import io.sentrius.sso.core.dto.ztat.ZtatRequestDTO;
import io.sentrius.sso.core.exceptions.ZtatException;
import io.sentrius.sso.core.services.agents.AgentClientService;
import io.sentrius.sso.core.services.agents.ZeroTrustClientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;

@Slf4j
public abstract class BaseEnterpriseAgent implements ApplicationListener<ApplicationReadyEvent> {

    @Autowired
    protected final AgentVerbs agentVerbs;
    @Autowired
    protected final ZeroTrustClientService zeroTrustClientService;
    @Autowired
    protected final AgentClientService agentClientService;
    @Autowired
    protected final VerbRegistry verbRegistry;

    protected BaseEnterpriseAgent(
        AgentVerbs agentVerbs, ZeroTrustClientService zeroTrustClientService, AgentClientService agentClientService,
        VerbRegistry verbRegistry
    ) {
        this.agentVerbs = agentVerbs;
        this.zeroTrustClientService = zeroTrustClientService;
        this.agentClientService = agentClientService;
        this.verbRegistry = verbRegistry;
    }


    protected ArrayNode promptAgent(AgentExecution execution) throws ZtatException {
        return promptAgent(execution);
    }

    protected ArrayNode promptAgent(AgentExecution execution, AgentExecutionContextDTO contextDTO) throws ZtatException {
        while(true){
            try {
                log.info("Prompting agent...");
                return agentVerbs.promptAgent(execution,contextDTO);
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
}
