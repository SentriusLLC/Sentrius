package io.sentrius.sso.core.model;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class AgentPolicyAssignmentId implements Serializable {
    private Long userId;
    private UUID policyId;

    public AgentPolicyAssignmentId() {}

    public AgentPolicyAssignmentId(Long agent, UUID policy) {
        this.userId = agent;
        this.policyId = policy;
    }

    // equals and hashCode
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AgentPolicyAssignmentId)) return false;
        AgentPolicyAssignmentId that = (AgentPolicyAssignmentId) o;
        return Objects.equals(userId, that.userId) && Objects.equals(policyId, that.policyId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, policyId);
    }
}
