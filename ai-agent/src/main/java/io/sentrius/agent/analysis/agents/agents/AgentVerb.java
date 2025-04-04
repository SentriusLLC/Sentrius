package io.sentrius.agent.analysis.agents.agents;

import java.lang.reflect.Method;
import java.util.Map;
import lombok.Builder;
import lombok.Getter;

@Builder
@Getter

public class AgentVerb {
    private String name;
    private String description;
    private Method method;
}