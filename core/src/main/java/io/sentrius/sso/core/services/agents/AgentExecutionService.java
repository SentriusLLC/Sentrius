package io.sentrius.sso.core.services.agents;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import io.sentrius.sso.core.dto.UserDTO;
import io.sentrius.sso.core.dto.agents.AgentExecution;
import io.sentrius.sso.core.dto.agents.AgentExecutionContextDTO;
import org.springframework.stereotype.Service;

@Service
public class AgentExecutionService {

    private final LoadingCache<UserDTO, AgentExecution> agentExecutionCache =
        Caffeine.newBuilder()
            .expireAfterWrite(1, TimeUnit.HOURS)
            .build(this::createExecution);

    private final Cache<String, AgentExecutionContextDTO> agentExecutionContextCache =
        Caffeine.newBuilder().maximumSize(100).build();

    public void setExecutionContextDTO(AgentExecution execution, AgentExecutionContextDTO contextDTO) {
        agentExecutionContextCache.put(execution.getExecutionId(), contextDTO);
    }


    public AgentExecutionContextDTO getExecutionContextDTO(String executionId) {
        return agentExecutionContextCache.getIfPresent(executionId);
    }

    protected AgentExecution createExecution(UserDTO user){
        return AgentExecution.builder().user(user).executionId(UUID.randomUUID().toString()).build();
    }

    public AgentExecution getAgentExecution(UserDTO user) {
        return agentExecutionCache.get(user);
    }
}
