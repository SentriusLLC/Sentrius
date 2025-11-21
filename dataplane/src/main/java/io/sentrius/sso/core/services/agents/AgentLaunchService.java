package io.sentrius.sso.core.services.agents;

import java.util.Optional;
import java.util.UUID;
import io.sentrius.sso.core.model.agents.AgentContext;
import io.sentrius.sso.core.model.agents.AgentLaunch;
import io.sentrius.sso.core.repository.AgentLaunchRepository;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

@Slf4j
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
    
    /**
     * Update the agentId in the launch record to match the actual registered username.
     * This is needed because the launcher uses a short name, but Keycloak creates a longer username.
     * 
     * @param oldAgentId The short name used during launch (e.g., "my-agent")
     * @param newAgentId The actual Keycloak username (e.g., "service-account-my-agent-xyz123")
     * @return true if a launch record was found and updated, false otherwise
     */
    public boolean updateAgentIdForLaunch(String oldAgentId, String newAgentId) {
        Optional<AgentLaunch> launchOpt = launchRepo.findLatestByAgentId(oldAgentId);
        if (launchOpt.isPresent()) {
            AgentLaunch launch = launchOpt.get();
            log.info("Updating launch record agentId from '{}' to '{}'", oldAgentId, newAgentId);
            launch.setAgentId(newAgentId);
            launchRepo.save(launch);
            return true;
        }
        return false;
    }
}
