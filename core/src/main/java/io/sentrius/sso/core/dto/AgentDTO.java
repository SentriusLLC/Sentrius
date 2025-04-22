package io.sentrius.sso.core.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class AgentDTO {
    private final String agentName;
    private final String agentId;
    private final String policyId;
    private final String lastHeartbeat;
    @Builder.Default
    private final boolean isRegistered = false;
}
