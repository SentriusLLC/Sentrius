package io.sentrius.sso.core.model;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class AgentPolicyAssignmentId implements Serializable {
    private Long agent;
    private UUID policy;

    public AgentPolicyAssignmentId() {}

    public AgentPolicyAssignmentId(Long agent, UUID policy) {
        this.agent = agent;
        this.policy = policy;
    }

    // equals and hashCode
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AgentPolicyAssignmentId)) return false;
        AgentPolicyAssignmentId that = (AgentPolicyAssignmentId) o;
        return Objects.equals(agent, that.agent) && Objects.equals(policy, that.policy);
    }

    @Override
    public int hashCode() {
        return Objects.hash(agent, policy);
    }
}
