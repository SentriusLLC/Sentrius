package io.sentrius.sso.core.trust;

import java.util.Set;
import com.fasterxml.jackson.annotation.JsonProperty;

class MatchCriteria {
    @JsonProperty("agent_tags")
    private Set<String> agentTags;

    public boolean matches(AgentContext ctx) {
        return ctx.getTags().stream().anyMatch(agentTags::contains);
    }
}