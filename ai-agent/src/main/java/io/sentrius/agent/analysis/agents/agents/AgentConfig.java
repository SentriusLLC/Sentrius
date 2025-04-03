package io.sentrius.agent.analysis.agents.agents;

import java.util.ArrayList;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Builder
@Getter
@Data
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class AgentConfig {
    @JsonProperty(required=false)
    @Builder.Default
    private List<String> roles = new ArrayList<>();
    @JsonProperty(required=false)
    private String description;
    @JsonProperty(required=true)
    private String context;

    // getters and setters
}
