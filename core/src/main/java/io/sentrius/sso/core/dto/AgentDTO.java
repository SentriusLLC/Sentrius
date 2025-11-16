package io.sentrius.sso.core.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.SuperBuilder;
import java.util.UUID;

@Getter
@SuperBuilder(toBuilder = true)
@AllArgsConstructor
public class AgentDTO {
    private final String agentName;
    private final String agentId;
    private final String policyId;
    private final String lastHeartbeat;
    private final String agentCallback;
    @Builder.Default
    private final boolean isRegistered = false;
    
    private final Integer generation;
    private final UUID parentId;
    private final String memoryNamespace;
    private final Double trustScore;
    private final Long inheritedMemoryCount;
}
