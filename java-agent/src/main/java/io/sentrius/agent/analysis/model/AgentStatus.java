package io.sentrius.agent.analysis.model;

import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.ToString;

@Getter
@Data
@ToString
@Builder
public class AgentStatus {

    private String status;
    private String version;
    private String health;
}
