package io.sentrius.sso.core.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class AgentHeartbeatDTO {
    private final String status;
    private final String agentId;
    private final String name;
    private final String agentUrl;

}
