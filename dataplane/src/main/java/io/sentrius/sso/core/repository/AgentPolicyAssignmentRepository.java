package io.sentrius.sso.core.repository;

import java.util.List;
import io.sentrius.sso.core.model.AgentPolicyAssignment;
import io.sentrius.sso.core.model.AgentPolicyAssignmentId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentPolicyAssignmentRepository extends JpaRepository<AgentPolicyAssignment, AgentPolicyAssignmentId> {
    // Add custom queries here if needed

    List<AgentPolicyAssignment> findByUserUsername(String username);
}

