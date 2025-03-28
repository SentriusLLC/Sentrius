package io.sentrius.sso.core.model;

import java.time.Instant;
import io.sentrius.sso.core.model.users.User;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "agent_policy_assignments")
public class AgentPolicyAssignment {


    @EmbeddedId
    private AgentPolicyAssignmentId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("userId")
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("policyId")
    @JoinColumn(name = "policy_id", nullable = false)
    private ATPLPolicyEntity policy;

    @Column(name = "assigned_at", nullable = false, columnDefinition = "timestamp default current_timestamp")
    private Instant assignedAt;
}
