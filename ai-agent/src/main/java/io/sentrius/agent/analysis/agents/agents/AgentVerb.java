package io.sentrius.agent.analysis.agents.agents;

import java.util.Map;

public interface AgentVerb {
    String name();
    String description();
    Object execute(Map<String, Object> params) throws Exception;
}