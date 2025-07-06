package io.sentrius.sso.core.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder(toBuilder = true)

public class AgentHistoryDTO extends AgentDTO{
    private String agentTypeName;
}
