package io.sentrius.sso.core.services.agents;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import io.sentrius.sso.core.dto.UserDTO;
import io.sentrius.sso.core.dto.ztat.AgentExecution;
import org.springframework.stereotype.Service;

@Service
public class AgentExecutionService {

    private final LoadingCache<UserDTO, AgentExecution> agentExecutionCache =
        Caffeine.newBuilder()
            .expireAfterWrite(1, TimeUnit.HOURS)
            .build(this::createExecution);

    protected AgentExecution createExecution(UserDTO user){
        return AgentExecution.builder().user(user).executionId(UUID.randomUUID().toString()).build();
    }

    public AgentExecution getAgentExecution(UserDTO user) {
        return agentExecutionCache.get(user);
    }
}
