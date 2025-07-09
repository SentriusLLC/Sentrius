package io.sentrius.sso.core.services.agents;

import java.util.UUID;
import io.sentrius.sso.core.model.agents.AgentContext;
import io.sentrius.sso.core.model.agents.AgentLaunch;
import io.sentrius.sso.core.repository.AgentLaunchRepository;
import org.springframework.stereotype.Service;

@Service
public class AgentLaunchService {

    private final AgentLaunchRepository launchRepo;
    private final AgentContextService contextService;

    public AgentLaunchService(AgentLaunchRepository launchRepo, AgentContextService contextService) {
        this.launchRepo = launchRepo;
        this.contextService = contextService;
    }

    public UUID recordLaunch(String agentId, UUID contextId, String launchedBy, String parameters) {
        AgentContext context = contextService.getContextOrThrow(contextId);

        AgentLaunch launch = new AgentLaunch();
        launch.setAgentId(agentId);
        launch.setContext(context);
        launch.setLaunchedBy(launchedBy);
        launch.setLaunchParameters(parameters);

        AgentLaunch saved = launchRepo.save(launch);
        return saved.getId();
    }
}